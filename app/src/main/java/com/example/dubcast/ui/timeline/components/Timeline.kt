package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
/** Standard height for one lane row in any overlay/dub/subtitle/bgm track. */
internal val TRACK_LANE_HEIGHT = 28.dp
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
    onImageClipLaneChanged: (clipId: String, delta: Int) -> Unit = { _, _ -> },
    onImageSegmentResized: (segmentId: String, newDurationMs: Long) -> Unit,
    textOverlays: List<com.example.dubcast.domain.model.TextOverlay> = emptyList(),
    selectedTextOverlayId: String? = null,
    onTextOverlaySelected: (String?) -> Unit = {},
    onTextOverlayLongPressed: (String) -> Unit = {},
    onTextOverlayResized: (String, Long) -> Unit = { _, _ -> },
    onTextOverlayMoved: (String, Long) -> Unit = { _, _ -> },
    onTextOverlayLaneChanged: (String, Int) -> Unit = { _, _ -> },
    bgmClips: List<com.example.dubcast.domain.model.BgmClip> = emptyList(),
    selectedBgmClipId: String? = null,
    onBgmClipSelected: (String?) -> Unit = {},
    isRangeSelecting: Boolean = false,
    rangeTargetSegmentId: String? = null,
    rangeTargetSegmentStartMs: Long = 0L,
    rangeTargetTrimStartMs: Long = 0L,
    rangeTargetTrimEndMs: Long = 0L,
    pendingRangeStartMs: Long = 0L,
    pendingRangeEndMs: Long = 0L,
    onRangeStartChanged: (Long) -> Unit = {},
    onRangeEndChanged: (Long) -> Unit = {},
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
                                } else {
                                    onSeek(globalMs.coerceAtLeast(0L))
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

                        // Range edit handles — drag on the timeline to set the
                        // start/end of the operation range (volume/speed/duplicate/
                        // delete). Local segment ms are converted to global so
                        // the handles share the trim handle visual language.
                        if (isRangeSelecting && rangeTargetSegmentId != null) {
                            val rangeStartGlobal = rangeTargetSegmentStartMs +
                                (pendingRangeStartMs - rangeTargetTrimStartMs).coerceAtLeast(0L)
                            val rangeEndGlobal = rangeTargetSegmentStartMs +
                                (pendingRangeEndMs - rangeTargetTrimStartMs).coerceAtLeast(0L)
                            // Faint green wash between the two handles so the
                            // entire selected interval is visually obvious,
                            // not just the bounding handles.
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val pxPerMs = size.width / totalDurationMs.toFloat()
                                val sx = rangeStartGlobal * pxPerMs
                                val ex = rangeEndGlobal * pxPerMs
                                if (ex > sx) {
                                    drawRect(
                                        color = Color(0xFF34C759).copy(alpha = 0.18f),
                                        topLeft = Offset(sx, 0f),
                                        size = Size(ex - sx, size.height)
                                    )
                                }
                            }
                            RangeHandle(
                                positionMs = rangeStartGlobal,
                                totalDurationMs = totalDurationMs,
                                totalWidthPx = totalWidthPx,
                                isStart = true,
                                onDrag = { newGlobal ->
                                    val local = (newGlobal - rangeTargetSegmentStartMs +
                                        rangeTargetTrimStartMs)
                                        .coerceIn(rangeTargetTrimStartMs, rangeTargetTrimEndMs)
                                    onRangeStartChanged(local)
                                }
                            )
                            RangeHandle(
                                positionMs = rangeEndGlobal,
                                totalDurationMs = totalDurationMs,
                                totalWidthPx = totalWidthPx,
                                isStart = false,
                                onDrag = { newGlobal ->
                                    val local = (newGlobal - rangeTargetSegmentStartMs +
                                        rangeTargetTrimStartMs)
                                        .coerceIn(rangeTargetTrimStartMs, rangeTargetTrimEndMs)
                                    onRangeEndChanged(local)
                                }
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

                // Dub track — only render when there's at least one clip so
                // the timeline doesn't reserve empty rows under the video.
                if (dubClips.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(totalWidthDp)
                            .height(TRACK_LANE_HEIGHT)
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
                }

                // Subtitle track
                if (subtitleClips.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(totalWidthDp)
                            .height(TRACK_LANE_HEIGHT)
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
                }

                OverlayTrackRow(
                    imageClips = imageClips,
                    textOverlays = textOverlays,
                    totalDurationMs = totalDurationMs,
                    totalWidthDp = totalWidthDp,
                    selectedImageClipId = selectedImageClipId,
                    selectedTextOverlayId = selectedTextOverlayId,
                    onImageClipSelected = onImageClipSelected,
                    onImageClipMoved = onImageClipMoved,
                    onImageClipResized = onImageClipResized,
                    onImageClipLaneChanged = onImageClipLaneChanged,
                    onTextOverlaySelected = onTextOverlaySelected,
                    onTextOverlayLongPressed = onTextOverlayLongPressed,
                    onTextOverlayResized = onTextOverlayResized,
                    onTextOverlayMoved = onTextOverlayMoved,
                    onTextOverlayLaneChanged = onTextOverlayLaneChanged
                )

                // BGM track
                if (bgmClips.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(totalWidthDp)
                            .height(TRACK_LANE_HEIGHT)
                            .padding(vertical = 1.dp)
                    ) {
                        bgmClips.forEach { clip ->
                            BgmTrackItem(
                                clip = clip,
                                videoDurationMs = totalDurationMs,
                                isSelected = clip.id == selectedBgmClipId,
                                onClick = { onBgmClipSelected(clip.id) },
                                totalWidthDp = totalWidthDp
                            )
                        }
                    }
                }
            }

            // Playhead
            val playheadXPx = playbackPositionMs.toFloat() / totalDurationMs * totalWidthPx
            val hitAreaWidth = 32.dp
            val playheadOffsetDp = with(density) { playheadXPx.toDp() } - hitAreaWidth / 2

            // Capture latest props so the drag lambda doesn't close over stale
            // values during fast drags (otherwise the playhead jumps).
            val latestPosition = androidx.compose.runtime.rememberUpdatedState(playbackPositionMs)
            val latestTotalDuration = androidx.compose.runtime.rememberUpdatedState(totalDurationMs)
            val latestTotalWidthPx = androidx.compose.runtime.rememberUpdatedState(totalWidthPx)
            val latestOnSeek = androidx.compose.runtime.rememberUpdatedState(onSeek)

            Box(
                modifier = Modifier
                    .offset(x = playheadOffsetDp)
                    .width(hitAreaWidth)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val width = latestTotalWidthPx.value
                            val total = latestTotalDuration.value
                            if (width <= 0f || total <= 0L) return@detectHorizontalDragGestures
                            val deltaMs = (dragAmount / width * total).toLong()
                            val next = (latestPosition.value + deltaMs).coerceIn(0L, total)
                            latestOnSeek.value(next)
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
        // No width overlap: a previous +1px hack let the next segment cover
        // the previous segment's resize handle, blocking image resize taps.
        val widthDp = with(density) {
            (segDuration.toFloat() / totalDurationMs * totalWidthPx).toDp()
        }
        val baseColor = when (segment.type) {
            SegmentType.VIDEO -> VideoSegmentColor
            SegmentType.IMAGE -> ImageSegmentColor
        }
        // Visual indicator: tint VIDEO segments that have non-default speed
        // or volume so the user can immediately tell which pieces have been
        // modified after applying a range edit.
        val color = if (segment.type == SegmentType.VIDEO) {
            val speedAccent = segment.speedScale != 1f
            val volumeAccent = segment.volumeScale != 1f
            when {
                speedAccent && volumeAccent -> Color(0xFF8E44AD) // purple
                speedAccent -> Color(0xFF1976D2)                 // blue (speed)
                volumeAccent -> Color(0xFF2E7D32)                // green (volume)
                else -> baseColor
            }
        } else baseColor

        Box(
            modifier = Modifier
                .offset(x = leftDp)
                .width(widthDp)
                .fillMaxHeight()
                .background(color)
                .pointerInput(segment.id, isTrimming) {
                    detectTapGestures { tapOffset ->
                        val pxPerMs = size.width.toFloat() / segDuration.toFloat()
                        val localMs = (tapOffset.x / pxPerMs).toLong().coerceAtLeast(0L)
                        // Always seek the playhead to the tap location so range
                        // mode opens its default 1s window starting where the
                        // user tapped, not at the segment's leftmost edge.
                        onTrackTappedForSeek(segStart + localMs)
                        if (!isTrimming) {
                            onSegmentSelected(segment.id)
                        }
                    }
                }
        ) {
            // Inner labels and per-segment selection borders are intentionally
            // omitted so a multi-segment timeline reads as a single continuous
            // strip. Selection state is conveyed through the inline edit panel
            // and the green range handles instead.

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
) = DraggableHandle(
    positionMs = positionMs,
    totalDurationMs = totalDurationMs,
    totalWidthPx = totalWidthPx,
    color = TrimHandleColor,
    onDrag = onDrag
)

@Composable
private fun RangeHandle(
    positionMs: Long,
    totalDurationMs: Long,
    totalWidthPx: Float,
    isStart: Boolean,
    onDrag: (Long) -> Unit
) = DraggableHandle(
    positionMs = positionMs,
    totalDurationMs = totalDurationMs,
    totalWidthPx = totalWidthPx,
    color = RangeHandleColor,
    onDrag = onDrag
)

private val TrimHandleColor = Color(0xFFE8772E)
private val RangeHandleColor = Color(0xFF34C759)

/**
 * Shared draggable timeline handle (used by trim + range UIs). Uses
 * `rememberUpdatedState` + accumulated `dragAmount` so the lambda doesn't
 * close over stale props mid-drag (which made fast drags jumpy in the older
 * "absolute position from change.position" implementation).
 */
@Composable
private fun DraggableHandle(
    positionMs: Long,
    totalDurationMs: Long,
    totalWidthPx: Float,
    color: Color,
    onDrag: (Long) -> Unit
) {
    val density = LocalDensity.current
    val handleXPx = positionMs.toFloat() / totalDurationMs * totalWidthPx
    val handleOffsetDp = with(density) { handleXPx.toDp() } - TRIM_HANDLE_HIT_WIDTH / 2

    val latestPosition = androidx.compose.runtime.rememberUpdatedState(positionMs)
    val latestTotalDuration = androidx.compose.runtime.rememberUpdatedState(totalDurationMs)
    val latestTotalWidthPx = androidx.compose.runtime.rememberUpdatedState(totalWidthPx)
    val latestOnDrag = androidx.compose.runtime.rememberUpdatedState(onDrag)

    Box(
        modifier = Modifier
            .offset(x = handleOffsetDp)
            .width(TRIM_HANDLE_HIT_WIDTH)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val width = latestTotalWidthPx.value
                    val total = latestTotalDuration.value
                    if (width <= 0f || total <= 0L) return@detectHorizontalDragGestures
                    val deltaMs = (dragAmount / width * total).toLong()
                    val next = (latestPosition.value + deltaMs).coerceIn(0L, total)
                    latestOnDrag.value(next)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(TRIM_HANDLE_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color)
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
    onResized: (newEndMs: Long) -> Unit,
    onMoved: (newStartMs: Long) -> Unit = {},
    onLaneChanged: (delta: Int) -> Unit = {},
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

    var resizeDeltaPx by remember(overlay.id, overlay.endMs) { mutableFloatStateOf(0f) }
    var moveDeltaPx by remember(overlay.id, overlay.startMs) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(x = offsetXDp + with(density) { moveDeltaPx.toDp() })
            .width((widthDp + with(density) { resizeDeltaPx.toDp() }).coerceAtLeast(20.dp))
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
            .pointerInput(overlay.id, overlay.startMs, pxPerMs) {
                // 2D drag: horizontal -> startMs, vertical -> lane change.
                val laneRowHeightPx = TRACK_LANE_HEIGHT.toPx()
                var verticalAcc = 0f
                detectDragGestures(
                    onDragStart = { verticalAcc = 0f },
                    onDragEnd = {
                        if (pxPerMs > 0f) {
                            val deltaMs = (moveDeltaPx / pxPerMs).toLong()
                            val target = (overlay.startMs + deltaMs).coerceAtLeast(0L)
                            if (target != overlay.startMs) onMoved(target)
                        }
                        val laneDelta = (verticalAcc / laneRowHeightPx).toInt()
                        if (laneDelta != 0) onLaneChanged(laneDelta)
                    },
                    onDragCancel = {
                        moveDeltaPx = 0f
                        verticalAcc = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    moveDeltaPx += dragAmount.x
                    verticalAcc += dragAmount.y
                }
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

        if (isSelected) {
            // Right-edge drag handle: matches the photo segment resize affordance
            // so users can stretch text overlay duration directly on the timeline.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(IMAGE_RESIZE_HIT_WIDTH)
                    .fillMaxHeight()
                    .pointerInput(overlay.id, overlay.endMs, pxPerMs) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (pxPerMs > 0f) {
                                    val deltaMs = (resizeDeltaPx / pxPerMs).toLong()
                                    val target = (overlay.endMs + deltaMs)
                                        .coerceAtLeast(overlay.startMs + 100L)
                                    onResized(target)
                                }
                                resizeDeltaPx = 0f
                            },
                            onDragCancel = { resizeDeltaPx = 0f }
                        ) { _, dragAmount ->
                            resizeDeltaPx += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(IMAGE_RESIZE_HANDLE_WIDTH)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(ImageResizeHandleColor)
                )
            }
        }
    }
}

