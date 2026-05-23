package com.vibi.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibi.cmp.platform.extractAudioPeaks
import com.vibi.cmp.platform.rememberAudioPreviewer
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.shared.ui.timeline.BgmTrimRequest

/**
 * 영상보다 긴 BGM 음원 선택 시 노출되는 시트. 사용자가 파형 + 두 핸들로 영상 길이 이내 구간을
 * 골라 "삽입" 누르면 그 sub-range 만 BgmClip 의 trim 으로 보존된 채 timeline 에 추가된다.
 * 취소 시 미삽입.
 *
 * - WaveformPlayBar + rememberAudioPreviewer 로 미리듣기 + seek (BgmActionSheet 동일 패턴).
 * - peaks 추출은 [extractAudioPeaks] (iOS 만 동작, Android stub) — 비어 있으면 progress 막대 fallback.
 * - 핸들 사이 구간이 영상 길이 이내 + [MIN_TRIM_SPAN_MS] 이상일 때만 "삽입" 활성.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgmTrimSheet(
    request: BgmTrimRequest,
    videoDurationMs: Long,
    onUpdateRange: (rangeStartMs: Long, rangeEndMs: Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previewer = rememberAudioPreviewer()
    var isPreviewing by remember(request.sourceUri) { mutableStateOf(false) }

    var peaks by remember(request.sourceUri) {
        mutableStateOf(bgmTrimPeaksCache[request.sourceUri].orEmpty())
    }
    LaunchedEffect(request.sourceUri) {
        if (peaks.isEmpty()) {
            val extracted = extractAudioPeaks(request.sourceUri, samples = 480)
            if (extracted.isNotEmpty()) {
                bgmTrimPeaksCache[request.sourceUri] = extracted
                peaks = extracted
            }
        }
    }

    val dismissAndStop: () -> Unit = {
        if (isPreviewing) {
            previewer.stop()
            isPreviewing = false
        }
        onCancel()
    }

    val span = (request.rangeEndMs - request.rangeStartMs).coerceAtLeast(0L)
    val withinLimit = videoDurationMs <= 0L || span <= videoDurationMs
    val canInsert = span >= MIN_TRIM_SPAN_MS && withinLimit

    ModalBottomSheet(
        onDismissRequest = dismissAndStop,
        sheetState = sheetState,
        containerColor = tokens.panelBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VibiSpacing.base, vertical = VibiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
        ) {
            Text(
                "This track is longer than the video — pick the range to insert",
                color = tokens.onBackgroundPrimary,
                style = typo.titleSm,
            )
            Text(
                "Video ${formatSec(videoDurationMs)} / Track ${formatSec(request.sourceDurationMs)}",
                color = tokens.onBackgroundPrimary.copy(alpha = 0.7f),
                style = typo.bodySm,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
            ) {
                IconButton(
                    modifier = Modifier
                        .size(VibiSpacing.touchMin)
                        .clip(CircleShape)
                        .background(
                            if (isPreviewing) tokens.accent.copy(alpha = 0.2f)
                            else tokens.chipBg
                        ),
                    onClick = {
                        if (isPreviewing) {
                            previewer.stop()
                            isPreviewing = false
                        } else {
                            previewer.play(
                                url = request.sourceUri,
                                onComplete = { isPreviewing = false },
                            )
                            isPreviewing = true
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPreviewing) "Stop" else "Preview",
                        tint = tokens.onBackgroundPrimary,
                    )
                }
                // 파형 라인 자체에 trim 핸들 + 선택 fill 을 overlay — 사용자가 파형을 보면서 직접
                // 구간을 잡도록. 핸들 hit zone 밖 영역은 WaveformPlayBar 의 tap/drag seek 가
                // 그대로 동작 (재생 중일 때).
                WaveformWithTrimOverlay(
                    peaks = peaks,
                    progressMs = previewer.progressMs.value,
                    sourceDurationMs = request.sourceDurationMs,
                    isPreviewing = isPreviewing,
                    onSeek = if (isPreviewing) { ms -> previewer.seekTo(ms) } else null,
                    rangeStartMs = request.rangeStartMs,
                    rangeEndMs = request.rangeEndMs,
                    accent = tokens.accent,
                    gripColor = tokens.onBackgroundPrimary,
                    onChange = onUpdateRange,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                if (withinLimit) "Selected ${formatSec(span)}"
                else "Selected ${formatSec(span)} — longer than video",
                color = if (withinLimit) tokens.onBackgroundPrimary.copy(alpha = 0.7f)
                else tokens.accent,
                style = typo.bodySm,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = VibiShape.lg,
                    onClick = dismissAndStop,
                ) { Text("Cancel", style = typo.bodySm) }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = canInsert,
                    shape = VibiShape.lg,
                    onClick = {
                        if (isPreviewing) {
                            previewer.stop()
                            isPreviewing = false
                        }
                        onConfirm()
                    },
                ) { Text("Insert", style = typo.bodySm) }
            }
            Spacer(Modifier.height(VibiSpacing.xs))
        }
    }
}

/**
 * WaveformPlayBar 위에 trim 핸들 + 선택 fill 을 overlay. 사용자가 파형을 보면서 같은 라인에서
 * 구간을 잡도록. 핸들 hit zone 밖 영역의 tap/drag 는 WaveformPlayBar 의 seek 가 처리.
 */
