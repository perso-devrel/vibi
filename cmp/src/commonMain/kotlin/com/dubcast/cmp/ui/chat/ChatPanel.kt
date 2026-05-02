package com.dubcast.cmp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dubcast.cmp.theme.LocalDubCastColors
import com.dubcast.shared.data.remote.dto.ChatMessageDto
import com.dubcast.shared.data.remote.dto.ProposalDto
import com.dubcast.shared.data.remote.dto.ToolCallDto
import com.dubcast.shared.domain.chat.ChatToolDispatcher
import com.dubcast.shared.domain.chat.DispatchResult
import com.dubcast.shared.domain.chat.ProjectContextBuilder
import com.dubcast.shared.ui.chat.ChatViewModel
import com.dubcast.shared.ui.timeline.TimelineUiState
import com.dubcast.shared.ui.timeline.TimelineViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * 채팅 패널 — 사용자가 자연어로 편집 의도를 말하면 BFF/Gemini 가 proposal 로 응답, 사용자 [적용]
 * 시 [ChatToolDispatcher] 가 timelineVm 의 onXxx 들을 순차 호출.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPanel(
    timelineVm: TimelineViewModel,
    timelineState: TimelineUiState,
    onDismiss: () -> Unit,
    chatVm: ChatViewModel = koinViewModel(),
    dispatcher: ChatToolDispatcher = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by chatVm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val tokens = LocalDubCastColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "편집 어시스턴트",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.onBackgroundPrimary,
            )

            // 메시지 영역 — 비어있을 때 placeholder 칩 3종.
            if (state.messages.isEmpty()) {
                ExamplePromptChips(onPick = { input = it })
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(state.messages) { _, msg -> MessageBubble(msg) }
                }
            }

            // ProposalCard
            state.pending?.let { proposal ->
                ProposalCard(
                    proposal = proposal,
                    dispatcher = dispatcher,
                    labelFor = { dispatcher.labelFor(it) },
                    onApply = { steps ->
                        scope.launch {
                            val result = dispatcher.dispatch(steps, timelineVm)
                            val summary = formatDispatchSummary(result)
                            chatVm.onApplied(steps, summary)
                        }
                    },
                    onRevise = {
                        // [수정 요청] — 입력창에 prefill.
                        input = "방금 제안에서 다음 부분만 다르게: "
                    },
                    onCancel = { chatVm.cancelProposal() },
                )
            }

            state.error?.let { err ->
                Text("⚠ $err", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            // 입력창 + 전송.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("자연어로 편집 의도를 말해보세요") },
                    enabled = !state.isSending,
                )
                Button(
                    enabled = !state.isSending && input.isNotBlank(),
                    onClick = {
                        val ctx = ProjectContextBuilder.build(timelineState)
                        chatVm.send(input.trim(), ctx)
                        input = ""
                    },
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).clip(RoundedCornerShape(8.dp)),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("전송")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExamplePromptChips(onPick: (String) -> Unit) {
    val examples = listOf(
        "외국어로 '안녕'이라고 말한 자막 찾아줘",
        "3번 자막 텍스트를 '안녕하세요' 로 바꿔줘",
        "이 영상 좀 활기차게 만들어줘",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        examples.forEach { ex ->
            AssistChip(
                onClick = { onPick(ex) },
                label = { Text(ex, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessageDto) {
    val tokens = LocalDubCastColors.current
    val (bg, color, align) = when (msg.role) {
        "user" -> Triple(tokens.accent, tokens.onBackgroundPrimary, Alignment.End)
        "system" -> Triple(tokens.chipBg, tokens.mutedText, Alignment.CenterHorizontally)
        else -> Triple(tokens.panelBg, tokens.onBackgroundPrimary, Alignment.Start)
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when (align) {
            Alignment.End -> Alignment.CenterEnd
            Alignment.CenterHorizontally -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(msg.content, color = color, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: ProposalDto,
    dispatcher: ChatToolDispatcher,
    labelFor: (ToolCallDto) -> String,
    onApply: (List<ToolCallDto>) -> Unit,
    onRevise: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalDubCastColors.current
    val hasCostHint = proposal.steps.any {
        it.name in COST_INTENSIVE
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.panelBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "제안",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.mutedText,
        )
        Text(proposal.rationale, color = tokens.onBackgroundPrimary, fontSize = 13.sp)
        Text(
            proposal.steps.mapIndexed { i, s -> "${i + 1}. ${labelFor(s)}" }.joinToString("\n"),
            color = tokens.onBackgroundPrimary,
            fontSize = 12.sp,
        )
        if (hasCostHint) {
            Text(
                "⏱ 자동 자막/더빙·음원 분리는 수 분 소요될 수 있습니다.",
                color = tokens.mutedText,
                fontSize = 11.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onApply(proposal.steps) }) { Text("적용", fontSize = 13.sp) }
            OutlinedButton(onClick = onRevise) { Text("수정 요청", fontSize = 13.sp) }
            TextButton(onClick = onCancel) { Text("취소", fontSize = 13.sp) }
        }
    }
}

private val COST_INTENSIVE = setOf(
    "generate_subtitles", "generate_dub", "separate_audio_range",
    "generate_subtitles_for_bgm", "generate_dub_for_bgm",
)

private fun formatDispatchSummary(result: DispatchResult): String = when (result) {
    is DispatchResult.Success -> "✓ 적용 완료: ${result.appliedLabels.joinToString(", ")}"
    is DispatchResult.Failure ->
        "⚠ ${result.failedAtIndex + 1}번째 단계(${result.failedLabel}) 실패: ${result.message}\n" +
            "이미 적용: ${result.appliedLabels.joinToString(", ").ifBlank { "없음" }}"
}
