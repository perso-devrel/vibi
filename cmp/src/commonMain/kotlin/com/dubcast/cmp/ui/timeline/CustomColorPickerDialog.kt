package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

/**
 * 사용자 정의 색 선택 dialog — RGB(A) slider 기반.
 *
 * Material3 + foundation 만 사용해 KMP (Android + iOS) 호환. JVM-only API (java.awt.Color 등) 미사용.
 *
 * @param initialHex `#AARRGGBB` 또는 `#RRGGBB` 형식. 길이 안 맞으면 흰색으로 fallback.
 * @param showAlpha true 면 alpha slider 노출 (배경색 picker 용). false 면 alpha = FF 고정.
 * @param onSelect "선택" 클릭 시 `#AARRGGBB` 형식 hex 반환.
 * @param onDismiss dialog 닫기.
 */
@Composable
fun CustomColorPickerDialog(
    initialHex: String,
    showAlpha: Boolean = false,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialChannels = remember(initialHex) { parseChannels(initialHex) }
    var alpha by remember { mutableStateOf(initialChannels.a.toFloat()) }
    var red by remember { mutableStateOf(initialChannels.r.toFloat()) }
    var green by remember { mutableStateOf(initialChannels.g.toFloat()) }
    var blue by remember { mutableStateOf(initialChannels.b.toFloat()) }

    val effectiveAlpha = if (showAlpha) alpha.toInt() else 0xFF
    val previewColor = Color(
        alpha = effectiveAlpha / 255f,
        red = red.toInt() / 255f,
        green = green.toInt() / 255f,
        blue = blue.toInt() / 255f,
    )
    val hexString = formatArgbHex(effectiveAlpha, red.toInt(), green.toInt(), blue.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("색 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 큰 preview Box — 체커보드 배경 위에 색을 올려 alpha 가시화.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(previewColor, RoundedCornerShape(8.dp)),
                    )
                }
                Text(
                    text = hexString,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )

                ChannelSlider(label = "R", value = red, color = Color.Red) { red = it }
                ChannelSlider(label = "G", value = green, color = Color(0xFF2E7D32)) { green = it }
                ChannelSlider(label = "B", value = blue, color = Color.Blue) { blue = it }
                if (showAlpha) {
                    ChannelSlider(label = "A", value = alpha, color = Color.Gray) { alpha = it }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                onSelect(hexString)
                onDismiss()
            }) { Text("선택") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(20.dp).background(color, RoundedCornerShape(4.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.size(width = 18.dp, height = 20.dp),
        )
        Slider(
            value = value,
            valueRange = 0f..255f,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toInt().toString().padStart(3, ' '),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.size(width = 32.dp, height = 20.dp),
        )
    }
}

private data class ArgbChannels(val a: Int, val r: Int, val g: Int, val b: Int)

private fun parseChannels(hex: String): ArgbChannels {
    val cleaned = hex.removePrefix("#")
    return runCatching {
        val v = cleaned.toLong(16)
        when (cleaned.length) {
            8 -> ArgbChannels(
                a = ((v shr 24) and 0xFF).toInt(),
                r = ((v shr 16) and 0xFF).toInt(),
                g = ((v shr 8) and 0xFF).toInt(),
                b = (v and 0xFF).toInt(),
            )
            6 -> ArgbChannels(
                a = 0xFF,
                r = ((v shr 16) and 0xFF).toInt(),
                g = ((v shr 8) and 0xFF).toInt(),
                b = (v and 0xFF).toInt(),
            )
            else -> ArgbChannels(0xFF, 0xFF, 0xFF, 0xFF)
        }
    }.getOrElse { ArgbChannels(0xFF, 0xFF, 0xFF, 0xFF) }
}

private fun formatArgbHex(a: Int, r: Int, g: Int, b: Int): String {
    val ac = a.coerceIn(0, 255)
    val rc = r.coerceIn(0, 255)
    val gc = g.coerceIn(0, 255)
    val bc = b.coerceIn(0, 255)
    return "#" + toHex2(ac) + toHex2(rc) + toHex2(gc) + toHex2(bc)
}

private fun toHex2(v: Int): String {
    val s = v.toString(16).uppercase()
    return if (s.length == 1) "0$s" else s
}
