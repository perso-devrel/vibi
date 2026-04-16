package com.example.dubcast.ui.timeline

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.Voice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertDubbingSheet(
    voices: List<Voice>,
    isLoading: Boolean,
    error: String?,
    previewClip: PreviewDubClip?,
    onDismiss: () -> Unit,
    onSynthesize: (text: String, voiceId: String, voiceName: String) -> Unit,
    onInsert: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf(voices.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    // MediaPlayer for preview playback
    var isPreviewPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer.stop() } catch (_: IllegalStateException) {}
            mediaPlayer.release()
        }
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
                text = "Add Dub",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Voice selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedVoice?.name ?: "Pick a voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Text("${voice.name}${voice.language?.let { " ($it)" } ?: ""}")
                            },
                            onClick = {
                                selectedVoice = voice
                                expanded = false
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
                label = { Text("What to say") },
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

            // Generate button
            Button(
                onClick = {
                    selectedVoice?.let { voice ->
                        isPreviewPlaying = false
                        mediaPlayer.reset()
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
                    Text("Generate")
                }
            }

            // Preview + Insert (shown after TTS generation)
            if (previewClip != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Preview playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isPreviewPlaying) {
                            mediaPlayer.stop()
                            mediaPlayer.reset()
                            isPreviewPlaying = false
                        } else {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(previewClip.audioFilePath)
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                            isPreviewPlaying = true
                            mediaPlayer.setOnCompletionListener {
                                isPreviewPlaying = false
                            }
                        }
                    }) {
                        Icon(
                            if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewPlaying) "Stop" else "Play"
                        )
                    }

                    Text(
                        text = "Listen (${previewClip.durationMs / 1000}.${(previewClip.durationMs % 1000) / 100}s)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Insert button
                Button(
                    onClick = {
                        mediaPlayer.reset()
                        isPreviewPlaying = false
                        onInsert()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Drop it in")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
