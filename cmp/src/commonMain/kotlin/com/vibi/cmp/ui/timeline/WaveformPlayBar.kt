package com.vibi.cmp.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vibi.cmp.theme.LocalVibiColors
import kotlin.math.max

/**
 * 파형 막대 + playhead 재생바. BgmActionSheet / AudioSeparationSheet 의 미리듣기 영역에 노출.
 *
 * - peaks: 0..1 normalized peak. 가로로 균등 분배해 각 막대로 그림.
 * - progressMs / durationMs: 현재 재생 위치 (절대 ms). durationMs=0 이면 playhead 0 위치.
 * - peaks 가 비어있을 때(Android stub / 추출 실패) 는 단순 progress bar 로 fallback.
 * - tap / horizontal drag 로 seek — onSeek 가 null 이면 비활성.
 * - compact=true: stem 행 내부용 미니 변형 — 28.dp 높이, duration 텍스트 / placeholder 라인 stripped.
 */
@Composable
fun WaveformPlayBar(
    peaks: List<Float>,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onSeek: ((Long) -> Unit)? = null,
    barColorOverride: Color? = null,
    playedColorOverride: Color? = null,
    playheadColorOverride: Color? = null,
    trackBgOverride: Color? = null,
) {
    val tokens = LocalVibiColors.current
    val barColor = barColorOverride ?: tokens.onBackgroundPrimary.copy(alpha = 0.45f)
    val playedColor = playedColorOverride ?: tokens.accent
    val playheadColor = playheadColorOverride ?: tokens.onBackgroundPrimary
    val trackBg = trackBgOverride
        ?: tokens.timelineBarTrack.copy(alpha = if (compact) 0.45f else 0.55f)
    val containerHeight = if (compact) 28.dp else 56.dp
    val cornerRadius = if (compact) 4.dp else 6.dp

    var widthPx by remember { mutableFloatStateOf(0f) }
    val progressFraction = if (durationMs > 0L) {
        (progressMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val seekMod = if (onSeek != null && durationMs > 0L) {
        Modifier
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val w = if (widthPx > 0f) widthPx else size.width.toFloat()
                    if (w <= 0f) return@detectTapGestures
                    val frac = (offset.x / w).coerceIn(0f, 1f)
                    onSeek((frac * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ ->
                    val w = if (widthPx > 0f) widthPx else size.width.toFloat()
                    if (w <= 0f) return@detectHorizontalDragGestures
                    val frac = (change.position.x / w).coerceIn(0f, 1f)
                    onSeek((frac * durationMs).toLong())
                }
            }
    } else Modifier

    Box(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .height(containerHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackBg)
            .then(seekMod),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(containerHeight)) {
            widthPx = size.width
            val peakCount = peaks.size
            if (peakCount > 0) {
                // dp 기반 슬롯/막대 — hair-thin voice UI (Spotify mini-scrubber / SoundCloud 류). 1dp 면 3x
                // 디스플레이에서 raw 3px → 한 막대가 짧은 선처럼 보임. canvas 폭에서 barCount 동적 계산해
                // 컴팩트 행과 풀폭에서 일관된 시각 밀도. peaks 가 더 dense 하면 fraction 매핑으로 리샘플.
                val barPx = 1.dp.toPx()
                val gapPx = (if (compact) 0.5.dp else 1.dp).toPx()
                val slotPx = barPx + gapPx
                val barCount = (size.width / slotPx).toInt().coerceAtLeast(1)
                val cornerR = CornerRadius(barPx / 2f, barPx / 2f)
                val cy = size.height / 2f
                val maxHalfHeight = size.height / 2f - 4f
                for (i in 0 until barCount) {
                    val peakIdx = ((i.toFloat() / barCount) * peakCount).toInt().coerceIn(0, peakCount - 1)
                    val peak = peaks[peakIdx].coerceIn(0f, 1f)
                    // 시각적 dynamic range 보정 — 너무 작은 peak 도 살짝 보이도록 sqrt 적용.
                    val h = max(barPx / 2f, kotlin.math.sqrt(peak) * maxHalfHeight)
                    val x = slotPx * i
                    val played = (i.toFloat() / barCount) < progressFraction
                    drawRoundRect(
                        color = if (played) playedColor else barColor,
                        topLeft = Offset(x, cy - h),
                        size = Size(barPx, h * 2f),
                        cornerRadius = cornerR,
                    )
                }
            } else if (!compact) {
                // fallback — 단순 progress 막대 (full-size 한정 — compact 행은 빈 trackBg 만).
                val barHeight = 6f
                val cy = size.height / 2f
                drawBar(
                    x = 0f,
                    cy = cy,
                    width = size.width,
                    halfHeight = barHeight / 2f,
                    color = barColor,
                )
                drawBar(
                    x = 0f,
                    cy = cy,
                    width = size.width * progressFraction,
                    halfHeight = barHeight / 2f,
                    color = playedColor,
                )
            }
            // playhead 세로선.
            if (durationMs > 0L) {
                val px = (size.width * progressFraction).coerceIn(0f, size.width)
                drawRect(
                    color = playheadColor,
                    topLeft = Offset(px - 1f, 0f),
                    size = Size(2f, size.height),
                )
            }
        }
        // duration 표기 — 우측 하단 작게. compact 행에서는 공간 부족으로 생략.
        if (durationMs > 0L && !compact) {
            Box(modifier = Modifier.fillMaxWidth().height(containerHeight)) {
                Text(
                    text = formatMs(progressMs) + " / " + formatMs(durationMs),
                    color = tokens.onBackgroundPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    x: Float,
    cy: Float,
    width: Float,
    halfHeight: Float,
    color: Color,
) {
    drawRect(
        color = color,
        topLeft = Offset(x, cy - halfHeight),
        size = Size(width, halfHeight * 2f),
    )
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (s < 10) "$m:0$s" else "$m:$s"
}
