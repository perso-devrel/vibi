package com.example.dubcast.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    modifier: Modifier = Modifier
) {
    val visibleOverlays = remember(textOverlays, playbackPositionMs) {
        textOverlays.filter { playbackPositionMs in it.startMs..it.endMs }
    }
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSelect(null) })
            }
    ) {
        if (size == IntSize.Zero) return@Box
        for (overlay in visibleOverlays) {
            val color = remember(overlay.colorHex) {
                runCatching { Color(android.graphics.Color.parseColor(overlay.colorHex)) }
                    .getOrDefault(Color.White)
            }
            val sizePx = size
            val xPx = (overlay.xPct / 100f) * sizePx.width
            val yPx = (overlay.yPct / 100f) * sizePx.height
            // Scale font on preview: assume 1080-tall reference like ASS path so
            // preview matches what the renderer will produce on export.
            val previewSp = (overlay.fontSizeSp / 1080f * sizePx.height) /
                density.density
            val isSelected = overlay.id == selectedOverlayId
            val xDp = with(density) { xPx.toDp() }
            val yDp = with(density) { yPx.toDp() }
            Box(
                modifier = Modifier
                    .offset(x = xDp - 60.dp, y = yDp - 16.dp)
                    .pointerInput(overlay.id) {
                        detectTapGestures(onTap = { onSelect(overlay.id) })
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
