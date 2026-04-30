package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 구간 선택 sheet — RangeSlider 로 시작/끝 시간 지정 후 삭제 또는 복제.
 *
 * legacy `RangeActionSheet` 의 단순화된 등가. 6 핵심 기능 한정에 맞춰 volume/speed 옵션은 제외.
 */
@Composable
fun RangeSelectionSheet(
    startMs: Long,
    endMs: Long,
    videoDurationMs: Long,
    onUpdateStart: (Long) -> Unit,
    onUpdateEnd: (Long) -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("구간 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeSlider(
                    value = startMs.toFloat()..endMs.toFloat(),
                    valueRange = 0f..videoDurationMs.toFloat().coerceAtLeast(1f),
                    onValueChange = { range ->
                        if (range.start.toLong() != startMs) onUpdateStart(range.start.toLong())
                        if (range.endInclusive.toLong() != endMs) onUpdateEnd(range.endInclusive.toLong())
                    }
                )
                Text(
                    "${startMs / 1000}s ~ ${endMs / 1000}s (${(endMs - startMs) / 1000}s)",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onRemove(); onDismiss() }) {
                        Text("구간 삭제")
                    }
                    Button(onClick = { onDuplicate(); onDismiss() }) {
                        Text("구간 복제")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
