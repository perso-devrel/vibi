package com.vibi.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.vibi.cmp.platform.StemMixerSource
import com.vibi.cmp.platform.extractAudioPeaks
import com.vibi.cmp.platform.rememberAudioPreviewer
import com.vibi.cmp.platform.rememberStemMixer
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.ui.timeline.AudioSeparationStep
import com.vibi.shared.ui.timeline.AudioSeparationUiState
import com.vibi.shared.ui.timeline.localizeProgressReason
import com.vibi.shared.ui.timeline.stemDisplayLabel

/**
 * stem 별 파형 peaks 모듈 레벨 캐시 — sheet 닫고 다시 열 때 즉시 표시. composable scope (`remember`) 만
 * 쓰면 reopen 마다 빈 list 로 리셋되어 추출 재실행 (실패 시 영영 안 보임). URL 키, 프로세스 lifetime 영속.
 */
private val stemPeaksCache = mutableMapOf<String, List<Float>>()

/**
 * 음성분리 sheet — Setup → Processing → PickStems → Mixing → Done.
 *
 * legacy `AudioSeparationSheet` 의 등가. TimelineViewModel 의
 * `onUpdateSeparationSpeakers`, `onStartSeparation`, `onToggleStemSelection`,
 * `onUpdateStemVolume`, `onConfirmStemMix`, `onDismissAudioSeparationSheet` 호출.
 *
 * BgmActionSheet 와 동일 ModalBottomSheet 포맷 — title row + content + footer (confirm/dismiss).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AudioSeparationSheet(
    state: AudioSeparationUiState,
    onStart: () -> Unit,
    onToggleStem: (String) -> Unit,
    onUpdateStemVolume: (String, Float) -> Unit,
    onToggleMuteOriginal: (Boolean) -> Unit,
    onConfirmMix: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previewer = rememberAudioPreviewer()
    // 전체 재생 — 선택된 stem 다수를 동시에 재생하기 위한 mixer. 개별 ▶ 는 previewer 단일 player.
    val mixer = rememberStemMixer()
    // 현재 재생 중인 stemId. "all" = 전체 미리듣기 (mixer). null = 재생 안 됨. 그 외 = 단일 stem (previewer).
    var playingId by remember { mutableStateOf<String?>(null) }
    // stem 별 파형 peaks 캐시 — 모듈 레벨 stemPeaksCache 에서 즉시 시드. composable scope 만 쓰면 sheet
    // 닫고 다시 열 때마다 빈 list 로 리셋되어 같은 stem 의 파형이 안 보임.
    val peaksByUrl = remember {
        mutableStateMapOf<String, List<Float>>().apply { putAll(stemPeaksCache) }
    }
    // 볼륨 인라인 슬라이더 expand 대상 stemId. null = 닫힘.
    var volumeExpandStemId by remember { mutableStateOf<String?>(null) }
    val dismissAndCleanup: () -> Unit = {
        previewer.stop()
        mixer.pause()
        playingId = null
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = dismissAndCleanup,
        sheetState = sheetState,
        containerColor = tokens.panelBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VibiSpacing.md, vertical = VibiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
        ) {
            // PICK_STEMS 단계엔 title 우측에 "▶ 전체" / "⏸ 전체" 토글.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "음원 분리",
                    style = typo.displaySm,
                    color = tokens.onBackgroundPrimary,
                    modifier = Modifier.weight(1f),
                )
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
                            contentDescription = if (isAllPlaying) "선택 재생 일시정지" else "선택 재생",
                            modifier = Modifier.size(VibiSpacing.md),
                        )
                        Spacer(Modifier.size(VibiSpacing.xxs))
                        Text("선택 재생", style = typo.bodySm)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm)) {
                when (state.step) {
                    AudioSeparationStep.SETUP -> Unit  // Perso 자동 감지 — 사용자 입력 불필요

                    AudioSeparationStep.PROCESSING -> {
                        Text(localizeProgressReason(state.progressReason), style = typo.bodyMd)
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.progress}%", style = typo.caption)
                    }

                    AudioSeparationStep.PICK_STEMS -> {
                        // "모든 화자" (voice_all) 은 화자별 + 배경음 으로 충분히 표현되므로 제외.
                        val visibleStems = state.stems.filter { it.stemId != Stem.STEM_ID_VOICE_ALL }
                        visibleStems.forEach { stem ->
                            val sel = state.selections[stem.stemId]
                            val selected = sel?.selected == true
                            val volume = sel?.volume ?: 1.0f
                            val isThisPlaying = playingId == stem.stemId
                            val canPreview = stem.url.isNotBlank()
                            // stem 별 파형 — 모듈 레벨 stemPeaksCache 에 1회 추출 후 영속.
                            // 같은 URL 의 sheet 재진입 시 즉시 표시 (이전엔 composable scope remember 라
                            // 매 reopen 마다 리셋 → 추출 재실행, 실패 시 영영 빈 상태).
                            LaunchedEffect(stem.url) {
                                if (stem.url.isNotBlank() && peaksByUrl[stem.url].isNullOrEmpty()) {
                                    val extracted = extractAudioPeaks(stem.url, samples = 80)
                                    if (extracted.isNotEmpty()) {
                                        stemPeaksCache[stem.url] = extracted
                                        peaksByUrl[stem.url] = extracted
                                    }
                                }
                            }
                            val volumeExpanded = volumeExpandStemId == stem.stemId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = VibiSpacing.xxs)
                            ) {
                                // 원형 토글 — 사각 Checkbox 대신.
                                CircleToggle(
                                    selected = selected,
                                    onClick = { onToggleStem(stem.stemId) },
                                )
                                Spacer(Modifier.size(VibiSpacing.xs))
                                Text(
                                    text = stemDisplayLabel(stem),
                                    style = typo.bodySm,
                                    color = tokens.onBackgroundPrimary,
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
                                        .padding(horizontal = VibiSpacing.xxs)
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
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isThisPlaying) "일시정지" else "재생",
                                        modifier = Modifier.size(VibiSpacing.md),
                                    )
                                }
                                Text(
                                    text = stemDisplayLabel(stem),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 80.dp)
                                        .clickable { onToggleStem(stem.stemId) }
                                )
                                Spacer(Modifier.size(6.dp))
                                WaveformPlayBar(
                                    peaks = peaksByUrl[stem.url] ?: emptyList(),
                                    progressMs = if (isThisPlaying) previewer.progressMs.value else 0L,
                                    durationMs = if (isThisPlaying) previewer.durationMs.value else 0L,
                                    isPlaying = isThisPlaying,
                                    modifier = Modifier.weight(1f),
                                    compact = true,
                                )
                                Spacer(Modifier.size(6.dp))
                                // 볼륨 — 회전된 세로 슬라이더 popup 대신 인라인 가로 슬라이더 (아래 row 펼침).
                                IconButton(
                                    onClick = {
                                        volumeExpandStemId = if (volumeExpanded) null else stem.stemId
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "볼륨",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (volumeExpanded) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.size(2.dp))
                                CircleToggle(
                                    selected = selected,
                                    onClick = { onToggleStem(stem.stemId) },
                                )
                            }
                            if (volumeExpanded) {
                                // 인라인 가로 볼륨 슬라이더 — 부모 stem row 의 가시 너비와 맞추기 위해
                                // leading padding 제거. "볼륨" 라벨도 생략 — 위 토글 버튼이 맥락 제공,
                                // 슬라이더 트랙이 row 의 끝까지 확장돼 thumb 의 움직임 범위가 배경과 일치.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Slider(
                                        value = volume,
                                        valueRange = 0f..2f,
                                        onValueChange = { onUpdateStemVolume(stem.stemId, it) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${(volume * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }

                    AudioSeparationStep.DONE -> {
                        Text("완료 — 선택한 stem 이 명세로 저장됨", style = typo.bodyMd)
                    }

                    AudioSeparationStep.FAILED -> {
                        Text("실패", color = MaterialTheme.colorScheme.error, style = typo.bodyMd)
                        state.errorMessage?.let { Text(it, style = typo.bodySm) }
                    }
                }
            }
            // Footer — confirm + dismiss/delete 한 row 에. AlertDialog 의 confirmButton/
            // dismissButton 슬롯 등가물이지만 ModalBottomSheet 는 직접 배치.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
            ) {
                if (onDelete != null && state.step == AudioSeparationStep.PICK_STEMS) {
                    TextButton(onClick = {
                        previewer.stop()
                        mixer.pause()
                        playingId = null
                        onDelete()
                    }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = dismissAndCleanup) { Text("취소") }
                Spacer(Modifier.weight(1f))
                when (state.step) {
                    AudioSeparationStep.SETUP -> Button(
                        enabled = state.canStart,
                        onClick = onStart,
                    ) { Text("분리 시작") }

                    AudioSeparationStep.PICK_STEMS -> Button(
                        enabled = state.canMix,
                        onClick = onConfirmMix,
                    ) { Text("적용") }

                    AudioSeparationStep.DONE,
                    AudioSeparationStep.FAILED -> Button(onClick = dismissAndCleanup) { Text("닫기") }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun CircleToggle(selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalVibiColors.current
    Box(
        modifier = Modifier
            .size(VibiSpacing.md)
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
                    .size(VibiSpacing.sm)
                    .clip(CircleShape)
                    .background(tokens.onBackgroundPrimary)
            )
        }
    }
}

