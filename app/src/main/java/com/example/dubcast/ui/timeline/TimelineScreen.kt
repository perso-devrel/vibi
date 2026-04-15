package com.example.dubcast.ui.timeline

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.dubcast.ui.timeline.components.Timeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: (projectId: String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(state.videoUri) {
        if (state.videoUri.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(Uri.parse(state.videoUri))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(state.isPlaying) {
        exoPlayer.playWhenReady = state.isPlaying
    }

    // Sync ExoPlayer position back to ViewModel while playing
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                viewModel.onUpdatePlaybackPosition(exoPlayer.currentPosition)
                kotlinx.coroutines.delay(50L)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToExport.collect { projectId ->
            onNavigateToExport(projectId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // Video Preview
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

        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.onTogglePlayback() }) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play"
                )
            }

            Text(
                text = formatTime(state.playbackPositionMs),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(60.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { viewModel.onShowDubbingSheet() }) {
                Icon(Icons.Default.MicExternalOn, contentDescription = "Insert Dubbing")
            }

            if (state.selectedDubClipId != null) {
                IconButton(onClick = { viewModel.onDeleteSelectedClip() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { viewModel.onUndo() },
                enabled = state.canUndo
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }

            IconButton(
                onClick = { viewModel.onRedo() },
                enabled = state.canRedo
            ) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }

            IconButton(onClick = { viewModel.onNavigateToExport() }) {
                Icon(Icons.Default.Save, contentDescription = "Export")
            }
        }

        // Timeline
        Timeline(
            videoDurationMs = state.videoDurationMs,
            dubClips = state.dubClips,
            subtitleClips = state.subtitleClips,
            playbackPositionMs = state.playbackPositionMs,
            selectedDubClipId = state.selectedDubClipId,
            selectedSubtitleClipId = state.selectedSubtitleClipId,
            onDubClipSelected = { viewModel.onSelectDubClip(it) },
            onSubtitleClipSelected = { viewModel.onSelectSubtitleClip(it) },
            onDubClipMoved = { clipId, newStartMs -> viewModel.onMoveDubClip(clipId, newStartMs) },
            onSeek = { posMs ->
                    viewModel.onUpdatePlaybackPosition(posMs)
                    exoPlayer.seekTo(posMs)
                },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
    }

    // Bottom Sheets
    if (state.showDubbingSheet) {
        InsertDubbingSheet(
            voices = state.voices,
            isLoading = state.isSynthesizing,
            error = state.synthError,
            onDismiss = { viewModel.onDismissDubbingSheet() },
            onSynthesize = { text, voiceId, voiceName ->
                viewModel.onSynthesize(text, voiceId, voiceName)
            }
        )
    }

}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
