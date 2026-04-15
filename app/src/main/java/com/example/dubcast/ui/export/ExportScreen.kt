package com.example.dubcast.ui.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShare: (outputPath: String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("영상 내보내기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isExporting && state.outputPath == null) {
                // Step 1: Export mode selection
                ExportModeSelector(
                    selected = state.exportMode,
                    onSelect = { viewModel.onSelectExportMode(it) }
                )

                // Step 2: Translation options (only when WITH_TRANSLATION)
                AnimatedVisibility(visible = state.exportMode == ExportMode.WITH_TRANSLATION) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        TranslationOptionsCard(state = state, viewModel = viewModel)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.startExport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("내보내기 시작")
                }
            }

            // Progress
            if (state.isExporting) {
                Spacer(modifier = Modifier.height(32.dp))

                state.statusMessage?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${state.progressPercent}%")
            }

            // Error
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.startExport() }) {
                    Text("다시 시도")
                }
            }

            // Complete
            state.outputPath?.let { path ->
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "내보내기 완료!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onNavigateToShare(path) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("저장 및 공유")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExportModeSelector(
    selected: ExportMode,
    onSelect: (ExportMode) -> Unit
) {
    Column {
        Text(
            "저장 방식 선택",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ModeCard(
                title = "본 영상만 저장",
                description = "편집한 영상 그대로 저장",
                icon = { Icon(Icons.Default.MovieFilter, contentDescription = null) },
                isSelected = selected == ExportMode.ORIGINAL_ONLY,
                onClick = { onSelect(ExportMode.ORIGINAL_ONLY) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            ModeCard(
                title = "다국어 번역본",
                description = "더빙 · 자막 · 립싱크 적용",
                icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                isSelected = selected == ExportMode.WITH_TRANSLATION,
                onClick = { onSelect(ExportMode.WITH_TRANSLATION) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationOptionsCard(
    state: ExportUiState,
    viewModel: ExportViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("번역 옵션", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Target language selector
            LanguageDropdown(
                label = "번역 언어",
                selected = state.targetLanguage,
                onSelect = { viewModel.onSelectTargetLanguage(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Dubbing toggle
            OptionRow(
                label = "더빙",
                description = "AI 보이스로 더빙 적용",
                checked = state.enableDubbing,
                onCheckedChange = { viewModel.onToggleDubbing(it) }
            )

            // Voice language option
            AnimatedVisibility(visible = state.enableDubbing) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    VoiceLanguageSelector(
                        selected = state.voiceLanguage,
                        onSelect = { viewModel.onSelectVoiceLanguage(it) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Lip Sync toggle
            OptionRow(
                label = "립싱크",
                description = "더빙 음성에 맞춰 입 모양 동기화",
                checked = state.enableLipSync,
                enabled = state.enableDubbing,
                onCheckedChange = { viewModel.onToggleLipSync(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Auto Subtitles toggle
            OptionRow(
                label = "자동 자막",
                description = "번역 언어로 자막 자동 생성",
                checked = state.enableAutoSubtitles,
                onCheckedChange = { viewModel.onToggleAutoSubtitles(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    label: String,
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
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AVAILABLE_LANGUAGES.forEach { lang ->
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
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun VoiceLanguageSelector(
    selected: VoiceLanguage,
    onSelect: (VoiceLanguage) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "동영상에 삽입한 더빙",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            VoiceLanguage.entries.forEach { lang ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == lang,
                        onClick = { onSelect(lang) }
                    )
                    Text(
                        text = lang.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
