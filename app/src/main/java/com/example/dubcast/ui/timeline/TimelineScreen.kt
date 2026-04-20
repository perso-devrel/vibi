package com.example.dubcast.ui.timeline

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeUp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.dubcast.ui.timeline.components.ImageOverlayLayer
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // PickVisualMedia may not grant persistable permissions; safe to ignore
            }
            viewModel.onInsertImage(uri.toString())
        }
    }

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

    // Track active MediaPlayers for dub clip audio overlay
    val activePlayers = remember { mutableStateMapOf<String, MediaPlayer>() }

    // Sync ExoPlayer position back to ViewModel + play dub clip audio
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                val posMs = exoPlayer.currentPosition
                // Stop at trim end
                val trimEnd = state.effectiveTrimEndMs
                if (posMs >= trimEnd) {
                    exoPlayer.seekTo(state.trimStartMs)
                    viewModel.onUpdatePlaybackPosition(state.trimStartMs)
                    kotlinx.coroutines.delay(50L)
                    continue
                }
                viewModel.onUpdatePlaybackPosition(posMs)

                // Start dub clips that should be playing at current position
                for (clip in state.dubClips) {
                    val clipEnd = clip.startMs + clip.durationMs
                    if (posMs in clip.startMs until clipEnd && clip.id !in activePlayers) {
                        try {
                            val mp = MediaPlayer().apply {
                                setDataSource(clip.audioFilePath)
                                setVolume(clip.volume, clip.volume)
                                prepare()
                                val offsetMs = (posMs - clip.startMs).toInt()
                                if (offsetMs > 0) seekTo(offsetMs)
                                start()
                                setOnCompletionListener {
                                    it.release()
                                    activePlayers.remove(clip.id)
                                }
                            }
                            activePlayers[clip.id] = mp
                        } catch (_: Exception) { /* file not found etc. */ }
                    }
                }

                // Stop clips that are no longer in range
                val toRemove = activePlayers.keys.filter { id ->
                    val clip = state.dubClips.find { it.id == id }
                    clip == null || posMs < clip.startMs || posMs >= clip.startMs + clip.durationMs
                }
                for (id in toRemove) {
                    activePlayers.remove(id)?.apply { try { stop() } catch (_: IllegalStateException) {}; release() }
                }

                kotlinx.coroutines.delay(50L)
            }
        } else {
            // Playback stopped — release all active players
            activePlayers.values.forEach { try { it.stop() } catch (_: IllegalStateException) {}; it.release() }
            activePlayers.clear()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToExport.collect { projectId ->
            onNavigateToExport(projectId)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activePlayers.values.forEach { try { it.stop() } catch (_: IllegalStateException) {}; it.release() }
            activePlayers.clear()
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

    // Drag handle state — declared outside Column so it's not reset
    val density = LocalDensity.current
    var timelineHeightDp by remember { mutableStateOf(120f) }

    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // Video Preview + subtitle overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.matchParentSize()
            )
            SubtitlePreviewLayer(
                subtitleClips = state.subtitleClips,
                playbackPositionMs = state.playbackPositionMs,
                selectedSubtitleClipId = state.selectedSubtitleClipId,
                onSelectSubtitle = { viewModel.onSelectSubtitleClip(it) },
                onUpdatePosition = { id, x, y, w, h ->
                    viewModel.onUpdateSubtitlePosition(id, x, y, w, h)
                },
                modifier = Modifier.matchParentSize()
            )
            ImageOverlayLayer(
                imageClips = state.imageClips,
                playbackPositionMs = state.playbackPositionMs,
                selectedImageClipId = state.selectedImageClipId,
                onSelect = { viewModel.onSelectImageClip(it) },
                onUpdate = { id, x, y, w, h ->
                    viewModel.onUpdateImageClipPosition(id, x, y, w, h)
                },
                modifier = Modifier.matchParentSize()
            )
        }

        // Toolbar — switches between normal and trim mode
        if (state.isTrimming) {
            // Trim mode toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onCancelTrim() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${formatTime(state.pendingTrimStartMs)} — ${formatTime(state.pendingTrimEndMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Trim",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { viewModel.onConfirmTrim() }) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Apply Trim",
                        tint = Color(0xFF34C759)
                    )
                }
            }
        } else {
            // Normal toolbar
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

                IconButton(onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Default.Image, contentDescription = "Insert Image")
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
        }

        // Dub clip selected action bar
        val selectedClip = state.dubClips.find { it.id == state.selectedDubClipId }
        if (selectedClip != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onToggleDubVolumeSlider() }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume")
                }
                IconButton(onClick = { viewModel.onDeleteSelectedClip() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            if (state.showDubVolumeSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume", modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = selectedClip.volume,
                        onValueChange = { viewModel.onUpdateDubClipVolume(selectedClip.id, it) },
                        valueRange = 0f..2f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(selectedClip.volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }

        // Image clip selected action bar
        val selectedImageClip = state.imageClips.find { it.id == state.selectedImageClipId }
        if (selectedImageClip != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onDeleteSelectedClip() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Video selected action bar — small icon buttons above timeline
        if (state.isVideoSelected && !state.isTrimming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEnterTrimMode() }) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Trim")
                }
                IconButton(onClick = { viewModel.onToggleVideoVolumeSlider() }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume")
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            if (state.showVideoVolumeSlider) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Volume", modifier = Modifier.padding(end = 8.dp))
                    Slider(
                        value = state.videoVolume,
                        onValueChange = {
                            viewModel.onUpdateVideoVolume(it)
                            exoPlayer.volume = it.coerceIn(0f, 1f)
                        },
                        valueRange = 0f..2f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(state.videoVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }

        // Drag handle to resize timeline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val deltaDp = with(density) { dragAmount.toDp().value }
                        timelineHeightDp = (timelineHeightDp - deltaDp).coerceIn(60f, 300f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }

        // Timeline
        // In trim mode, show pending values; otherwise show saved values
        val displayTrimStart = if (state.isTrimming) state.pendingTrimStartMs else state.trimStartMs
        val displayTrimEnd = if (state.isTrimming) state.pendingTrimEndMs else state.effectiveTrimEndMs

        Timeline(
            videoDurationMs = state.videoDurationMs,
            dubClips = state.dubClips,
            subtitleClips = state.subtitleClips,
            imageClips = state.imageClips,
            playbackPositionMs = state.playbackPositionMs,
            trimStartMs = displayTrimStart,
            trimEndMs = displayTrimEnd,
            isTrimming = state.isTrimming,
            isVideoSelected = state.isVideoSelected,
            selectedDubClipId = state.selectedDubClipId,
            selectedSubtitleClipId = state.selectedSubtitleClipId,
            selectedImageClipId = state.selectedImageClipId,
            onVideoTrackTapped = { viewModel.onVideoTrackTapped() },
            onDubClipSelected = { viewModel.onSelectDubClip(it) },
            onSubtitleClipSelected = { viewModel.onSelectSubtitleClip(it) },
            onImageClipSelected = { viewModel.onSelectImageClip(it) },
            onDubClipMoved = { clipId, newStartMs -> viewModel.onMoveDubClip(clipId, newStartMs) },
            onImageClipMoved = { clipId, newStartMs -> viewModel.onMoveImageClip(clipId, newStartMs) },
            onImageClipResized = { clipId, newEndMs -> viewModel.onResizeImageClipDuration(clipId, newEndMs) },
            onSeek = { posMs ->
                    val clampedMs = posMs.coerceIn(state.trimStartMs, state.effectiveTrimEndMs)
                    viewModel.onUpdatePlaybackPosition(clampedMs)
                    exoPlayer.seekTo(clampedMs)
                },
            onTrimStartChanged = { viewModel.onSetPendingTrimStart(it) },
            onTrimEndChanged = { viewModel.onSetPendingTrimEnd(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(timelineHeightDp.dp)
        )
    }
    }

    // Bottom Sheets
    if (state.showDubbingSheet) {
        InsertDubbingSheet(
            voices = state.voices,
            isLoading = state.isSynthesizing,
            error = state.synthError,
            previewClip = state.previewClip,
            onDismiss = { viewModel.onDismissDubbingSheet() },
            onSynthesize = { text, voiceId, voiceName ->
                viewModel.onSynthesize(text, voiceId, voiceName)
            },
            onInsert = { showOnScreen -> viewModel.onInsertPreviewClip(showOnScreen) }
        )
    }

}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
