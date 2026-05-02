package com.dubcast.shared.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.SupportedLanguage
import com.dubcast.shared.domain.model.TargetLanguage
import com.dubcast.shared.domain.model.ValidationError
import com.dubcast.shared.domain.model.ValidationResult
import com.dubcast.shared.domain.model.VideoInfo
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.LanguageRepository
import com.dubcast.shared.domain.repository.SegmentRepository
import com.dubcast.shared.platform.VideoThumbnailExtractor
import com.dubcast.shared.domain.usecase.draft.ExpireOldDraftsUseCase
import com.dubcast.shared.domain.usecase.input.CreateProjectWithInitialVideoSegmentUseCase
import com.dubcast.shared.domain.usecase.input.ValidateVideoUseCase
import com.dubcast.shared.domain.usecase.input.VideoMetadataExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class InputUiState(
    val selectedVideo: VideoInfo? = null,
    val validationResult: ValidationResult? = null,
    val isExtracting: Boolean = false,
    /** Perso 가 지원하는 언어 동적 목록 (BFF `/api/v2/languages`). */
    val availableLanguages: List<SupportedLanguage> = emptyList(),
    val isLoadingLanguages: Boolean = false,
    val languagesError: String? = null,
    /** my_plan: 다중 언어 선택. 비어있으면 "원본 그대로" (translation OFF). */
    val selectedLanguageCodes: Set<String> = emptySet(),
    val enableAutoSubtitles: Boolean = false,
    val enableAutoDubbing: Boolean = false,
    val numberOfSpeakers: Int = 1,
    /** 메인 화면 "이어서 작업" 카드 데이터 — 자동 저장된 EditProject 들의 요약. */
    val drafts: List<DraftSummary> = emptyList(),
) {
    val isTranslationLanguage: Boolean
        get() = selectedLanguageCodes.isNotEmpty()
}

/**
 * "이어서 작업" 카드 한 장에 필요한 최소 정보. EditProject 전체를 UI 로 흘리지 않기 위함.
 *
 * @param jobsRunningSummary RUNNING 인 자동 잡(분리/자동자막/자동더빙) 수 한 줄. 0이면 null.
 */
data class DraftSummary(
    val projectId: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val jobsRunningSummary: String?,
    /**
     * 썸네일 JPEG 의 cache 경로 (이미 추출된 정적 이미지). Coil `AsyncImage` model 로 그대로 전달.
     * null = 추출 미완 또는 segment 없음. VideoPlayer 인스턴스 띄우지 않으려는 최적화.
     */
    val thumbnailPath: String? = null,
)

