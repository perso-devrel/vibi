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
import androidx.compose.material3.OutlinedButton
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
    initialNumberOfSpeakers: Int,
    onDismiss: () -> Unit,
    onSave: (languageCode: String, autoSubtitles: Boolean, autoDubbing: Boolean, numberOfSpeakers: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    var language by remember {
        mutableStateOf(TargetLanguage.fromCode(initialLanguageCode))
    }
    var autoSubtitles by remember { mutableStateOf(initialAutoSubtitles) }
    var autoDubbing by remember { mutableStateOf(initialAutoDubbing) }
    var speakers by remember { mutableStateOf(initialNumberOfSpeakers.coerceIn(1, 10)) }
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

            // 자막/더빙은 한 묶음 토글. 둘 중 하나만 켜는 일이 없어
            // 사용자 결정 부담을 줄이려고 단일 스위치로 통합.
            OptionRow(
                label = "자동 자막 + 더빙 적용",
                checked = autoSubtitles && autoDubbing,
                enabled = isTranslation,
                onCheckedChange = {
                    autoSubtitles = it
                    autoDubbing = it
                }
            )

            Spacer(Modifier.height(12.dp))
            SpeakerCountStepper(value = speakers, onChange = { speakers = it })

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSave(language.code, autoSubtitles, autoDubbing, speakers) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("저장")
            }
        }
    }
}

@Composable
private fun SpeakerCountStepper(
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "화자 수", fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onChange((value - 1).coerceAtLeast(1)) },
                enabled = value > 1,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) { Text("-") }
            Text(
                text = value.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedButton(
                onClick = { onChange((value + 1).coerceAtMost(10)) },
                enabled = value < 10,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) { Text("+") }
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
    autoDubbing: Boolean,
    numberOfSpeakers: Int = 1
): String {
    val lang = TargetLanguage.fromCode(languageCode)
    if (lang.code == TargetLanguage.CODE_ORIGINAL) return "원본 그대로"
    // UI 는 단일 토글로 둘을 동시에 키지만, DB 는 향후 분리 가능성을
    // 위해 두 컬럼을 유지한다. 표기는 EditProject.isAutoLocalizationEnabled
    // 와 동일한 의도(둘 다 켜짐 = "자막+더빙")로 묶는다.
    val flags = buildList {
        when {
            autoSubtitles && autoDubbing -> add("자막+더빙")
            autoSubtitles -> add("자막")
            autoDubbing -> add("더빙")
        }
        if (numberOfSpeakers > 1) add("화자 ${numberOfSpeakers}명")
    }
    return if (flags.isEmpty()) lang.label else "${lang.label} · ${flags.joinToString(" · ")}"
}
