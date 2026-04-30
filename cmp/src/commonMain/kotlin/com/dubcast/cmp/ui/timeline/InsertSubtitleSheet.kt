package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dubcast.shared.domain.model.Anchor
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.SubtitlePosition

data class SubtitleStyleResult(
    val fontFamily: String,
    val fontSizeSp: Float,
    val colorHex: String,
    val backgroundColorHex: String,
)

/**
 * 자막 삽입 sheet — 텍스트 + 시간 + 위치(anchor + yOffset) + 스타일(폰트크기/글자색/배경).
 *
 * Phase A: 자막 스타일 선택 가능. 폰트 패밀리는 burn-in 안전성 위해 Noto Sans KR 고정.
 * 글자 색·배경색은 빠른 팔레트 chip 으로. 미세 조절은 후속.
 */
@Composable
fun InsertSubtitleSheet(
    playbackPositionMs: Long,
    videoDurationMs: Long,
    onConfirm: (
        text: String,
        startMs: Long,
        endMs: Long,
        position: SubtitlePosition,
        style: SubtitleStyleResult,
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var startMs by remember { mutableStateOf(playbackPositionMs) }
    var durationMs by remember { mutableStateOf(3000L) }
    var anchor by remember { mutableStateOf(Anchor.BOTTOM) }
    var yOffsetPct by remember { mutableStateOf(90f) }

    var fontSizeSp by remember { mutableStateOf(SubtitleClip.DEFAULT_FONT_SIZE_SP) }
    var colorHex by remember { mutableStateOf(SubtitleClip.DEFAULT_COLOR_HEX) }
    var backgroundColorHex by remember { mutableStateOf(SubtitleClip.DEFAULT_BACKGROUND_COLOR_HEX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("자막 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("자막 텍스트") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = (startMs / 1000).toString(),
                    onValueChange = { startMs = (it.toLongOrNull() ?: 0L) * 1000 },
                    label = { Text("시작 (초)") }
                )
                OutlinedTextField(
                    value = (durationMs / 1000).toString(),
                    onValueChange = { durationMs = ((it.toLongOrNull() ?: 1L) * 1000).coerceAtLeast(500L) },
                    label = { Text("길이 (초)") }
                )

                Text("위치", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Anchor.entries.forEach { a ->
                        OutlinedButton(
                            onClick = {
                                anchor = a
                                yOffsetPct = when (a) {
                                    Anchor.TOP -> 10f
                                    Anchor.MIDDLE -> 50f
                                    Anchor.BOTTOM -> 90f
                                }
                            }
                        ) {
                            Text(
                                when (a) {
                                    Anchor.TOP -> if (anchor == a) "✓ 상단" else "상단"
                                    Anchor.MIDDLE -> if (anchor == a) "✓ 중간" else "중간"
                                    Anchor.BOTTOM -> if (anchor == a) "✓ 하단" else "하단"
                                }
                            )
                        }
                    }
                }
                Text("Y 오프셋: ${yOffsetPct.toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = yOffsetPct, valueRange = 0f..100f, onValueChange = { yOffsetPct = it })

                Spacer(Modifier.height(4.dp))

                Text("스타일", style = MaterialTheme.typography.titleSmall)
                Text("글자 크기: ${fontSizeSp.toInt()}sp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = fontSizeSp,
                    valueRange = 12f..32f,
                    onValueChange = { fontSizeSp = it }
                )

                Text("글자 색", style = MaterialTheme.typography.bodySmall)
                ColorPaletteRow(
                    selectedHex = colorHex,
                    palette = TEXT_COLOR_PALETTE,
                    onSelect = { colorHex = it }
                )

                Text("배경", style = MaterialTheme.typography.bodySmall)
                ColorPaletteRow(
                    selectedHex = backgroundColorHex,
                    palette = BG_COLOR_PALETTE,
                    onSelect = { backgroundColorHex = it }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank() && (startMs + durationMs) <= videoDurationMs,
                onClick = {
                    onConfirm(
                        text,
                        startMs,
                        startMs + durationMs,
                        SubtitlePosition(anchor = anchor, yOffsetPct = yOffsetPct),
                        SubtitleStyleResult(
                            fontFamily = SubtitleClip.DEFAULT_FONT_FAMILY,
                            fontSizeSp = fontSizeSp,
                            colorHex = colorHex,
                            backgroundColorHex = backgroundColorHex,
                        )
                    )
                    onDismiss()
                }
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

internal val TEXT_COLOR_PALETTE: List<String> = listOf(
    "#FFFFFFFF", // white
    "#FF000000", // black
    "#FFFFEB3B", // yellow
    "#FFE53935", // red
    "#FF1E88E5", // blue
)

internal val BG_COLOR_PALETTE: List<String> = listOf(
    "#00000000", // transparent
    "#80000000", // black 50%
    "#CC000000", // black 80%
    "#80FFFFFF", // white 50%
)

@Composable
internal fun ColorPaletteRow(
    selectedHex: String,
    palette: List<String>,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        palette.forEach { hex ->
            val selected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(parseArgbHex(hex), CircleShape)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) Color.White else Color.Black.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (hex.startsWith("#00") || hex.equals("#00000000", ignoreCase = true)) {
                    Text("∅", color = Color.Gray)
                }
            }
        }
    }
}

private fun parseArgbHex(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val v = cleaned.toLong(16)
    return when (cleaned.length) {
        8 -> Color(
            alpha = ((v shr 24) and 0xFF) / 255f,
            red = ((v shr 16) and 0xFF) / 255f,
            green = ((v shr 8) and 0xFF) / 255f,
            blue = (v and 0xFF) / 255f,
        )
        6 -> Color(
            alpha = 1f,
            red = ((v shr 16) and 0xFF) / 255f,
            green = ((v shr 8) and 0xFF) / 255f,
            blue = (v and 0xFF) / 255f,
        )
        else -> Color.White
    }
}
