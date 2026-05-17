package com.vibi.shared.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.data.remote.dto.ChatMessageDto
import com.vibi.shared.data.remote.dto.ChatRequestDto
import com.vibi.shared.data.remote.dto.ChatResponseDto
import com.vibi.shared.data.remote.dto.ProjectContextDto
import com.vibi.shared.data.remote.dto.ProposalDto
import com.vibi.shared.data.remote.dto.ToolCallDto
import com.vibi.shared.data.repository.ChatRepository
import com.vibi.shared.domain.chat.ChatToolDispatcher
import com.vibi.shared.domain.chat.DispatchResult
import com.vibi.shared.ui.timeline.TimelineViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 채팅 패널 in-memory 세션. v1 은 Room 영구 보관 X — 앱 프로세스 종료 시 휘발.
 *
 * **projectId 별 격리**: ViewModelStoreOwner 가 영상 간 공유돼도 messages 가 섞이지 않도록
 * [bindProject] 가 active session 을 swap. 영상 A→B→A 로 돌아오면 A 의 기록 보존.
 *
 * pending proposal 이 있을 때만 [ChatPanel] 이 ProposalCard 를 렌더. 사용자 [적용] 시
 * [applyProposal] 이 본 VM scope 에서 dispatcher 를 직접 실행 — 패널 닫혀도 진행 유지.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var boundProjectId: String? = null
    private val sessionsByProject = mutableMapOf<String, ChatUiState>()

    // TimelineViewModel.chatAssistantEvents 를 ChatPanel(ModalBottomSheet) 가 아니라 본 VM 의
    // viewModelScope 에서 collect — 패널이 dismiss 되어도 STT/자막 완료 메시지를 messages 에 누적.
    // SharedFlow 의 replay=0 이라 collect 끊기면 그 사이 emit 은 유실되는 문제 회피.
    private var assistantEventsJob: Job? = null

    // 패널 가시성 — true 일 때 도착한 model/system 메시지는 unread 로 간주 안 함.
    private var panelOpen: Boolean = false

    // 메시지 id 카운터 — "처리 중" 메시지 → 결과 메시지 in-place 갱신용. v1 은 in-memory 라
    // process 재시작 시 0 부터 다시 시작해도 무방.
    private var nextLocalId: Long = 0L
    private fun generateLocalId(): String = "local-${++nextLocalId}"

    /**
     * ChatPanel 이 LaunchedEffect(projectId) 로 호출. 다른 projectId 로 전환되면 현재 _state 를
     * 이전 슬롯에 보관 후 새 projectId 의 세션을 복원 (없으면 빈 ChatUiState).
     */
    fun bindProject(projectId: String) {
        if (projectId.isBlank()) return
        if (boundProjectId == projectId) return
        boundProjectId?.let { prev -> sessionsByProject[prev] = _state.value }
        boundProjectId = projectId
        _state.value = sessionsByProject[projectId] ?: ChatUiState()
    }

    /**
     * TimelineViewModel 의 chatAssistantEvents 를 본 VM scope 에서 collect 시작.
     * ChatPanel(ModalBottomSheet) 의 LaunchedEffect 에서 collect 하면 패널 dismiss 시 collect
     * 가 cancel 되어 STT 완료/자막 완료 emit 이 유실됨 — 본 VM 으로 옮겨 panel 가시성과 무관하게
     * 누적되도록 한다.
     */
    fun bindTimelineEvents(events: SharedFlow<String>) {
        assistantEventsJob?.cancel()
        assistantEventsJob = viewModelScope.launch {
            events.collect { msg -> pushAssistantMessage(msg) }
        }
    }

    fun onPanelOpened() {
        panelOpen = true
        if (_state.value.hasUnreadMessages) {
            _state.value = _state.value.copy(hasUnreadMessages = false)
        }
    }

    fun onPanelClosed() {
        panelOpen = false
    }

    fun send(text: String, projectContext: ProjectContextDto, locale: String = "ko") {
        if (text.isBlank()) return
        appendMessage("user", text) { copy(isSending = true, error = null) }
        viewModelScope.launch {
            // BFF/Gemini turn 은 user/model 만 의미. UI 전용 "system" (dispatcher 결과 라벨) 은
            // 컨텍스트 노이즈라 BFF 전송 리스트에서 제외. role 정합성은 appendLocalGuide 에서 "model"
            // 로 push 해 user/model alternation 을 깨지 않게 유지.
            val turnsForBff = _state.value.messages.filter { it.role == "user" || it.role == "model" }
            val req = ChatRequestDto(
                messages = turnsForBff,
                projectContext = projectContext,
                locale = locale,
            )
            chatRepository.chat(req).fold(
                onSuccess = { resp -> applyResponse(resp) },
                onFailure = { _ ->
                    _state.value = _state.value.copy(
                        isSending = false,
                        error = "채팅 호출 실패",
                    )
                },
            )
        }
    }

    private fun applyResponse(resp: ChatResponseDto) {
        when (resp.kind) {
            "text" -> {
                appendMessage("model", resp.text.orEmpty()) {
                    copy(isSending = false, pending = null)
                }
            }
            "proposal" -> {
                val proposal = resp.proposal ?: return run {
                    _state.value = _state.value.copy(
                        isSending = false,
                        error = "proposal 비어있음",
                    )
                }
                // proposal rationale 도 messages 에 model turn 으로 보존 → 다음 send 시 컨텍스트.
                appendMessage("model", proposal.rationale) {
                    copy(isSending = false, pending = proposal)
                }
            }
            else -> {
                _state.value = _state.value.copy(
                    isSending = false,
                    error = "알 수 없는 응답 kind: ${resp.kind}",
                )
            }
        }
    }

    /**
     * Gemini 가 emit 한 proposal 자동 적용 — TimelineScreen 의 LaunchedEffect 가 pending 도착 시
     * 즉시 호출. 자연어 confirm 흐름이라 별도 [적용] 버튼 없음. 동의는 채팅으로 이미 받았다.
     *
     * 1) "⏳ 처리 중: 단계1, 단계2" system 메시지 push + pending 제거 + isApplying=true
     * 2) dispatcher.dispatch (각 step suspend 종료까지 await)
     * 3) 같은 id 메시지를 "✓ 적용 완료" / "⚠ 실패" 로 replace + isApplying=false
     */
    fun applyProposal(
        steps: List<ToolCallDto>,
        dispatcher: ChatToolDispatcher,
        timelineVm: TimelineViewModel,
    ) {
        val labels = steps.map { dispatcher.labelFor(it) }
        val pendingMsg = appendMessage(
            role = "system",
            content = "⏳ 처리 중: ${labels.joinToString(", ")}",
        ) { copy(pending = null, isApplying = true) }
        viewModelScope.launch {
            val result = runCatching { dispatcher.dispatch(steps, timelineVm) }
                .getOrElse { DispatchResult.Failure(
                    appliedLabels = emptyList(),
                    failedAtIndex = 0,
                    failedLabel = labels.firstOrNull() ?: "?",
                    message = "알 수 없는 오류",
                ) }
            val finalContent = formatDispatchResult(result)
            val replaced = _state.value.messages.map { msg ->
                if (msg.id == pendingMsg.id) msg.copy(content = finalContent) else msg
            }
            _state.value = _state.value.copy(
                messages = replaced,
                isApplying = false,
                // 결과 메시지 도착 시 panel 닫혀있으면 unread 표시.
                hasUnreadMessages = _state.value.hasUnreadMessages || !panelOpen,
            )
        }
    }

    /** 이전 버전 호환 — 더 이상 UI 에서 호출하지 않지만 외부 호출자가 있을 수 있어 남김. */
    fun cancelProposal() {
        appendMessage("system", "취소되었습니다.") { copy(pending = null) }
    }

    /**
     * TimelineViewModel 의 비동기 작업 (예: STT 완료 후 스크립트 표시) 결과를 채팅 thread 에
     * model 메시지로 push. Gemini 호출 없이 로컬 추가만. [bindTimelineEvents] 가 본 VM scope 에서
     * collect 후 본 메서드 호출.
     *
     * 다음 user turn 에서 BFF 로 송신될 때 [send] 가 messages 를 그대로 컨텍스트로 보내므로,
     * Gemini 가 직전 push 된 스크립트 본문을 보고 "3번째 줄을 X로" 같은 후속 발화를 정확히 해석.
     */
    fun pushAssistantMessage(content: String) {
        if (content.isBlank()) return
        appendMessage("model", content)
    }

    /**
     * 정해진 답이 있는 가이드성 질문 — Gemini 호출 없이 user/assistant 메시지를 로컬로 append.
     * "어떤 편집을 할 수 있는지" 같은 capability 안내가 대표 케이스.
     */
    fun appendLocalGuide(userPrompt: String, assistantReply: String) {
        // role 은 Gemini 규약 (user/model) 으로 통일 — "assistant" 는 OpenAI 스타일이라 BFF coerce 시
        // user 로 깎여 turn alternation 이 깨졌었다.
        appendMessage("user", userPrompt)
        appendMessage("model", assistantReply)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * 메시지 1개 append + unread 토글 + 호출자 측 추가 state 변경을 한 번의 _state 갱신으로 묶음.
     * user role 은 본인 행위라 unread 로 카운트 안 함. transform 은 messages/hasUnreadMessages 외
     * 필드 (isSending/pending/error) 갱신용.
     */
    private fun appendMessage(
        role: String,
        content: String,
        transform: ChatUiState.() -> ChatUiState = { this },
    ): ChatMessageDto {
        val msg = ChatMessageDto(role = role, content = content, id = generateLocalId())
        val countsAsUnread = role != "user" && !panelOpen
        _state.value = _state.value.copy(
            messages = _state.value.messages + msg,
            hasUnreadMessages = _state.value.hasUnreadMessages || countsAsUnread,
        ).transform()
        return msg
    }
}

