package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.example.dubcast.ui.theme.PlayheadDark
import com.example.dubcast.ui.theme.TimeRulerGray
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.model.SubtitleClip

private const val PX_PER_SECOND = 50f
private val VIDEO_TRACK_HEIGHT = 28.dp
private val TRIM_HANDLE_WIDTH = 14.dp
private val TRIM_HANDLE_HIT_WIDTH = 32.dp

@Composable
fun Timeline(
    videoDurationMs: Long,
    dubClips: List<DubClip>,
    subtitleClips: List<SubtitleClip>,
    imageClips: List<ImageClip>,
    playbackPositionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    isTrimming: Boolean,
    isVideoSelected: Boolean,
    selectedDubClipId: String?,
    selectedSubtitleClipId: String?,
    selectedImageClipId: String?,
    onVideoTrackTapped: () -> Unit,
    onDubClipSelected: (String?) -> Unit,
    onSubtitleClipSelected: (String?) -> Unit,
    onImageClipSelected: (String?) -> Unit,
    onDubClipMoved: (clipId: String, newStartMs: Long) -> Unit,
    onImageClipMoved: (clipId: String, newStartMs: Long) -> Unit,
    onImageClipResized: (clipId: String, newEndMs: Long) -> Unit,
    onSeek: (Long) -> Unit,
    onTrimStartChanged: (Long) -> Unit,
    onTrimEndChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val durationSeconds = videoDurationMs / 1000f
    val totalWidthPx = (durationSeconds * PX_PER_SECOND)
    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val effectiveTrimEnd = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
    val hasTrim = trimStartMs > 0L || (trimEndMs > 0L && trimEndMs < videoDurationMs)

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

                // Video track — tappable to select, shows trim handles when trimming
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(VIDEO_TRACK_HEIGHT)
                ) {
                    // Track background
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                if (isVideoSelected || isTrimming)
                                    Color(0xFFE4E7EC)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (isTrimming) {
                                        val ms = (offset.x / size.width * videoDurationMs).toLong()
                                        onSeek(ms.coerceIn(trimStartMs, effectiveTrimEnd))
                                    } else {
                                        onVideoTrackTapped()
                                    }
                                }
                            }
                    ) {
                        Text(
                            "Video",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(4.dp)
                        )
                    }

                    // Dim overlay outside trim range
                    if (isTrimming || hasTrim) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val pxPerMs = size.width / videoDurationMs.toFloat()
                            val trimStartPx = trimStartMs * pxPerMs
                            val trimEndPx = effectiveTrimEnd * pxPerMs
                            val dimColor = Color.Black.copy(alpha = 0.35f)

                            if (trimStartPx > 0f) {
                                drawRect(dimColor, Offset.Zero, Size(trimStartPx, size.height))
                            }
                            if (trimEndPx < size.width) {
                                drawRect(dimColor, Offset(trimEndPx, 0f), Size(size.width - trimEndPx, size.height))
                            }
                        }
                    }

                    // Trim handles — only in trim mode, aligned to video track height
                    if (isTrimming && videoDurationMs > 0) {
                        TrimHandle(
                            positionMs = trimStartMs,
                            videoDurationMs = videoDurationMs,
                            totalWidthPx = totalWidthPx,
                            isStart = true,
                            onDrag = { newMs -> onTrimStartChanged(newMs) }
                        )
                        TrimHandle(
                            positionMs = effectiveTrimEnd,
                            videoDurationMs = videoDurationMs,
                            totalWidthPx = totalWidthPx,
                            isStart = false,
                            onDrag = { newMs -> onTrimEndChanged(newMs) }
                        )
                    }
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

                // Image track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    imageClips.forEach { clip ->
                        ImageClipItem(
                            clip = clip,
                            videoDurationMs = videoDurationMs,
                            isSelected = clip.id == selectedImageClipId,
                            onClick = { onImageClipSelected(clip.id) },
                            onMoved = { newStartMs -> onImageClipMoved(clip.id, newStartMs) },
                            onResized = { newEndMs -> onImageClipResized(clip.id, newEndMs) },
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
                                val newPx = playheadXPx + (change.position.x - hitAreaWidthPx / 2f)
                                val newMs = (newPx / totalWidthPx * videoDurationMs)
                                    .toLong()
                                    .coerceIn(trimStartMs, effectiveTrimEnd)
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

/**
 * Trim handle — orange bar matching video track height, wide hit area for easy dragging.
 * Rendered inside the video track Box so it's constrained to that height.
 */
@Composable
private fun TrimHandle(
    positionMs: Long,
    videoDurationMs: Long,
    totalWidthPx: Float,
    isStart: Boolean,
    onDrag: (Long) -> Unit
) {
    val density = LocalDensity.current
    val handleXPx = positionMs.toFloat() / videoDurationMs * totalWidthPx
    val handleOffsetDp = with(density) { handleXPx.toDp() } - TRIM_HANDLE_HIT_WIDTH / 2
    val hitWidthPx = with(density) { TRIM_HANDLE_HIT_WIDTH.toPx() }

    Box(
        modifier = Modifier
            .offset(x = handleOffsetDp)
            .width(TRIM_HANDLE_HIT_WIDTH)
            .fillMaxHeight()
            .pointerInput(videoDurationMs, totalWidthPx) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newPx = handleXPx + (change.position.x - hitWidthPx / 2f)
                    val newMs = (newPx / totalWidthPx * videoDurationMs)
                        .toLong()
                        .coerceIn(0L, videoDurationMs)
                    onDrag(newMs)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Visible bar — thicker for visibility
        Box(
            modifier = Modifier
                .width(TRIM_HANDLE_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE8772E))
        )
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
