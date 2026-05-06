package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dubcast.shared.ui.timeline.ExportVariantPickerState

/**
 * 저장/공유 흐름의 variant 선택 sheet.
 *
 *  - [ExportVariantPickerState.Save]  : 체크박스 multi-select, default 모두 선택. confirm = "저장 (n/total)".
 *  - [ExportVariantPickerState.Share] : 라디오 single-select, default "original". confirm = "공유".
 *
 * legacy `RangeSelectionSheet` 와 동일하게 AlertDialog 기반 — picker 가 짧고 sheet 동작 통일성을 위해.
 */
@Composable
fun ExportVariantPickerSheet(
    picker: ExportVariantPickerState,
    onToggleSave: (String) -> Unit,
    onSelectShare: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val title = when (picker) {
        is ExportVariantPickerState.Save -> "저장할 변종 선택"
        is ExportVariantPickerState.Share -> "공유할 변종 선택"
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            // variant 가 5+ 일 때 dialog 가 화면 하단을 넘어 confirm/cancel 버튼이 가려지는 사고 방지.
            // heightIn(max = 320.dp) + verticalScroll 로 dialog 내부에서 스크롤 가능하게.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (picker) {
                    is ExportVariantPickerState.Save -> {
                        picker.variants.forEach { variant ->
                            val checked = variant.key in picker.selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSave(variant.key) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleSave(variant.key) },
                                )
                                Text(
                                    variant.displayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    is ExportVariantPickerState.Share -> {
                        picker.variants.forEach { variant ->
                            val selected = variant.key == picker.selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectShare(variant.key) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onSelectShare(variant.key) },
                                )
                                Text(
                                    variant.displayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (picker) {
                is ExportVariantPickerState.Save -> {
                    val canConfirm = picker.selected.isNotEmpty()
                    Button(
                        enabled = canConfirm,
                        onClick = onConfirm,
                    ) {
                        Text("저장 (${picker.selected.size}/${picker.variants.size})")
                    }
                }
                is ExportVariantPickerState.Share -> {
                    Button(onClick = onConfirm) { Text("공유") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("취소") }
        },
    )
}
