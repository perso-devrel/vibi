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
import androidx.compose.ui.unit.dp
import com.vibi.cmp.platform.RuntimeFlags
import com.vibi.cmp.platform.StemMixerSource
import com.vibi.cmp.platform.extractAudioPeaks
import com.vibi.cmp.ui.account.PAID_CREDITS_CTA_LABEL
import com.vibi.cmp.ui.account.PaidCreditsComingSoonNote
import com.vibi.cmp.platform.rememberAudioPreviewer
import com.vibi.cmp.platform.rememberStemMixer
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.ui.timeline.AudioSeparationStep
import com.vibi.shared.ui.timeline.AudioSeparationUiState
import com.vibi.shared.ui.timeline.CreditCostPreview
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
    /** 잔액 부족 시 "Buy credits" 버튼 → UserMenu/CreditPurchaseSheet 진입. null 이면 버튼 미표시
     *  (테스트 / dev). 운영 TimelineScreen 은 항상 주입. */
    onBuyCredits: (() -> Unit)? = null,
    /** IAP 미오픈([RuntimeFlags.iapEnabled]=false) 기간 — 잔액 부족 시 "Buy credits" 대신 노출되는
     *  "I want this" 수요 표현 탭. 컨페티 + BFF 적재를 호출자가 처리. */
    onWantPaidCredits: (() -> Unit)? = null,
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
                    "Separate audio",
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
                            contentDescription = if (isAllPlaying) "Pause selection" else "Play selection",
                            modifier = Modifier.size(VibiSpacing.md),
                        )
                        Spacer(Modifier.size(VibiSpacing.xxs))
                        Text("Play selection", style = typo.bodySm)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm)) {
                when (state.step) {
                    AudioSeparationStep.SETUP -> {
                        // 비용 미리보기 — costPreview null 이면 fetch 미완료, 도착 후 자동 갱신.
                        // 사용자가 Start 누르기 전에 차감액 + 잔액 + 부족 여부를 명시.
                        CostPreviewRow(state.costPreview)
                        // IAP 미오픈 + 잔액 부족 → "충전" 대신 "곧 열린다" 고지로 전환.
                        if (state.costPreview?.sufficient == false && !RuntimeFlags.iapEnabled) {
                            PaidCreditsComingSoonNote()
                        }
                    }

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
                                // 행 구성: 미리듣기 ▶ · 라벨 · 파형 · 볼륨 토글(아래 인라인 슬라이더 펼침) ·
                                // 선택 토글. 볼륨은 인라인 슬라이더(volumeExpanded)로 조절하므로 별도
                                // 가로 슬라이더는 두지 않는다.
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
                                    modifier = Modifier.size(VibiSpacing.touchMin),
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isThisPlaying) "Pause" else "Play",
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
                                    modifier = Modifier.size(VibiSpacing.touchMin),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Volume",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (volumeExpanded) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                // volume 토글(44dp) 과 CircleToggle(44dp) 의 hit zone 이 거의 닿아
                                // mis-tap 위험. 8dp 여유로 분리.
                                Spacer(Modifier.size(VibiSpacing.xs))
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
                                        colors = com.vibi.cmp.ui.timeline.sounddeck.mutedSliderColors(tokens.mutedText),
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
                        Text("Done. Selection saved.", style = typo.bodyMd)
                    }

                    AudioSeparationStep.FAILED -> {
                        if (state.insufficientCredits) {
                            // 잔액 부족 분기 — IAP 오픈 전이면 "충전" 대신 "곧 열린다" 고지로 전환.
                            if (!RuntimeFlags.iapEnabled) {
                                PaidCreditsComingSoonNote()
                            } else {
                                // costPreview 가 있으면 정확한 부족분 표시, 없으면 일반 안내.
                                Text(
                                    "Not enough credits to start",
                                    color = MaterialTheme.colorScheme.error,
                                    style = typo.bodyMd,
                                )
                                state.costPreview?.let { preview ->
                                    Text(
                                        "This separation needs ${preview.credits} credits, " +
                                            "but you have ${preview.balance}.",
                                        style = typo.bodySm,
                                    )
                                }
                            }
                        } else {
                            Text("Failed", color = MaterialTheme.colorScheme.error, style = typo.bodyMd)
                            state.errorMessage?.let { Text(it, style = typo.bodySm) }
                        }
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
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = dismissAndCleanup) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                when (state.step) {
                    AudioSeparationStep.SETUP -> {
                        val insufficient = state.costPreview?.sufficient == false
                        when {
                            // IAP 오픈 전: 부족 → "I want this" 수요 표현. 컨페티는 호출자가 띄움.
                            insufficient && !RuntimeFlags.iapEnabled && onWantPaidCredits != null ->
                                Button(onClick = onWantPaidCredits) { Text(PAID_CREDITS_CTA_LABEL) }
                            // 부족 → Start 대신 Buy credits 로 분기. onBuyCredits 미주입 시 fallback
                            // 으로 disabled Start 그대로 표시 (테스트 호환).
                            insufficient && onBuyCredits != null ->
                                Button(onClick = onBuyCredits) { Text("Buy credits") }
                            else ->
                                Button(
                                    enabled = state.canStart,
                                    onClick = onStart,
                                ) { Text("Start") }
                        }
                    }

                    AudioSeparationStep.PICK_STEMS -> Button(
                        enabled = state.canMix,
                        onClick = onConfirmMix,
                    ) { Text("Apply") }

                    AudioSeparationStep.DONE -> Button(onClick = dismissAndCleanup) { Text("Close") }

                    AudioSeparationStep.FAILED -> {
                        when {
                            state.insufficientCredits && !RuntimeFlags.iapEnabled && onWantPaidCredits != null ->
                                Button(onClick = onWantPaidCredits) { Text(PAID_CREDITS_CTA_LABEL) }
                            state.insufficientCredits && onBuyCredits != null ->
                                Button(onClick = onBuyCredits) { Text("Buy credits") }
                            else ->
                                Button(onClick = dismissAndCleanup) { Text("Close") }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}

/**
 * SETUP 단계 비용 미리보기 — "이 구간 분리에 X 크레딧 사용 (잔액 Y)" 한 줄. preview null (fetch
 * 미완료) 이면 placeholder 한 줄 — sheet 가 즉시 떠도 자리 잡힌 채로 정보가 채워지도록.
 */
@Composable
private fun CostPreviewRow(preview: CreditCostPreview?) {
    val typo = LocalVibiTypography.current
    val tokens = LocalVibiColors.current
    if (preview == null) {
        Text("Checking credit balance…", style = typo.bodySm, color = tokens.mutedText)
        return
    }
    val color = if (preview.sufficient) tokens.onBackgroundPrimary else MaterialTheme.colorScheme.error
    Text(
        text = "This separation will use ${preview.credits} credits (balance: ${preview.balance}).",
        style = typo.bodySm,
        color = color,
    )
}

@Composable
private fun CircleToggle(selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalVibiColors.current
    // 외곽: 투명 hit area 44dp (iOS HIG 44pt 기준). 내부: 시각 20dp 라디오 — 시각 톤은 그대로,
    // 손가락이 인접 row 의 화자 색 indicator/슬라이더에 닿지 않게 안전 마진 확보.
    Box(
        modifier = Modifier
            .size(VibiSpacing.touchMin)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(VibiSpacing.md)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = tokens.onBackgroundPrimary.copy(alpha = 0.6f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(VibiSpacing.sm)
                        .clip(CircleShape)
                        .background(tokens.onBackgroundPrimary)
                )
            }
        }
    }
}