@Composable
private fun WaveformWithTrimOverlay(
    peaks: List<Float>,
    progressMs: Long,
    sourceDurationMs: Long,
    isPreviewing: Boolean,
    onSeek: ((Long) -> Unit)?,
    rangeStartMs: Long,
    rangeEndMs: Long,
    accent: Color,
    gripColor: Color,
    onChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val waveformHeight = 56.dp // WaveformPlayBar non-compact 기본 높이.
    val handleVisualWidth = 6.dp
    // hit zone 은 iOS HIG 44pt 기준 충족. visual(6dp) 과 분리해 시각적으로는 가느다란 막대 유지하면서
    // 드래그 정밀도 확보 — 두 핸들이 가까이 붙어도 양쪽 hit 영역이 22dp 씩 좌우로 펼쳐져 충돌하지 않음
    // (선택 구간이 최소 0dp 까지 좁혀져도 양쪽 hit 의 합산은 44dp + 44dp 로 겹치지만, 드래그 우선순위는
    // 각 TrimHandle 의 pointerInput 이 개별로 처리하므로 동작은 분리됨).
    val handleHitWidth = VibiSpacing.touchMin
    val onChangeState = rememberUpdatedState(onChange)
    val startMsState = rememberUpdatedState(rangeStartMs)
    val endMsState = rememberUpdatedState(rangeEndMs)

    BoxWithConstraints(
        modifier = modifier.height(waveformHeight),
    ) {
        val totalWidthDp = maxWidth
        val totalWidthPx = with(density) { totalWidthDp.toPx() }
        val source = sourceDurationMs.coerceAtLeast(1L)
        val startFrac = (rangeStartMs.toFloat() / source).coerceIn(0f, 1f)
        val endFrac = (rangeEndMs.toFloat() / source).coerceIn(0f, 1f)
        val startDp = totalWidthDp * startFrac
        val endDp = totalWidthDp * endFrac

        WaveformPlayBar(
            peaks = peaks,
            progressMs = progressMs,
            durationMs = sourceDurationMs,
            isPlaying = isPreviewing,
            modifier = Modifier.fillMaxWidth(),
            onSeek = onSeek,
        )
        // 선택 구간 fill — 반투명이라 아래 파형 peaks 그대로 보임. border 로 영역 경계 강조.
        Box(
            modifier = Modifier
                .offset(x = startDp)
                .width((endDp - startDp).coerceAtLeast(0.dp))
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.22f))
                .border(width = 1.dp, color = accent, shape = RoundedCornerShape(2.dp)),
        )
        TrimHandle(
            offsetX = startDp - handleHitWidth / 2,
            hitWidth = handleHitWidth,
            visualWidth = handleVisualWidth,
            visualColor = accent,
            gripColor = gripColor,
            onDrag = { dxPx ->
                if (totalWidthPx > 0f) {
                    val deltaMs = (dxPx / totalWidthPx) * source
                    val nextStart = (startMsState.value + deltaMs).toLong()
                        .coerceIn(0L, endMsState.value)
                    if (nextStart != startMsState.value) {
                        onChangeState.value(nextStart, endMsState.value)
                    }
                }
            },
        )
        TrimHandle(
            offsetX = endDp - handleHitWidth / 2,
            hitWidth = handleHitWidth,
            visualWidth = handleVisualWidth,
            visualColor = accent,
            gripColor = gripColor,
            onDrag = { dxPx ->
                if (totalWidthPx > 0f) {
                    val deltaMs = (dxPx / totalWidthPx) * source
                    val nextEnd = (endMsState.value + deltaMs).toLong()
                        .coerceIn(startMsState.value, source)
                    if (nextEnd != endMsState.value) {
                        onChangeState.value(startMsState.value, nextEnd)
                    }
                }
            },
        )
    }
}

@Composable
private fun TrimHandle(
    offsetX: Dp,
    hitWidth: Dp,
    visualWidth: Dp,
    visualColor: Color,
    gripColor: Color,
    onDrag: (dxPx: Float) -> Unit,
) {
    val onDragState = rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .width(hitWidth)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDragState.value(drag.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(visualWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(visualColor)
                .border(width = 1.dp, color = gripColor.copy(alpha = 0.6f), shape = RoundedCornerShape(2.dp)),
        )
    }
}

private fun formatSec(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (s < 10) "${m}:0${s}" else "${m}:${s}"
}

/** sheet 재진입 시 재추출 회피용 모듈 레벨 peaks 캐시. */
private val bgmTrimPeaksCache = mutableMapOf<String, List<Float>>()

/** 너무 짧은 구간 confirm 막기 위한 minimum span. */
private const val MIN_TRIM_SPAN_MS = 200L
