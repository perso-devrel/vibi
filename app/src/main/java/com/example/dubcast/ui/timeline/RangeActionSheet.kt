package com.example.dubcast.ui.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.Segment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeActionSheet(
    segment: Segment,
    pendingStartMs: Long,
    pendingEndMs: Long,
    pendingVolume: Float,
    pendingSpeed: Float,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onApplyVolume: (Float) -> Unit,
    onApplySpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimStart = segment.trimStartMs
    val trimEnd = if (segment.trimEndMs <= 0L) segment.durationMs else segment.trimEndMs
    val canAct = pendingEndMs - pendingStartMs >= 100L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "구간 편집", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Text(
                text = "범위: ${formatMs(pendingStartMs)} ~ ${formatMs(pendingEndMs)}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            RangeSlider(
                value = pendingStartMs.toFloat()..pendingEndMs.toFloat(),
                onValueChange = { v ->
                    onRangeChange(v.start.toLong(), v.endInclusive.toLong())
                },
                valueRange = trimStart.toFloat()..trimEnd.toFloat()
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onDuplicate,
                    enabled = canAct,
                    modifier = Modifier.weight(1f)
                ) { Text("복제") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    enabled = canAct,
                    modifier = Modifier.weight(1f)
                ) { Text("삭제") }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "볼륨: ${"%.2f".format(pendingVolume)}",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Slider(
                value = pendingVolume,
                onValueChange = onVolumeChange,
                valueRange = 0f..2f
            )
            Button(
                onClick = { onApplyVolume(pendingVolume) },
                enabled = canAct,
                modifier = Modifier.fillMaxWidth()
            ) { Text("볼륨 적용") }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "속도: ${"%.2f".format(pendingSpeed)}x",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Slider(
                value = pendingSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.25f..4f,
                steps = 14
            )
            Button(
                onClick = { onApplySpeed(pendingSpeed) },
                enabled = canAct,
                modifier = Modifier.fillMaxWidth()
            ) { Text("속도 적용") }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) { Text("취소") }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val frac = (ms % 1000) / 100
    return "%d:%02d.%d".format(m, s, frac)
}
