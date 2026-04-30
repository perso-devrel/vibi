package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dubcast.cmp.platform.rememberAudioPreviewer
import com.dubcast.shared.domain.model.Voice

/**
 * 더빙(TTS) 삽입 sheet — 텍스트 입력 + 보이스 선택 + 합성 + 미리보기 → 타임라인 배치.
 *
 * P1-5: 보이스 검색 + 언어 필터 + 미리듣기 ▶.
 */
@Composable
fun InsertDubbingSheet(
    voices: List<Voice>,
    isVoicesLoading: Boolean,
    isSynthesizing: Boolean,
    previewAvailable: Boolean,
    synthError: String?,
    /** Input 화면에서 선택된 타깃 언어들 — 빈 Set 이면 필터 안 함. */
    targetLanguageCodes: Set<String> = emptySet(),
    onSynthesize: (text: String, voiceId: String, voiceName: String) -> Unit,
    onInsert: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf<Voice?>(null) }
    var query by remember { mutableStateOf("") }

    val previewer = rememberAudioPreviewer()

    val filteredVoices by remember(voices, query, targetLanguageCodes) {
        derivedStateOf {
            voices.asSequence()
                .filter { v ->
                    if (targetLanguageCodes.isEmpty()) true
                    else v.language != null && targetLanguageCodes.any { lang ->
                        v.language!!.contains(lang, ignoreCase = true)
                    }
                }
                .filter { v ->
                    query.isBlank() ||
                        v.name.contains(query, ignoreCase = true) ||
                        (v.language?.contains(query, ignoreCase = true) == true)
                }
                .toList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("더빙 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("더빙 텍스트") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("보이스 선택", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("검색 (이름/언어)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isVoicesLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("보이스 로딩…")
                    }
                } else if (filteredVoices.isEmpty()) {
                    Text("일치하는 보이스가 없습니다.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        items(filteredVoices, key = { it.voiceId }) { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if (selectedVoice?.voiceId == voice.voiceId) "✓" else "  ",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${voice.name}${voice.language?.let { " ($it)" } ?: ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedVoice = voice }
                                )
                                voice.previewUrl?.let { url ->
                                    TextButton(onClick = { previewer.play(url) }) { Text("▶") }
                                }
                            }
                        }
                    }
                }

                synthError?.let {
                    Text("오류: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (previewAvailable) {
                Button(onClick = {
                    onInsert()
                    onDismiss()
                }) { Text("타임라인에 삽입") }
            } else {
                Button(
                    enabled = text.isNotBlank() && selectedVoice != null && !isSynthesizing,
                    onClick = {
                        val voice = selectedVoice ?: return@Button
                        onSynthesize(text, voice.voiceId, voice.name)
                    }
                ) {
                    Text(if (isSynthesizing) "합성 중…" else "TTS 합성")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