/**
 * Combined image+text overlay track row. Both element types share the lane
 * space so they can sit side-by-side or stacked freely on the same row.
 */
@Composable
private fun OverlayTrackRow(
    imageClips: List<ImageClip>,
    textOverlays: List<com.example.dubcast.domain.model.TextOverlay>,
    totalDurationMs: Long,
    totalWidthDp: Dp,
    selectedImageClipId: String?,
    selectedTextOverlayId: String?,
    onImageClipSelected: (String?) -> Unit,
    onImageClipMoved: (String, Long) -> Unit,
    onImageClipResized: (String, Long) -> Unit,
    onImageClipLaneChanged: (String, Int) -> Unit,
    onTextOverlaySelected: (String?) -> Unit,
    onTextOverlayLongPressed: (String) -> Unit,
    onTextOverlayResized: (String, Long) -> Unit,
    onTextOverlayMoved: (String, Long) -> Unit,
    onTextOverlayLaneChanged: (String, Int) -> Unit
) {
    if (imageClips.isEmpty() && textOverlays.isEmpty()) return
    val maxImageLane = imageClips.maxOfOrNull { it.lane } ?: -1
    val maxTextLane = textOverlays.maxOfOrNull { it.lane } ?: -1
    val laneCount = (maxOf(maxImageLane, maxTextLane) + 1).coerceAtLeast(1)
    Box(
        modifier = Modifier
            .width(totalWidthDp)
            .height(TRACK_LANE_HEIGHT * laneCount)
            .padding(vertical = 1.dp)
    ) {
        imageClips.forEach { clip ->
            Box(modifier = Modifier
                .offset(y = TRACK_LANE_HEIGHT * clip.lane)
                .height(TRACK_LANE_HEIGHT)
                .width(totalWidthDp)
            ) {
                ImageClipItem(
                    clip = clip,
                    videoDurationMs = totalDurationMs,
                    isSelected = clip.id == selectedImageClipId,
                    onClick = { onImageClipSelected(clip.id) },
                    onMoved = { newStartMs -> onImageClipMoved(clip.id, newStartMs) },
                    onResized = { newEndMs -> onImageClipResized(clip.id, newEndMs) },
                    onLaneChanged = { delta -> onImageClipLaneChanged(clip.id, delta) },
                    totalWidthDp = totalWidthDp
                )
            }
        }
        textOverlays.forEach { overlay ->
            Box(modifier = Modifier
                .offset(y = TRACK_LANE_HEIGHT * overlay.lane)
                .height(TRACK_LANE_HEIGHT)
                .width(totalWidthDp)
            ) {
                TextOverlayTrackItem(
                    overlay = overlay,
                    videoDurationMs = totalDurationMs,
                    isSelected = overlay.id == selectedTextOverlayId,
                    onClick = { onTextOverlaySelected(overlay.id) },
                    onLongPress = { onTextOverlayLongPressed(overlay.id) },
                    onResized = { newEndMs -> onTextOverlayResized(overlay.id, newEndMs) },
                    onMoved = { newStartMs -> onTextOverlayMoved(overlay.id, newStartMs) },
                    onLaneChanged = { delta -> onTextOverlayLaneChanged(overlay.id, delta) },
                    totalWidthDp = totalWidthDp
                )
            }
        }
    }
}

private val BgmTrackColor = Color(0xFF26A69A)

@Composable
private fun BgmTrackItem(
    clip: com.example.dubcast.domain.model.BgmClip,
    videoDurationMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    totalWidthDp: Dp
) {
    if (videoDurationMs <= 0L) return
    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }
    val pxPerMs = totalWidthPx / videoDurationMs.toFloat()
    // Cap visible BGM length to remaining timeline (no looping policy).
    val effectiveEndMs = (clip.startMs + clip.sourceDurationMs).coerceAtMost(videoDurationMs)
    val widthMs = (effectiveEndMs - clip.startMs).coerceAtLeast(1L)
    val offsetXDp = with(density) { (clip.startMs * pxPerMs).toDp() }
    val widthDp = with(density) { (widthMs * pxPerMs).toDp() }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .offset(x = offsetXDp)
            .width(widthDp.coerceAtLeast(20.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(BgmTrackColor.copy(alpha = 0.85f))
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .pointerInput(clip.id) {
                detectTapGestures { onClick() }
            }
    ) {
        Text(
            text = "♪",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