private fun formatDispatchResult(result: DispatchResult): String = when (result) {
    is DispatchResult.Success -> "✓ 적용 완료: ${result.appliedLabels.joinToString(", ")}"
    is DispatchResult.Failure ->
        "⚠ ${result.failedAtIndex + 1}번째 단계(${result.failedLabel}) 실패: ${result.message}\n" +
            "이미 적용: ${result.appliedLabels.joinToString(", ").ifBlank { "없음" }}"
}

data class ChatUiState(
    val messages: List<ChatMessageDto> = emptyList(),
    /**
     * Gemini 가 emit 한 proposal 중 아직 자동 dispatch 가 시작되지 않은 것. TimelineScreen 의
     * LaunchedEffect 가 이 값을 watch 해 즉시 [ChatViewModel.applyProposal] 을 트리거.
     * 이전 버전과 달리 UI 에는 노출 안 함 — 자연어 confirm 흐름으로 전환.
     */
    val pending: ProposalDto? = null,
    val isSending: Boolean = false,
    /** dispatcher 가 실행 중 — stepper 이동 가드용. */
    val isApplying: Boolean = false,
    val error: String? = null,
    // 패널이 닫혀있는 동안 AI(model) 또는 시스템 메시지가 새로 도착하면 true. 패널 열림 시 reset.
    val hasUnreadMessages: Boolean = false,
)
