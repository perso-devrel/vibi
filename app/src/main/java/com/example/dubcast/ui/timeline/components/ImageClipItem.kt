package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.ImageClip

private val ImageClipIndigo = Color(0xFF5B5BD6)
private val TRIM_HIT_WIDTH = 16.dp
private const val MIN_DURATION_MS = 500L

@Composable
fun ImageClipItem(
    clip: ImageClip,
    videoDurationMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMoved: (newStartMs: Long) -> Unit,
    onResized: (newEndMs: Long) -> Unit,
    totalWidthDp: Dp
) {
    if (videoDurationMs <= 0L) return

    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }
    val pxPerMs = totalWidthPx / videoDurationMs.toFloat()

    val durationMs = (clip.endMs - clip.startMs).coerceAtLeast(MIN_DURATION_MS)
    val offsetXDp = with(density) { (clip.startMs * pxPerMs).toDp() }
    val widthDp = with(density) { (durationMs * pxPerMs).toDp() }

    var dragOffset by remember(clip.startMs, clip.endMs) { mutableFloatStateOf(0f) }
    var dragStartMs by remember { mutableFloatStateOf(clip.startMs.toFloat()) }
    var resizeDelta by remember(clip.endMs) { mutableFloatStateOf(0f) }
    var resizeStartEndMs by remember { mutableFloatStateOf(clip.endMs.toFloat()) }

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .offset(x = offsetXDp + with(density) { dragOffset.toDp() })
            .width(widthDp.coerceAtLeast(20.dp) + with(density) { resizeDelta.toDp() })
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(ImageClipIndigo.copy(alpha = 0.75f))
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .pointerInput(clip.id) {
                detectTapGestures { onClick() }
            }
            .pointerInput(clip.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragStartMs = clip.startMs.toFloat() },
                    onDragEnd = {
                        val newStartMs = ((dragStartMs * pxPerMs + dragOffset) / pxPerMs).toLong()
                        onMoved(newStartMs.coerceAtLeast(0L))
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                ) { _, dragAmount ->
                    dragOffset += dragAmount
                }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Image",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(TRIM_HIT_WIDTH)
                    .fillMaxHeight()
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { resizeStartEndMs = clip.endMs.toFloat() },
                            onDragEnd = {
                                val newEndMs = ((resizeStartEndMs * pxPerMs + resizeDelta) / pxPerMs).toLong()
                                onResized(newEndMs)
                                resizeDelta = 0f
                            },
                            onDragCancel = { resizeDelta = 0f }
                        ) { _, dragAmount ->
                            resizeDelta += dragAmount
                        }
                    }
                    .background(Color.White.copy(alpha = 0.6f))
            )
        }
    }
}
