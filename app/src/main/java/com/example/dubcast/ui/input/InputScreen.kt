package com.example.dubcast.ui.input

import android.net.Uri
import com.example.dubcast.domain.model.TargetLanguage
import com.example.dubcast.domain.model.ValidationResult
import com.example.dubcast.ui.theme.CharcoalPrimary
import com.example.dubcast.ui.theme.TextSecondary
import com.example.dubcast.ui.theme.BorderLine
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dubcast.domain.model.ValidationError

@Composable
private fun SolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bgColor = if (enabled) CharcoalPrimary else CharcoalPrimary.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderLine, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = CharcoalPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetLanguageDropdown(
    selected: TargetLanguage,
    onSelect: (TargetLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
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
private fun InputOptionRow(
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
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (enabled) CharcoalPrimary else TextSecondary
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun InputOptionsCard(
    state: InputUiState,
    viewModel: InputViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderLine, RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        TargetLanguageDropdown(
            selected = state.targetLanguage,
            onSelect = { viewModel.onSelectTargetLanguage(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        InputOptionRow(
            label = "자동 자막",
            checked = state.enableAutoSubtitles,
            enabled = state.isTranslationLanguage,
            onCheckedChange = { viewModel.onToggleAutoSubtitles(it) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        InputOptionRow(
            label = "자동 더빙",
            checked = state.enableAutoDubbing,
            enabled = state.isTranslationLanguage,
            onCheckedChange = { viewModel.onToggleAutoDubbing(it) }
        )
    }
}

@Composable
fun InputScreen(
    onNavigateToTimeline: (projectId: String) -> Unit,
    viewModel: InputViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigateToTimeline.collect { projectId ->
            onNavigateToTimeline(projectId)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onVideoPicked(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F5))
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.35f))

        Text(
            text = "DubCast",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            color = CharcoalPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "AI dubbing, dead simple.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(0.3f))

        state.selectedVideo?.let { video ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderLine, RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text(
                    text = video.fileName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = CharcoalPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${video.durationMs / 1000}s  ·  ${video.width}x${video.height}  ·  ${video.sizeBytes / 1024 / 1024}MB",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.validationResult == ValidationResult.Valid) {
                InputOptionsCard(state = state, viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        state.validationResult?.let { result ->
            when (result) {
                is ValidationResult.Invalid -> {
                    Text(
                        text = when (result.reason) {
                            ValidationError.UNSUPPORTED_FORMAT -> "Use MP4, MOV, or WebM."
                            ValidationError.DURATION_EXCEEDS_LIMIT -> "10 min max."
                            ValidationError.RESOLUTION_EXCEEDS_LIMIT -> "1080p max."
                            ValidationError.METADATA_UNREADABLE -> "Can't read this file."
                        },
                        color = Color(0xFFD32F2F),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ValidationResult.Valid -> {}
            }
        }

        SolidButton(
            text = if (state.selectedVideo != null) "Let's Go" else "Pick a Video",
            onClick = {
                if (state.selectedVideo != null && state.validationResult == ValidationResult.Valid) {
                    viewModel.onContinue()
                } else {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            },
            enabled = state.selectedVideo == null || state.validationResult == ValidationResult.Valid
        )

        if (state.selectedVideo != null) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlineButton(
                text = "Change Video",
                onClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            )
        }

        Spacer(modifier = Modifier.weight(0.35f))

        HorizontalDivider(color = BorderLine)
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Text("from ", fontSize = 12.sp, color = TextSecondary)
            Text("DubCast", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
