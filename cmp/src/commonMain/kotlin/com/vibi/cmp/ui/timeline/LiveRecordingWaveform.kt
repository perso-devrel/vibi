package com.vibi.cmp.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 녹음 중 라이브 파형 — iOS 보이스메모식. 플레이헤드(빨간 세로선 + 위아래 점)는 캔버스 폭의
 * [playheadFraction] 지점에 고정되고, 입력 레벨이 좌측으로 흘러간다. 하단에 1초 단위 시간 눈금.
 *
 * [WaveformPlayBar] 와 책임 분리 — 시간 축 매핑·고정 플레이헤드·tick 라벨은 녹음 중에만 의미가
 * 있고, Preview/재생은 "전체 파일을 한눈에" 가 의도라 모델이 다르다.
 *
 * - 막대 1개 = [sampleIntervalMs] 동안 측정한 amplitude. levels.last() 가 가장 최근 = 플레이헤드.
 * - 시간 눈금: elapsedMs 기준 정수 초마다 마크 + "mm:ss" 라벨.
 * - 입력 가정: levels 는 [sampleIntervalMs] 간격으로 추가된 0..1 정규화 amplitude. AudioInsertSheet
 *   의 50ms 폴링 루프 출력을 그대로 받는다.
 */
@Composable
fun LiveRecordingWaveform(
    levels: List<Float>,
    elapsedMs: Long,
    playheadColor: Color,
    barColor: Color,
    tickColor: Color,
    modifier: Modifier = Modifier,
    sampleIntervalMs: Long = 50L,
    playheadFraction: Float = 0.62f,
) {
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = TextStyle(
        color = tickColor,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val barPx = 1.dp.toPx()
            val gapPx = 1.dp.toPx()
            val slotPx = barPx + gapPx
            val playheadX = size.width * playheadFraction
            val cy = size.height / 2f
            // 위아래 dot 가 잘리지 않도록 파형 영역 상하 4dp 패딩.
            val verticalPad = 4.dp.toPx()
            val maxHalfHeight = (size.height - verticalPad * 2f) / 2f

            val n = levels.size
            for (i in 0 until n) {
                val x = playheadX - (n - 1 - i) * slotPx
                if (x + barPx < 0f || x > size.width) continue
                val peak = levels[i].coerceIn(0f, 1f)
                // sqrt — WaveformPlayBar 와 동일한 dynamic range 보정.
                val h = max(barPx / 2f, sqrt(peak) * maxHalfHeight)
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, cy - h),
                    size = Size(barPx, h * 2f),
                )
            }

            // 플레이헤드 — 빨간 세로선 + 양 끝 점.
            val dotRadius = 4.dp.toPx()
            val lineWidth = 2.dp.toPx()
            drawRect(
                color = playheadColor,
                topLeft = Offset(playheadX - lineWidth / 2f, dotRadius),
                size = Size(lineWidth, size.height - dotRadius * 2f),
            )
            drawCircle(
                color = playheadColor,
                radius = dotRadius,
                center = Offset(playheadX, dotRadius),
            )
            drawCircle(
                color = playheadColor,
                radius = dotRadius,
                center = Offset(playheadX, size.height - dotRadius),
            )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            val barPx = 1.dp.toPx()
            val gapPx = 1.dp.toPx()
            val slotPx = barPx + gapPx
            // 파형 캔버스와 동일한 px/ms — 막대와 tick 이 같은 속도로 흘러야 함.
            val pxPerMs = slotPx / sampleIntervalMs.toFloat()
            val playheadX = size.width * playheadFraction
            val pxPerSec = pxPerMs * 1000f
            if (pxPerSec <= 0f) return@Canvas

            val elapsedSec = elapsedMs / 1000L
            // 가시 정수 초 범위 — 플레이헤드 양쪽으로 캔버스 끝까지.
            val pastSeconds = (playheadX / pxPerSec).toInt() + 1
            val futureSeconds = ((size.width - playheadX) / pxPerSec).toInt() + 1
            val firstK = (elapsedSec - pastSeconds).coerceAtLeast(0L)
            val lastK = elapsedSec + futureSeconds

            val tickHeightPx = 4.dp.toPx()
            val labelTopPx = 8.dp.toPx()

            for (k in firstK..lastK) {
                val x = playheadX - (elapsedMs - k * 1000L) * pxPerMs
                if (x < 0f || x > size.width) continue
                drawRect(
                    color = tickColor,
                    topLeft = Offset(x - 0.5f, 0f),
                    size = Size(1f, tickHeightPx),
                )
                val label = formatTick(k)
                val measured = textMeasurer.measure(label, tickStyle)
                val labelX = (x - measured.size.width / 2f)
                    .coerceIn(0f, size.width - measured.size.width.toFloat())
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(labelX, labelTopPx),
                    style = tickStyle,
                )
            }
        }
    }
}

/** "mm:ss" — tick 라벨용 절대 시간 포맷. */
private fun formatTick(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val m = s / 60
    val rem = s % 60
    val mStr = m.toString().padStart(2, '0')
    val remStr = rem.toString().padStart(2, '0')
    return "$mStr:$remStr"
}
