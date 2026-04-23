package com.example.dubcast.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dubcast.domain.model.TargetLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExportOptionsSheet(
    initialLanguageCode: String,
    initialAutoSubtitles: Boolean,
    initialAutoDubbing: Boolean,
    onDismiss: () -> Unit,
    onSave: (languageCode: String, autoSubtitles: Boolean, autoDubbing: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    var language by remember {
        mutableStateOf(TargetLanguage.fromCode(initialLanguageCode))
    }
    var autoSubtitles by remember { mutableStateOf(initialAutoSubtitles) }
    var autoDubbing by remember { mutableStateOf(initialAutoDubbing) }
    val isTranslation = language.code != TargetLanguage.CODE_ORIGINAL

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "내보내기 옵션",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))

            TargetLanguageDropdown(
                selected = language,
                onSelect = { selected ->
                    language = selected
                    if (selected.code == TargetLanguage.CODE_ORIGINAL) {
                        autoSubtitles = false
                        autoDubbing = false
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            OptionRow(
                label = "자동 자막",
                checked = autoSubtitles,
                enabled = isTranslation,
                onCheckedChange = { autoSubtitles = it }
            )
            Spacer(Modifier.height(4.dp))
            OptionRow(
                label = "자동 더빙",
                checked = autoDubbing,
                enabled = isTranslation,
                onCheckedChange = { autoDubbing = it }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSave(language.code, autoSubtitles, autoDubbing) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("저장")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetLanguageDropdown(
    selected: TargetLanguage,
    onSelect: (TargetLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("대상 언어") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TargetLanguage.AVAILABLE.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.label) },
                    onClick = {
                        onSelect(lang)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

fun summarizeExportOptions(
    languageCode: String,
    autoSubtitles: Boolean,
    autoDubbing: Boolean
): String {
    val lang = TargetLanguage.fromCode(languageCode)
    if (lang.code == TargetLanguage.CODE_ORIGINAL) return "원본 그대로"
    val flags = buildList {
        if (autoSubtitles) add("자막")
        if (autoDubbing) add("더빙")
    }
    return if (flags.isEmpty()) lang.label else "${lang.label} · ${flags.joinToString(" · ")}"
}
