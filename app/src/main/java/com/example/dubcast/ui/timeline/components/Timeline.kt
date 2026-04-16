package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.dubcast.ui.theme.PlayheadDark
import com.example.dubcast.ui.theme.TimeRulerGray
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.SubtitleClip

private const val PX_PER_SECOND = 50f // pixels per second at default zoom

@Composable
fun Timeline(
    videoDurationMs: Long,
    dubClips: List<DubClip>,
    subtitleClips: List<SubtitleClip>,
    playbackPositionMs: Long,
    selectedDubClipId: String?,
    selectedSubtitleClipId: String?,
    onDubClipSelected: (String?) -> Unit,
    onSubtitleClipSelected: (String?) -> Unit,
    onDubClipMoved: (clipId: String, newStartMs: Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val durationSeconds = videoDurationMs / 1000f
    val totalWidthPx = (durationSeconds * PX_PER_SECOND)
    val totalWidthDp = with(density) { totalWidthPx.toDp() }

    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .width(totalWidthDp.coerceAtLeast(300.dp))
                    .fillMaxHeight()
            ) {
                // Time ruler
                TimeRuler(
                    durationMs = videoDurationMs,
                    totalWidth = totalWidthDp.coerceAtLeast(300.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                )

                // Video track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val ms = (offset.x / size.width * videoDurationMs).toLong()
                                onSeek(ms.coerceIn(0, videoDurationMs))
                            }
                        }
                ) {
                    Text(
                        "Video",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                // Dubbing track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    dubClips.forEach { clip ->
                        DubClipItem(
                            clip = clip,
                            videoDurationMs = videoDurationMs,
                            isSelected = clip.id == selectedDubClipId,
                            onClick = { onDubClipSelected(clip.id) },
                            onMoved = { newStartMs -> onDubClipMoved(clip.id, newStartMs) },
                            totalWidthDp = totalWidthDp.coerceAtLeast(300.dp)
                        )
                    }
                }

                // Subtitle track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    subtitleClips.forEach { clip ->
                        SubtitleClipItem(
                            clip = clip,
                            videoDurationMs = videoDurationMs,
                            isSelected = clip.id == selectedSubtitleClipId,
                            onClick = { onSubtitleClipSelected(clip.id) },
                            totalWidthDp = totalWidthDp.coerceAtLeast(300.dp)
                        )
                    }
                }
            }

            // Playhead (draggable)
            if (videoDurationMs > 0) {
                val playheadXPx = playbackPositionMs.toFloat() / videoDurationMs * totalWidthPx
                val hitAreaWidth = 32.dp
                val hitAreaWidthPx = with(density) { hitAreaWidth.toPx() }
                val playheadOffsetDp = with(density) { playheadXPx.toDp() } - hitAreaWidth / 2

                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(hitAreaWidth)
                        .fillMaxHeight()
                        .pointerInput(videoDurationMs, totalWidthPx) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                // change.position.x is relative to this Box's left edge
                                // Box is centered on the playhead, so actual position = playheadXPx + (position.x - halfWidth)
                                val newPx = playheadXPx + (change.position.x - hitAreaWidthPx / 2f)
                                val newMs = (newPx / totalWidthPx * videoDurationMs)
                                    .toLong()
                                    .coerceIn(0, videoDurationMs)
                                onSeek(newMs)
                            }
                        }
                ) {
                    Canvas(
                        modifier = Modifier
                            .offset(x = hitAreaWidth / 2 - 1.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                    ) {
                        drawLine(
                            color = PlayheadDark,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(
    durationMs: Long,
    totalWidth: Dp,
    modifier: Modifier = Modifier
) {
    val durationSeconds = (durationMs / 1000).toInt()
    val intervalSeconds = when {
        durationSeconds <= 30 -> 5
        durationSeconds <= 120 -> 10
        else -> 30
    }

    Canvas(modifier = modifier) {
        val pxPerMs = size.width / durationMs.toFloat()
        for (sec in 0..durationSeconds step intervalSeconds) {
            val x = sec * 1000f * pxPerMs
            drawLine(
                color = TimeRulerGray,
                start = Offset(x, size.height * 0.5f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
    }

    // Text labels drawn as overlay — simplified
    Box(modifier = modifier) {
        val density = LocalDensity.current
        val pxPerMs = with(density) { totalWidth.toPx() } / durationMs.toFloat()
        for (sec in 0..durationSeconds step intervalSeconds) {
            val offsetX = with(density) { (sec * 1000f * pxPerMs).toDp() }
            Text(
                text = "${sec}s",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.offset(x = offsetX),
                color = TimeRulerGray
            )
        }
    }
}
