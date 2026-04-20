package com.example.dubcast.ui.export

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.dubcast.domain.model.DubClip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShare: (outputPath: String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Preview player
    var isPreviewing by remember { mutableStateOf(false) }
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    val activePlayers = remember { mutableStateMapOf<String, MediaPlayer>() }

    LaunchedEffect(state.videoUri) {
        if (state.videoUri.isNotEmpty()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(state.videoUri)))
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(isPreviewing) {
        exoPlayer.playWhenReady = isPreviewing
        if (isPreviewing) {
            while (true) {
                val posMs = exoPlayer.currentPosition
                for (clip in state.dubClips) {
                    val clipEnd = clip.startMs + clip.durationMs
                    if (posMs in clip.startMs until clipEnd && clip.id !in activePlayers) {
                        try {
                            val mp = MediaPlayer().apply {
                                setDataSource(clip.audioFilePath)
                                setVolume(clip.volume, clip.volume)
                                prepare()
                                val offset = (posMs - clip.startMs).toInt()
                                if (offset > 0) seekTo(offset)
                                start()
                                setOnCompletionListener {
                                    it.release()
                                    activePlayers.remove(clip.id)
                                }
                            }
                            activePlayers[clip.id] = mp
                        } catch (_: Exception) {}
                    }
                }
                val toRemove = activePlayers.keys.filter { id ->
                    val clip = state.dubClips.find { it.id == id }
                    clip == null || posMs < clip.startMs || posMs >= clip.startMs + clip.durationMs
                }
                for (id in toRemove) {
                    activePlayers.remove(id)?.apply {
                        try { stop() } catch (_: IllegalStateException) {}
                        release()
                    }
                }
                kotlinx.coroutines.delay(50L)
            }
        } else {
            activePlayers.values.forEach {
                try { it.stop() } catch (_: IllegalStateException) {}
                it.release()
            }
            activePlayers.clear()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activePlayers.values.forEach {
                try { it.stop() } catch (_: IllegalStateException) {}
                it.release()
            }
            activePlayers.clear()
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
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

                // Preview player
                if (state.videoUri.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    if (!isPreviewing) {
                                        exoPlayer.seekTo(0)
                                    }
                                    isPreviewing = !isPreviewing
                                }) {
                                    Icon(
                                        if (isPreviewing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPreviewing) "Pause" else "Play"
                                    )
                                }
                                Text(
                                    text = if (isPreviewing) "Playing..." else "Preview",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (state.dubClips.isNotEmpty()) {
                                    Text(
                                        text = " (${state.dubClips.size} dubs)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Export mode selection
                ExportModeSelector(
                    selected = state.exportMode,
                    onSelect = { viewModel.onSelectExportMode(it) }
                )

                AnimatedVisibility(visible = state.exportMode == ExportMode.WITH_TRANSLATION) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        TranslationOptionsCard(state = state, viewModel = viewModel)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isPreviewing = false
                        viewModel.startExport()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Go!")
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
                    Text("Retry")
                }
            }

            // Complete
            state.outputPath?.let { path ->
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Done!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onNavigateToShare(path) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Share")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (state.showImageBffNotReadyDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissImageBffDialog() },
            title = { Text("Image overlay not fully supported") },
            text = {
                Text(
                    "The render server does not yet process image overlays. " +
                        "Your ${state.imageClipCount} image clip(s) may be skipped in the exported video. " +
                        "Continue anyway?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmExportWithImages() }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissImageBffDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExportModeSelector(
    selected: ExportMode,
    onSelect: (ExportMode) -> Unit
) {
    Column {
        Text(
            "Pick a mode",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ModeCard(
                title = "Original",
                description = "Keep it as-is",
                icon = { Icon(Icons.Default.MovieFilter, contentDescription = null) },
                isSelected = selected == ExportMode.ORIGINAL_ONLY,
                onClick = { onSelect(ExportMode.ORIGINAL_ONLY) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            ModeCard(
                title = "Translated",
                description = "Dub + subs + lip-sync",
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
            Text("Options", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(16.dp))

            LanguageDropdown(
                label = "Language",
                selected = state.targetLanguage,
                onSelect = { viewModel.onSelectTargetLanguage(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            OptionRow(
                label = "Dubbing",
                description = "AI voice overlay",
                checked = state.enableDubbing,
                onCheckedChange = { viewModel.onToggleDubbing(it) }
            )

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

            OptionRow(
                label = "Lip Sync",
                description = "Match mouth to voice",
                checked = state.enableLipSync,
                enabled = state.enableDubbing,
                onCheckedChange = { viewModel.onToggleLipSync(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            OptionRow(
                label = "Auto Subs",
                description = "Generate translated captions",
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
                "Dub voice",
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
