package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.ui.theme.PlayheadDark
import com.example.dubcast.ui.theme.TimeRulerGray

private const val PX_PER_SECOND = 50f
private val VIDEO_TRACK_HEIGHT = 28.dp
private val TRIM_HANDLE_WIDTH = 14.dp
private val TRIM_HANDLE_HIT_WIDTH = 32.dp
private val APPEND_BUTTON_SIZE = 28.dp
private val IMAGE_RESIZE_HANDLE_WIDTH = 6.dp
private val IMAGE_RESIZE_HIT_WIDTH = 20.dp
private val VideoSegmentColor = Color(0xFF4A4A4A)
private val ImageSegmentColor = Color(0xFF5B5BD6)
private val ImageResizeHandleColor = Color(0xFFFFD080)

@Composable
fun Timeline(
    totalDurationMs: Long,
    segments: List<Segment>,
    dubClips: List<DubClip>,
    subtitleClips: List<SubtitleClip>,
    imageClips: List<ImageClip>,
    playbackPositionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    isTrimming: Boolean,
    selectedSegmentId: String?,
    selectedDubClipId: String?,
    selectedSubtitleClipId: String?,
    selectedImageClipId: String?,
    onSegmentSelected: (String?) -> Unit,
    onDubClipSelected: (String?) -> Unit,
    onSubtitleClipSelected: (String?) -> Unit,
    onImageClipSelected: (String?) -> Unit,
    onDubClipMoved: (clipId: String, newStartMs: Long) -> Unit,
    onImageClipMoved: (clipId: String, newStartMs: Long) -> Unit,
    onImageClipResized: (clipId: String, newEndMs: Long) -> Unit,
    onImageSegmentResized: (segmentId: String, newDurationMs: Long) -> Unit,
    textOverlays: List<com.example.dubcast.domain.model.TextOverlay> = emptyList(),
    selectedTextOverlayId: String? = null,
    onTextOverlaySelected: (String?) -> Unit = {},
    onTextOverlayLongPressed: (String) -> Unit = {},
    onAppendRequested: () -> Unit,
    onSeek: (Long) -> Unit,
    onTrimStartChanged: (Long) -> Unit,
    onTrimEndChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    if (totalDurationMs <= 0L || segments.isEmpty()) return

    val durationSeconds = totalDurationMs / 1000f
    val totalWidthPx = durationSeconds * PX_PER_SECOND
    val totalWidthDp = with(density) { totalWidthPx.toDp() }.coerceAtLeast(300.dp)
    val effectiveTrimEnd = if (trimEndMs <= 0L) totalDurationMs else trimEndMs
    val hasTrim = trimStartMs > 0L || (trimEndMs > 0L && trimEndMs < totalDurationMs)

    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .width(totalWidthDp + APPEND_BUTTON_SIZE + 8.dp)
                    .fillMaxHeight()
            ) {
                TimeRuler(
                    durationMs = totalDurationMs,
                    totalWidth = totalWidthDp,
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(18.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(totalWidthDp + APPEND_BUTTON_SIZE + 8.dp)
                        .height(VIDEO_TRACK_HEIGHT)
                ) {
                    // Per-segment boxes
                    Box(
                        modifier = Modifier
                            .width(totalWidthDp)
                            .fillMaxHeight()
                    ) {
                        RenderSegments(
                            segments = segments,
                            totalDurationMs = totalDurationMs,
                            totalWidthDp = totalWidthDp,
                            selectedSegmentId = selectedSegmentId,
                            isTrimming = isTrimming,
                            onSegmentSelected = onSegmentSelected,
                            onImageSegmentResized = onImageSegmentResized,
                            onTrackTappedForSeek = { globalMs ->
                                if (isTrimming) {
                                    onSeek(globalMs.coerceIn(trimStartMs, effectiveTrimEnd))
                                }
                            }
                        )

                        if (isTrimming || hasTrim) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val pxPerMs = size.width / totalDurationMs.toFloat()
                                val trimStartPx = trimStartMs * pxPerMs
                                val trimEndPx = effectiveTrimEnd * pxPerMs
                                val dimColor = Color.Black.copy(alpha = 0.35f)
                                if (trimStartPx > 0f) {
                                    drawRect(dimColor, Offset.Zero, Size(trimStartPx, size.height))
                                }
                                if (trimEndPx < size.width) {
                                    drawRect(
                                        dimColor,
                                        Offset(trimEndPx, 0f),
                                        Size(size.width - trimEndPx, size.height)
                                    )
                                }
                            }
                        }

                        if (isTrimming) {
                            TrimHandle(
                                positionMs = trimStartMs,
                                totalDurationMs = totalDurationMs,
                                totalWidthPx = totalWidthPx,
                                isStart = true,
                                onDrag = { newMs -> onTrimStartChanged(newMs) }
                            )
                            TrimHandle(
                                positionMs = effectiveTrimEnd,
                                totalDurationMs = totalDurationMs,
                                totalWidthPx = totalWidthPx,
                                isStart = false,
                                onDrag = { newMs -> onTrimEndChanged(newMs) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (!isTrimming) {
                        Box(
                            modifier = Modifier
                                .size(APPEND_BUTTON_SIZE)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    detectTapGestures { onAppendRequested() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add next clip",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Dub track
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    dubClips.forEach { clip ->
                        DubClipItem(
                            clip = clip,
                            videoDurationMs = totalDurationMs,
                            isSelected = clip.id == selectedDubClipId,
                            onClick = { onDubClipSelected(clip.id) },
                            onMoved = { newStartMs -> onDubClipMoved(clip.id, newStartMs) },
                            totalWidthDp = totalWidthDp
                        )
                    }
                }

                // Subtitle track
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    subtitleClips.forEach { clip ->
                        SubtitleClipItem(
                            clip = clip,
                            videoDurationMs = totalDurationMs,
                            isSelected = clip.id == selectedSubtitleClipId,
                            onClick = { onSubtitleClipSelected(clip.id) },
                            totalWidthDp = totalWidthDp
                        )
                    }
                }

                // Sticker image track
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    imageClips.forEach { clip ->
                        ImageClipItem(
                            clip = clip,
                            videoDurationMs = totalDurationMs,
                            isSelected = clip.id == selectedImageClipId,
                            onClick = { onImageClipSelected(clip.id) },
                            onMoved = { newStartMs -> onImageClipMoved(clip.id, newStartMs) },
                            onResized = { newEndMs -> onImageClipResized(clip.id, newEndMs) },
                            totalWidthDp = totalWidthDp
                        )
                    }
                }

                // Text overlay track — long-press to duplicate
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .height(28.dp)
                        .padding(vertical = 1.dp)
                ) {
                    textOverlays.forEach { overlay ->
                        TextOverlayTrackItem(
                            overlay = overlay,
                            videoDurationMs = totalDurationMs,
                            isSelected = overlay.id == selectedTextOverlayId,
                            onClick = { onTextOverlaySelected(overlay.id) },
                            onLongPress = { onTextOverlayLongPressed(overlay.id) },
                            totalWidthDp = totalWidthDp
                        )
                    }
                }
            }

            // Playhead
            val playheadXPx = playbackPositionMs.toFloat() / totalDurationMs * totalWidthPx
            val hitAreaWidth = 32.dp
            val hitAreaWidthPx = with(density) { hitAreaWidth.toPx() }
            val playheadOffsetDp = with(density) { playheadXPx.toDp() } - hitAreaWidth / 2

            Box(
                modifier = Modifier
                    .offset(x = playheadOffsetDp)
                    .width(hitAreaWidth)
                    .fillMaxHeight()
                    .pointerInput(totalDurationMs, totalWidthPx) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newPx = playheadXPx + (change.position.x - hitAreaWidthPx / 2f)
                            val newMs = (newPx / totalWidthPx * totalDurationMs)
                                .toLong()
                                .coerceIn(0L, totalDurationMs)
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

@Composable
private fun RenderSegments(
    segments: List<Segment>,
    totalDurationMs: Long,
    totalWidthDp: Dp,
    selectedSegmentId: String?,
    isTrimming: Boolean,
    onSegmentSelected: (String?) -> Unit,
    onImageSegmentResized: (segmentId: String, newDurationMs: Long) -> Unit,
    onTrackTappedForSeek: (Long) -> Unit
) {
    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }
    var runningOffsetMs = 0L
    for (segment in segments) {
        val segStart = runningOffsetMs
        val segDuration = segment.effectiveDurationMs
        runningOffsetMs += segDuration
        val leftDp = with(density) {
            (segStart.toFloat() / totalDurationMs * totalWidthPx).toDp()
        }
        val widthDp = with(density) {
            (segDuration.toFloat() / totalDurationMs * totalWidthPx).toDp()
        }
        val color = when (segment.type) {
            SegmentType.VIDEO -> VideoSegmentColor
            SegmentType.IMAGE -> ImageSegmentColor
        }
        val borderColor = if (segment.id == selectedSegmentId) {
            MaterialTheme.colorScheme.primary
        } else Color.Transparent

        Box(
            modifier = Modifier
                .offset(x = leftDp)
                .width(widthDp)
                .fillMaxHeight()
                .background(color)
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(segment.id, isTrimming) {
                    detectTapGestures { tapOffset ->
                        if (isTrimming) {
                            val pxPerMs = size.width.toFloat() / segDuration.toFloat()
                            val localMs = (tapOffset.x / pxPerMs).toLong()
                            onTrackTappedForSeek(segStart + localMs)
                        } else {
                            onSegmentSelected(segment.id)
                        }
                    }
                }
        ) {
            if (borderColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(1.dp)
                        .background(Color.Transparent)
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRect(
                            color = borderColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
                }
            }
            Text(
                text = when (segment.type) {
                    SegmentType.VIDEO -> "Video"
                    SegmentType.IMAGE -> "Photo"
                },
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )

            if (segment.type == SegmentType.IMAGE && !isTrimming) {
                ImageSegmentResizeHandle(
                    segmentId = segment.id,
                    currentDurationMs = segment.durationMs,
                    pxPerMs = totalWidthPx / totalDurationMs.toFloat(),
                    isSelected = segment.id == selectedSegmentId,
                    onCommit = onImageSegmentResized,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ImageSegmentResizeHandle(
    segmentId: String,
    currentDurationMs: Long,
    pxPerMs: Float,
    isSelected: Boolean,
    onCommit: (segmentId: String, newDurationMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var dragOffsetPx by remember(segmentId, currentDurationMs) { mutableFloatStateOf(0f) }

    val handleAlpha = if (isSelected) 1f else 0.55f
    val visibleHandleColor = ImageResizeHandleColor.copy(alpha = handleAlpha)

    Box(
        modifier = modifier
            .width(IMAGE_RESIZE_HIT_WIDTH)
            .fillMaxHeight()
            .pointerInput(segmentId, currentDurationMs, pxPerMs) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (pxPerMs > 0f) {
                            val deltaMs = (dragOffsetPx / pxPerMs).toLong()
                            val target = (currentDurationMs + deltaMs).coerceAtLeast(0L)
                            onCommit(segmentId, target)
                        }
                        dragOffsetPx = 0f
                    },
                    onDragCancel = { dragOffsetPx = 0f }
                ) { _, dragAmount ->
                    dragOffsetPx += dragAmount
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(x = with(density) { dragOffsetPx.toDp() })
                .width(IMAGE_RESIZE_HANDLE_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(visibleHandleColor)
        )
    }
}

@Composable
private fun TrimHandle(
    positionMs: Long,
    totalDurationMs: Long,
    totalWidthPx: Float,
    isStart: Boolean,
    onDrag: (Long) -> Unit
) {
    val density = LocalDensity.current
    val handleXPx = positionMs.toFloat() / totalDurationMs * totalWidthPx
    val handleOffsetDp = with(density) { handleXPx.toDp() } - TRIM_HANDLE_HIT_WIDTH / 2
    val hitWidthPx = with(density) { TRIM_HANDLE_HIT_WIDTH.toPx() }

    Box(
        modifier = Modifier
            .offset(x = handleOffsetDp)
            .width(TRIM_HANDLE_HIT_WIDTH)
            .fillMaxHeight()
            .pointerInput(totalDurationMs, totalWidthPx) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val newPx = handleXPx + (change.position.x - hitWidthPx / 2f)
                    val newMs = (newPx / totalWidthPx * totalDurationMs)
                        .toLong()
                        .coerceIn(0L, totalDurationMs)
                    onDrag(newMs)
                }
            },
        contentAlignment = Alignment.Center
    ) {
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

private val TextOverlayTrackColor = Color(0xFFE8772E)

@Composable
private fun TextOverlayTrackItem(
    overlay: com.example.dubcast.domain.model.TextOverlay,
    videoDurationMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    totalWidthDp: Dp
) {
    if (videoDurationMs <= 0L) return
    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }
    val pxPerMs = totalWidthPx / videoDurationMs.toFloat()
    val durationMs = (overlay.endMs - overlay.startMs).coerceAtLeast(1L)
    val offsetXDp = with(density) { (overlay.startMs * pxPerMs).toDp() }
    val widthDp = with(density) { (durationMs * pxPerMs).toDp() }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .offset(x = offsetXDp)
            .width(widthDp.coerceAtLeast(20.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(TextOverlayTrackColor.copy(alpha = 0.85f))
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .pointerInput(overlay.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Text(
            text = overlay.text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
