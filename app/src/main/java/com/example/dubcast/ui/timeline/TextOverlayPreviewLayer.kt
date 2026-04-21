package com.example.dubcast.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dubcast.domain.model.TextOverlay

@Composable
fun TextOverlayPreviewLayer(
    textOverlays: List<TextOverlay>,
    playbackPositionMs: Long,
    selectedOverlayId: String?,
    onSelect: (String?) -> Unit,
    onPositionChanged: (id: String, xPct: Float, yPct: Float) -> Unit = { _, _, _ -> },
    onFontSizeChanged: (id: String, sizeSp: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val visibleOverlays = remember(textOverlays, playbackPositionMs) {
        textOverlays.filter { playbackPositionMs in it.startMs..it.endMs }
    }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    // No outer pointerInput here — it would intercept taps that should reach
    // the underlying ImageOverlayLayer (and the video preview itself).
    Box(modifier = modifier.onSizeChanged { size = it }) {
        if (size == IntSize.Zero) return@Box
        for (overlay in visibleOverlays) {
            val color = remember(overlay.colorHex) {
                runCatching { Color(android.graphics.Color.parseColor(overlay.colorHex)) }
                    .getOrDefault(Color.White)
            }
            val sizePx = size
            val isSelected = overlay.id == selectedOverlayId

            // Local pan/zoom accumulators for smooth dragging. We deliberately
            // do NOT reset them in onGesture-end — when the new overlay.xPct/
            // fontSizeSp arrives via the repository flow the `remember(...)`
            // keys below tear them down, avoiding a snap-back frame.
            var dragX by remember(overlay.id, overlay.xPct) { mutableFloatStateOf(0f) }
            var dragY by remember(overlay.id, overlay.yPct) { mutableFloatStateOf(0f) }
            var sizeFactor by remember(overlay.id, overlay.fontSizeSp) { mutableFloatStateOf(1f) }

            val displayXPct = (overlay.xPct + dragX / sizePx.width * 100f).coerceIn(0f, 100f)
            val displayYPct = (overlay.yPct + dragY / sizePx.height * 100f).coerceIn(0f, 100f)
            val displayFontSp = (overlay.fontSizeSp * sizeFactor)
                .coerceIn(TextOverlay.MIN_FONT_SIZE_SP, TextOverlay.MAX_FONT_SIZE_SP)
            val xPx = (displayXPct / 100f) * sizePx.width
            val yPx = (displayYPct / 100f) * sizePx.height
            val previewSp = (displayFontSp / 1080f * sizePx.height) / density.density
            val xDp = with(density) { xPx.toDp() }
            val yDp = with(density) { yPx.toDp() }

            Box(
                modifier = Modifier
                    .offset(x = xDp - 60.dp, y = yDp - 16.dp)
                    .pointerInput(overlay.id) {
                        detectTapGestures(onTap = { onSelect(overlay.id) })
                    }
                    .pointerInput(overlay.id, isSelected, sizePx) {
                        if (!isSelected) return@pointerInput
                        // Visual updates are local (dragX/dragY/sizeFactor are
                        // displayed without any repository round-trip). Commit
                        // a single repository write per gesture once the user
                        // releases — `awaitEachGesture` lets us track the up
                        // event reliably, and `detectTransformGestures` itself
                        // would otherwise fire a write on every pointer
                        // event (laggy with 60+ Hz input).
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                if (pan != androidx.compose.ui.geometry.Offset.Zero) {
                                    dragX += pan.x
                                    dragY += pan.y
                                }
                                if (zoom != 1f) sizeFactor *= zoom
                                event.changes.forEach { it.consume() }
                                if (event.changes.all { !it.pressed }) break
                            } while (true)
                            // One commit per gesture — coalesces pinch/drag.
                            val finalX = (overlay.xPct + dragX / sizePx.width * 100f)
                                .coerceIn(0f, 100f)
                            val finalY = (overlay.yPct + dragY / sizePx.height * 100f)
                                .coerceIn(0f, 100f)
                            val finalSp = (overlay.fontSizeSp * sizeFactor).coerceIn(
                                TextOverlay.MIN_FONT_SIZE_SP, TextOverlay.MAX_FONT_SIZE_SP
                            )
                            if (finalX != overlay.xPct || finalY != overlay.yPct) {
                                onPositionChanged(overlay.id, finalX, finalY)
                            }
                            if (finalSp != overlay.fontSizeSp) {
                                onFontSizeChanged(overlay.id, finalSp)
                            }
                        }
                    }
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(2.dp)
                        } else Modifier
                    )
            ) {
                Text(
                    text = overlay.text,
                    color = color,
                    fontSize = previewSp.coerceAtLeast(8f).sp
                )
            }
        }
    }
}