class InputViewModel constructor(
    private val extractor: VideoMetadataExtractor,
    private val validateVideo: ValidateVideoUseCase,
    private val createProjectWithInitialVideoSegment: CreateProjectWithInitialVideoSegmentUseCase,
    private val languageRepository: LanguageRepository,
    private val editProjectRepository: EditProjectRepository,
    private val segmentRepository: SegmentRepository,
    private val thumbnailExtractor: VideoThumbnailExtractor,
    private val expireOldDrafts: ExpireOldDraftsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InputUiState())
    val uiState: StateFlow<InputUiState> = _uiState.asStateFlow()

    private val _navigateToTimeline = MutableSharedFlow<String>()
    val navigateToTimeline: SharedFlow<String> = _navigateToTimeline.asSharedFlow()

    init {
        loadLanguages()
        // 7일 미접근 drafts cleanup. 실패해도 drafts observe 는 진행.
        viewModelScope.launch { runCatching { expireOldDrafts() } }
        // drafts 영속 상태 관찰 — 자동 저장이 EditProject 를 갱신할 때마다 카드도 갱신.
        //
        // 각 project 의 (firstSourceUri 조회 + JPEG 썸네일 추출) 를 모두 병렬 async/awaitAll 로 묶음.
        // 썸네일은 cache 히트 시 빠른 path 반환 (file existence check 만), 첫 추출만 ~50ms.
        // VideoPlayer(이전 구현) 의 ExoPlayer/AVPlayer 다중 인스턴스를 회피하려는 핫패스 최적화.
        editProjectRepository.observeAllProjects()
            .onEach { projects ->
                val summaries = coroutineScope {
                    projects.map { project ->
                        async {
                            val firstUri = runCatching {
                                segmentRepository.getFirstSourceUri(project.projectId)
                            }.getOrNull()
                            val thumbPath = firstUri
                                ?.let { runCatching { thumbnailExtractor.extractThumbnail(it) }.getOrNull() }
                            project.toDraftSummary(thumbPath)
                        }
                    }.awaitAll()
                }
                _uiState.value = _uiState.value.copy(drafts = summaries)
            }
            .launchIn(viewModelScope)
    }

    private fun loadLanguages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLanguages = true, languagesError = null)
            languageRepository.fetchLanguages().fold(
                onSuccess = { langs ->
                    _uiState.value = _uiState.value.copy(
                        availableLanguages = langs,
                        isLoadingLanguages = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingLanguages = false,
                        languagesError = e.message ?: "Failed to load languages"
                    )
                }
            )
        }
    }

    fun onRetryLoadLanguages() {
        loadLanguages()
    }

    fun onVideoPicked(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true)

            val videoInfo = extractor.extract(uri)
            if (videoInfo == null) {
                _uiState.value = _uiState.value.copy(
                    selectedVideo = null,
                    validationResult = ValidationResult.Invalid(ValidationError.METADATA_UNREADABLE),
                    isExtracting = false
                )
                return@launch
            }

            val result = validateVideo(videoInfo)
            _uiState.value = _uiState.value.copy(
                selectedVideo = videoInfo,
                validationResult = result,
                isExtracting = false
            )
            // 검증 통과 시 즉시 편집 화면으로 — 사용자가 별도 "다음" 누를 필요 없음.
            if (result is ValidationResult.Valid) {
                onContinue()
            }
        }
    }

    /** my_plan: 다중 언어 토글. 이미 선택된 코드면 해제, 아니면 추가. */
    fun onToggleLanguage(code: String) {
        val current = _uiState.value.selectedLanguageCodes
        val next = if (code in current) current - code else current + code
        // 모든 언어 해제 시 자동 자막/더빙도 OFF (번역 의미 없음)
        val translationActive = next.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedLanguageCodes = next,
            enableAutoSubtitles = if (translationActive) _uiState.value.enableAutoSubtitles else false,
            enableAutoDubbing = if (translationActive) _uiState.value.enableAutoDubbing else false
        )
    }

    fun onClearLanguageSelection() {
        _uiState.value = _uiState.value.copy(
            selectedLanguageCodes = emptySet(),
            enableAutoSubtitles = false,
            enableAutoDubbing = false
        )
    }

    /**
     * 비디오 선택 + 검증 결과 + 언어 선택 리셋. timeline 떠났다 InputScreen 재진입 시 호출되어
     * 사용자가 매번 처음 상태로 — 이전 선택이 남아있어 혼란 주는 것 방지.
     */
    fun onResetSelection() {
        _uiState.value = _uiState.value.copy(
            selectedVideo = null,
            validationResult = null,
            isExtracting = false,
            selectedLanguageCodes = emptySet(),
            enableAutoSubtitles = false,
            enableAutoDubbing = false,
            numberOfSpeakers = 1,
        )
    }

    fun onToggleAutoSubtitles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAutoSubtitles = enabled)
    }

    fun onToggleAutoDubbing(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAutoDubbing = enabled)
    }

    fun onToggleAutoLocalization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            enableAutoSubtitles = enabled,
            enableAutoDubbing = enabled
        )
    }

    fun onSetNumberOfSpeakers(count: Int) {
        _uiState.value = _uiState.value.copy(numberOfSpeakers = count.coerceIn(1, 10))
    }

    fun onContinue() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        viewModelScope.launch {
            // 단일 fallback (legacy 호환): 첫 번째 선택된 언어 또는 ORIGINAL.
            val primaryCode = state.selectedLanguageCodes.firstOrNull() ?: TargetLanguage.CODE_ORIGINAL
            val projectId = createProjectWithInitialVideoSegment(
                videoInfo = video,
                targetLanguageCode = primaryCode,
                targetLanguageCodes = state.selectedLanguageCodes.toList(),
                enableAutoDubbing = state.enableAutoDubbing,
                enableAutoSubtitles = state.enableAutoSubtitles,
                numberOfSpeakers = state.numberOfSpeakers
            )
            _navigateToTimeline.emit(projectId)
        }
    }

    /** "이어서 작업" 카드 클릭 — 해당 projectId 의 timeline 으로 진입. */
    fun onContinueDraft(projectId: String) {
        viewModelScope.launch {
            _navigateToTimeline.emit(projectId)
        }
    }

    /** 카드 X 버튼 / long-press 삭제. 자식 row 들은 deleteProject 가 cascade. */
    fun onDeleteDraft(projectId: String) {
        viewModelScope.launch {
            runCatching { editProjectRepository.deleteProject(projectId) }
        }
    }

    private fun EditProject.toDraftSummary(thumbnailPath: String?): DraftSummary {
        var running = 0
        if (separationStatus == AutoJobStatus.RUNNING) running++
        if (autoSubtitleStatus == AutoJobStatus.RUNNING) running++
        if (autoDubStatus == AutoJobStatus.RUNNING) running++
        // language-별 자동 더빙 잡도 카운트 (autoDubStatus 가 IDLE 라도 일부 언어가 RUNNING 일 수 있음)
        running += autoDubStatusByLang.values.count { it == AutoJobStatus.RUNNING }
        return DraftSummary(
            projectId = projectId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            jobsRunningSummary = if (running > 0) "${running}개 작업 진행 중" else null,
            thumbnailPath = thumbnailPath,
        )
    }
}
