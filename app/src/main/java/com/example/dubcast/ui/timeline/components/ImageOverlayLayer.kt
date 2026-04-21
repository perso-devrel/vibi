package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dubcast.domain.model.ImageClip

@Composable
fun ImageOverlayLayer(
    imageClips: List<ImageClip>,
    playbackPositionMs: Long,
    selectedImageClipId: String?,
    onSelect: (String) -> Unit,
    onUpdate: (id: String, xPct: Float, yPct: Float, widthPct: Float, heightPct: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeClips = imageClips.filter { playbackPositionMs in it.startMs until it.endMs }

    BoxWithConstraints(modifier = modifier) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        for (clip in activeClips) {
            key(clip.id) {
                OverlayImage(
                    clip = clip,
                    isSelected = clip.id == selectedImageClipId,
                    containerW = containerW,
                    containerH = containerH,
                    onTap = { onSelect(clip.id) },
                    onPositionChanged = { x, y, w, h -> onUpdate(clip.id, x, y, w, h) }
                )
            }
        }
    }
}

@Composable
private fun OverlayImage(
    clip: ImageClip,
    isSelected: Boolean,
    containerW: Float,
    containerH: Float,
    onTap: () -> Unit,
    onPositionChanged: (xPct: Float, yPct: Float, widthPct: Float, heightPct: Float) -> Unit
) {
    val density = LocalDensity.current

    var dragX by remember(clip.id, clip.xPct, clip.yPct) { mutableFloatStateOf(0f) }
    var dragY by remember(clip.id, clip.xPct, clip.yPct) { mutableFloatStateOf(0f) }
    var resizeDW by remember(clip.id, clip.widthPct, clip.heightPct) { mutableFloatStateOf(0f) }
    var resizeDH by remember(clip.id, clip.widthPct, clip.heightPct) { mutableFloatStateOf(0f) }

    val displayXPct = (clip.xPct + dragX / containerW * 100f).coerceIn(0f, 100f)
    val displayYPct = (clip.yPct + dragY / containerH * 100f).coerceIn(0f, 100f)
    val displayWPct = (clip.widthPct + resizeDW / containerW * 200f).coerceIn(5f, 100f)
    val displayHPct = (clip.heightPct + resizeDH / containerH * 200f).coerceIn(5f, 100f)

    val widthPx = displayWPct / 100f * containerW
    val heightPx = displayHPct / 100f * containerH
    val leftPx = displayXPct / 100f * containerW - widthPx / 2f
    val topPx = displayYPct / 100f * containerH - heightPx / 2f

    val leftDp = with(density) { leftPx.toDp() }
    val topDp = with(density) { topPx.toDp() }
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }

    Box(
        modifier = Modifier
            .offset(x = leftDp, y = topDp)
            .size(widthDp, heightDp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(clip.id) {
                detectTapGestures { onTap() }
            }
            .pointerInput(clip.id, containerW, containerH) {
                detectDragGestures(
                    onDragEnd = {
                        // Don't reset dragX/Y here — that would snap the image
                        // back to the old clip.xPct for one frame. The
                        // remember(clip.id, clip.xPct, clip.yPct) above auto
                        // -resets the deltas as soon as the new clip.xPct
                        // flows back through the repository.
                        val finalX = (clip.xPct + dragX / containerW * 100f).coerceIn(0f, 100f)
                        val finalY = (clip.yPct + dragY / containerH * 100f).coerceIn(0f, 100f)
                        onPositionChanged(finalX, finalY, clip.widthPct, clip.heightPct)
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                }
            }
    ) {
        AsyncImage(
            model = clip.imageUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .background(Color.Transparent)
                .size(widthDp, heightDp)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(Color.White, CircleShape)
                    .pointerInput(clip.id, containerW, containerH) {
                        detectDragGestures(
                            onDragEnd = {
                                val finalW = (clip.widthPct + resizeDW / containerW * 200f).coerceIn(5f, 100f)
                                val finalH = (clip.heightPct + resizeDH / containerH * 200f).coerceIn(5f, 100f)
                                onPositionChanged(clip.xPct, clip.yPct, finalW, finalH)
                                resizeDW = 0f
                                resizeDH = 0f
                            },
                            onDragCancel = {
                                resizeDW = 0f
                                resizeDH = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            resizeDW += dragAmount.x
                            resizeDH += dragAmount.y
                        }
                    }
            )
        }
    }
}
