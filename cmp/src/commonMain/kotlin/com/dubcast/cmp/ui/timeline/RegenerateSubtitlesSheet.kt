package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * 사용자가 수정한 source 언어 자막을 source 로 다른 언어 자막을 재생성하는 sheet.
 *
 * 입력: 현재 프로젝트에 자막이 존재하는 언어 코드 목록.
 * 출력: source 언어 1개 + target 언어들 (source 와 다름) 선택 후 onConfirm.
 */
@Composable
fun RegenerateSubtitlesSheet(
    availableLanguages: List<String>,
    onConfirm: (sourceLang: String, targetLangs: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by remember { mutableStateOf(availableLanguages.firstOrNull()) }
    var targets by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("다른 언어 자막 재번역") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "수정한 자막의 언어를 source 로 골라주세요. 같은 시점의 다른 언어 자막이 새 source 기반으로 재번역됩니다.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Source 언어", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableLanguages.forEach { lang ->
                        FilterChip(
                            selected = source == lang,
                            onClick = {
                                source = lang
                                // source 변경 시 targets 에서 source 자동 제거.
                                targets = targets - lang
                            },
                            label = { Text(lang.uppercase()) }
                        )
                    }
                }

                val targetCandidates = availableLanguages.filter { it != source }
                Text("재번역할 언어 (다중)", style = MaterialTheme.typography.labelMedium)
                if (targetCandidates.isEmpty()) {
                    Text("재번역할 다른 언어 자막이 없습니다.", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        targetCandidates.forEach { lang ->
                            FilterChip(
                                selected = lang in targets,
                                onClick = {
                                    targets = if (lang in targets) targets - lang else targets + lang
                                },
                                label = { Text(lang.uppercase()) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = source != null && targets.isNotEmpty(),
                onClick = { onConfirm(source!!, targets.toList()) }
            ) { Text("재번역 시작") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
