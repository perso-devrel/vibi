package com.example.dubcast.ui.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.Voice

private val LANGUAGES = listOf(
    "ko" to "한국어",
    "en" to "English",
    "ja" to "日本語",
    "zh" to "中文",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertDubbingSheet(
    voices: List<Voice>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSynthesize: (text: String, voiceId: String, voiceName: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf(voices.firstOrNull()) }
    var voiceExpanded by remember { mutableStateOf(false) }

    var selectedLanguage by remember { mutableStateOf(LANGUAGES[0]) }
    var langExpanded by remember { mutableStateOf(false) }

    // Filter voices by selected language
    val filteredVoices = remember(voices, selectedLanguage) {
        val filtered = voices.filter { it.language == selectedLanguage.first }
        if (filtered.isEmpty()) voices else filtered
    }

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
                text = "더빙 삽입",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language selector
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLanguage.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("언어") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.second) },
                            onClick = {
                                selectedLanguage = lang
                                langExpanded = false
                                // Reset voice selection when language changes
                                val newFiltered = voices.filter { it.language == lang.first }
                                selectedVoice = newFiltered.firstOrNull() ?: voices.firstOrNull()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Voice selector
            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedVoice?.name ?: "보이스 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("보이스") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false }
                ) {
                    filteredVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Text("${voice.name}${voice.language?.let { " ($it)" } ?: ""}")
                            },
                            onClick = {
                                selectedVoice = voice
                                voiceExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("더빙할 텍스트") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(12.dp))

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    selectedVoice?.let { voice ->
                        onSynthesize(text, voice.voiceId, voice.name)
                    }
                },
                enabled = text.isNotBlank() && selectedVoice != null && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("생성")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
