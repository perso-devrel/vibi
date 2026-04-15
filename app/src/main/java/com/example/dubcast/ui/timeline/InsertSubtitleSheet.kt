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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitlePosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertSubtitleSheet(
    currentPositionMs: Long,
    onDismiss: () -> Unit,
    onAdd: (text: String, startMs: Long, endMs: Long, position: SubtitlePosition) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var startMs by remember(currentPositionMs) { mutableLongStateOf(currentPositionMs) }
    var endMs by remember(currentPositionMs) { mutableLongStateOf(currentPositionMs + 3000L) }
    var anchor by remember { mutableStateOf(Anchor.BOTTOM) }
    var yOffsetPct by remember { mutableFloatStateOf(90f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Insert Subtitle",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Subtitle text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "${startMs / 1000.0}s",
                    onValueChange = {},
                    label = { Text("Start") },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = "${endMs / 1000.0}s",
                    onValueChange = {},
                    label = { Text("End") },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Position", style = MaterialTheme.typography.labelMedium)
            Row {
                Anchor.entries.forEach { a ->
                    FilterChip(
                        selected = anchor == a,
                        onClick = {
                            anchor = a
                            yOffsetPct = when (a) {
                                Anchor.TOP -> 10f
                                Anchor.MIDDLE -> 50f
                                Anchor.BOTTOM -> 90f
                            }
                        },
                        label = { Text(a.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Text("Y Offset: ${yOffsetPct.toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = yOffsetPct,
                onValueChange = { yOffsetPct = it },
                valueRange = 0f..100f
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onAdd(text, startMs, endMs, SubtitlePosition(anchor, yOffsetPct))
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Subtitle")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
