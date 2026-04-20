package com.example.dubcast.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.SubtitleClip

@Composable
fun SubtitlePreviewLayer(
    subtitleClips: List<SubtitleClip>,
    playbackPositionMs: Long,
    selectedSubtitleClipId: String?,
    onSelectSubtitle: (String) -> Unit,
    onUpdatePosition: (id: String, xPct: Float, yPct: Float, widthPct: Float, heightPct: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeClips = subtitleClips.filter { playbackPositionMs in it.startMs until it.endMs }

    BoxWithConstraints(modifier = modifier) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        for (clip in activeClips) {
            key(clip.id) {
                if (clip.isSticker &&
                    clip.xPct != null && clip.yPct != null &&
                    clip.widthPct != null && clip.heightPct != null
                ) {
                    StickerSubtitleBox(
                        clip = clip,
                        isSelected = clip.id == selectedSubtitleClipId,
                        containerW = containerW,
                        containerH = containerH,
                        onTap = { onSelectSubtitle(clip.id) },
                        onPositionChanged = { x, y, w, h ->
                            onUpdatePosition(clip.id, x, y, w, h)
                        }
                    )
                } else if (!clip.isSticker) {
                    ManualSubtitleText(clip = clip, containerH = containerH)
                }
            }
        }
    }
}

@Composable
private fun StickerSubtitleBox(
    clip: SubtitleClip,
    isSelected: Boolean,
    containerW: Float,
    containerH: Float,
    onTap: () -> Unit,
    onPositionChanged: (xPct: Float, yPct: Float, widthPct: Float, heightPct: Float) -> Unit
) {
    val density = LocalDensity.current

    // Drag deltas in px — accumulated during gesture, reset after commit
    var dragX by remember(clip.id) { mutableFloatStateOf(0f) }
    var dragY by remember(clip.id) { mutableFloatStateOf(0f) }
    var resizeDW by remember(clip.id) { mutableFloatStateOf(0f) }
    var resizeDH by remember(clip.id) { mutableFloatStateOf(0f) }

    val displayXPct = (clip.xPct!! + dragX / containerW * 100f).coerceIn(0f, 100f)
    val displayYPct = (clip.yPct!! + dragY / containerH * 100f).coerceIn(0f, 100f)
    val displayWPct = (clip.widthPct!! + resizeDW / containerW * 200f).coerceIn(10f, 100f)
    val displayHPct = (clip.heightPct!! + resizeDH / containerH * 200f).coerceIn(5f, 60f)

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
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            }
            .pointerInput(clip.id, containerW, containerH) {
                detectDragGestures(
                    onDragEnd = {
                        val finalX = (clip.xPct!! + dragX / containerW * 100f).coerceIn(0f, 100f)
                        val finalY = (clip.yPct!! + dragY / containerH * 100f).coerceIn(0f, 100f)
                        val finalW = (clip.widthPct!! + resizeDW / containerW * 200f).coerceIn(10f, 100f)
                        val finalH = (clip.heightPct!! + resizeDH / containerH * 200f).coerceIn(5f, 60f)
                        onPositionChanged(finalX, finalY, finalW, finalH)
                        dragX = 0f
                        dragY = 0f
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
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = clip.text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .pointerInput(clip.id, containerW, containerH) {
                        detectDragGestures(
                            onDragEnd = {
                                val finalX = (clip.xPct!! + dragX / containerW * 100f).coerceIn(0f, 100f)
                                val finalY = (clip.yPct!! + dragY / containerH * 100f).coerceIn(0f, 100f)
                                val finalW = (clip.widthPct!! + resizeDW / containerW * 200f).coerceIn(10f, 100f)
                                val finalH = (clip.heightPct!! + resizeDH / containerH * 200f).coerceIn(5f, 60f)
                                onPositionChanged(finalX, finalY, finalW, finalH)
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

@Composable
private fun ManualSubtitleText(clip: SubtitleClip, containerH: Float) {
    val density = LocalDensity.current
    val yDp = with(density) { (clip.position.yOffsetPct / 100f * containerH).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = yDp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = clip.text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
