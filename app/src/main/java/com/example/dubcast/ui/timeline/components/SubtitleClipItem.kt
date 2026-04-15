package com.example.dubcast.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.SubtitleClip

@Composable
fun SubtitleClipItem(
    clip: SubtitleClip,
    videoDurationMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    totalWidthDp: Dp
) {
    val density = LocalDensity.current
    val totalWidthPx = with(density) { totalWidthDp.toPx() }
    val pxPerMs = totalWidthPx / videoDurationMs.toFloat()

    val offsetXDp = with(density) { (clip.startMs * pxPerMs).toDp() }
    val durationMs = clip.endMs - clip.startMs
    val widthDp = with(density) { (durationMs * pxPerMs).toDp() }

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .offset(x = offsetXDp)
            .width(widthDp.coerceAtLeast(20.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2196F3).copy(alpha = 0.7f))
            .border(2.dp, borderColor, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
