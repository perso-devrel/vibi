package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dubcast.cmp.platform.StemMixerSource
import com.dubcast.cmp.platform.rememberAudioPreviewer
import com.dubcast.cmp.platform.rememberStemMixer
import com.dubcast.shared.ui.timeline.AudioSeparationStep
import com.dubcast.shared.ui.timeline.AudioSeparationUiState
import com.dubcast.shared.ui.timeline.localizeProgressReason
import com.dubcast.shared.ui.timeline.stemDisplayLabel

/**
 * 음성분리 sheet — Setup → Processing → PickStems → Mixing → Done.
 *
 * legacy `AudioSeparationSheet` 의 등가. TimelineViewModel 의
 * `onUpdateSeparationSpeakers`, `onStartSeparation`, `onToggleStemSelection`,
 * `onUpdateStemVolume`, `onConfirmStemMix`, `onDismissAudioSeparationSheet` 호출.
 */
@Composable
fun AudioSeparationSheet(
    state: AudioSeparationUiState,
    onUpdateSpeakers: (Int) -> Unit,
    onStart: () -> Unit,
    onToggleStem: (String) -> Unit,
    onUpdateStemVolume: (String, Float) -> Unit,
    onToggleMuteOriginal: (Boolean) -> Unit,
    onConfirmMix: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val previewer = rememberAudioPreviewer()
    // 전체 재생 — 선택된 stem 다수를 동시에 재생하기 위한 mixer. 개별 ▶ 는 previewer 단일 player.
    val mixer = rememberStemMixer()
    // 현재 재생 중인 stemId. "all" = 전체 미리듣기 (mixer). null = 재생 안 됨. 그 외 = 단일 stem (previewer).
    var playingId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = {
            previewer.stop()
            mixer.pause()
            playingId = null
            onDismiss()
        },
        title = {
            // PICK_STEMS 단계엔 title 우측에 "▶ 전체" / "⏸ 전체" 토글.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("음원 분리", modifier = Modifier.weight(1f))
                if (state.step == AudioSeparationStep.PICK_STEMS) {
                    val isAllPlaying = playingId == "all"
                    TextButton(onClick = {
                        if (isAllPlaying) {
                            mixer.pause()
                            playingId = null
                        } else {
                            // 단일 stem 미리듣기와 충돌 방지 — 먼저 stop.
                            previewer.stop()
                            // 선택된 (selected = true) stem 만 동시 재생. url 빈 항목은 제외.
                            val sources = state.stems.mapNotNull { stem ->
                                val sel = state.selections[stem.stemId]
                                if (sel?.selected != true) return@mapNotNull null
                                if (stem.url.isBlank()) return@mapNotNull null
                                StemMixerSource(stemId = stem.stemId, audioUrl = stem.url)
                            }
                            if (sources.isNotEmpty()) {
                                mixer.load(sources)
                                state.stems.forEach { stem ->
                                    val sel = state.selections[stem.stemId]
                                    val v = if (sel?.selected == true) (sel.volume) else 0f
                                    mixer.setVolume(stem.stemId, v)
                                }
                                mixer.seekTo(0L)
                                mixer.play()
                                playingId = "all"
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isAllPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isAllPlaying) "전체 일시정지" else "전체 재생",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("전체")
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.step) {
                    AudioSeparationStep.SETUP -> {
                        OutlinedTextField(
                            value = state.numberOfSpeakers.toString(),
                            onValueChange = { onUpdateSpeakers(it.toIntOrNull() ?: 1) },
                            label = { Text("화자 수 (1~10)") }
                        )
                    }

                    AudioSeparationStep.PROCESSING -> {
                        Text(localizeProgressReason(state.progressReason))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.progress}%", style = MaterialTheme.typography.bodySmall)
                    }

                    AudioSeparationStep.PICK_STEMS -> {
                        // 각 stem — 원형 토글 + 라벨 + 볼륨 슬라이더 + 재생 버튼 한 줄.
                        state.stems.forEach { stem ->
                            val sel = state.selections[stem.stemId]
                            val selected = sel?.selected == true
                            val volume = sel?.volume ?: 1.0f
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                // 원형 토글 — 사각 Checkbox 대신.
                                CircleToggle(
                                    selected = selected,
                                    onClick = { onToggleStem(stem.stemId) },
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stemDisplayLabel(stem),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 80.dp)
                                        .clickable { onToggleStem(stem.stemId) }
                                )
                                Slider(
                                    value = volume,
                                    valueRange = 0f..2f,
                                    onValueChange = { onUpdateStemVolume(stem.stemId, it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .scale(scaleX = 1f, scaleY = 0.7f),
                                )
                                val isThisPlaying = playingId == stem.stemId
                                val canPreview = stem.url.isNotBlank()
                                IconButton(
                                    enabled = canPreview,
                                    onClick = {
                                        if (isThisPlaying) {
                                            previewer.stop()
                                            playingId = null
                                        } else {
                                            // 전체 재생 중이면 충돌 방지 위해 mixer 정지.
                                            mixer.pause()
                                            previewer.play(stem.url)
                                            playingId = stem.stemId
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isThisPlaying) "일시정지" else "재생",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    AudioSeparationStep.DONE -> {
                        Text("완료 — 선택한 stem 이 명세로 저장됨", style = MaterialTheme.typography.bodyMedium)
                    }

                    AudioSeparationStep.FAILED -> {
                        Text("실패", color = MaterialTheme.colorScheme.error)
                        state.errorMessage?.let { Text(it) }
                    }
                }
            }
        },
        confirmButton = {
            when (state.step) {
                AudioSeparationStep.SETUP -> Button(
                    enabled = state.canStart,
                    onClick = onStart
                ) { Text("분리 시작") }

                AudioSeparationStep.PICK_STEMS -> Button(
                    enabled = state.canMix,
                    onClick = onConfirmMix
                ) { Text("적용") }

                AudioSeparationStep.DONE,
                AudioSeparationStep.FAILED -> Button(onClick = onDismiss) { Text("닫기") }

                else -> Unit
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null && state.step == AudioSeparationStep.PICK_STEMS) {
                    TextButton(onClick = {
                        previewer.stop()
                        mixer.pause()
                        playingId = null
                        onDelete()
                    }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.size(4.dp))
                }
                TextButton(onClick = {
                    previewer.stop()
                    mixer.pause()
                    playingId = null
                    onDismiss()
                }) { Text("취소") }
            }
        }
    )
}

@Composable
private fun CircleToggle(selected: Boolean, onClick: () -> Unit) {
    val tokens = com.dubcast.cmp.theme.LocalDubCastColors.current
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = tokens.onBackgroundPrimary.copy(alpha = 0.6f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // 라디오 버튼 스타일: 외곽 + 안 채운 동그라미.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tokens.onBackgroundPrimary)
            )
        }
    }
}
