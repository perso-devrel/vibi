package com.example.dubcast.ui.timeline

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
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
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onInsertImage(uri.toString())
        }
    }

    var appendMode by remember { mutableStateOf<AppendPickerTarget?>(null) }

    val videoAppendLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onAppendVideoSegment(uri.toString())
        }
        appendMode = null
    }

    val photoAppendLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onAppendImageSegment(uri.toString())
        }
        appendMode = null
    }

    val bgmPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onPickBgmAudio(uri.toString())
        }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    val currentSegment = currentSegmentAt(state.segments, state.playbackPositionMs)
    val currentSegmentId = currentSegment?.id
    val currentSegmentStart = remember(state.segments, currentSegmentId) {
        segmentStartOffset(state.segments, currentSegmentId)
    }

    // Track the currently loaded source URI in ExoPlayer separately from the
    // segment id. When the next segment plays the same underlying file (which
    // is the common case after split/duplicate operations), seek instead of
    // reloading — that eliminates the brief stutter at segment boundaries.
    val loadedSourceUri = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        currentSegmentId,
        currentSegment?.sourceUri,
        currentSegment?.volumeScale,
        currentSegment?.speedScale,
        state.isPlaying
    ) {
        val seg = currentSegment
        if (seg == null) {
            exoPlayer.clearMediaItems()
            exoPlayer.playWhenReady = false
            loadedSourceUri.value = null
            return@LaunchedEffect
        }
        if (seg.type == SegmentType.VIDEO) {
            val localMs = (state.playbackPositionMs - currentSegmentStart + seg.trimStartMs)
                .coerceAtLeast(0L)
            if (loadedSourceUri.value == seg.sourceUri) {
                exoPlayer.seekTo(localMs)
            } else {
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(seg.sourceUri)), localMs)
                exoPlayer.prepare()
                loadedSourceUri.value = seg.sourceUri
            }
            // Apply per-segment volume + speed to the preview. ExoPlayer's
            // volume is 0f..1f (clip values above 1 — the BFF render still
            // honours them); playbackParameters carries the speed scaling.
            exoPlayer.volume = seg.volumeScale.coerceIn(0f, 1f)
            exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(
                seg.speedScale.coerceIn(0.25f, 4f)
            )
            exoPlayer.playWhenReady = state.isPlaying
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    val activePlayers = remember { mutableStateMapOf<String, MediaPlayer>() }

    // Global playback loop. Emits position updates, drives dub audio, and
    // advances through image segments via a manual timer.
    LaunchedEffect(state.isPlaying, state.segments) {
        if (!state.isPlaying) {
            activePlayers.values.forEach {
                try { it.stop() } catch (_: IllegalStateException) {}
                it.release()
            }
            activePlayers.clear()
            return@LaunchedEffect
        }
        if (state.segments.isEmpty()) return@LaunchedEffect

        val total = state.videoDurationMs
        while (true) {
            val existingPos = viewModel.uiState.value.playbackPositionMs
            val seg = currentSegmentAt(state.segments, existingPos)
            val segStart = segmentStartOffset(state.segments, seg?.id)
            val nextPos = when (seg?.type) {
                SegmentType.VIDEO -> {
                    val localPos = exoPlayer.currentPosition
                    val trimStart = seg.trimStartMs
                    val trimEnd = if (seg.trimEndMs <= 0L) seg.durationMs else seg.trimEndMs
                    val globalPos = segStart + (localPos - trimStart).coerceAtLeast(0L)
                    when {
                        localPos >= trimEnd -> segStart + (trimEnd - trimStart)
                        else -> globalPos
                    }
                }
                SegmentType.IMAGE -> (existingPos + 50L)
                else -> existingPos
            }

            val looped = if (nextPos >= total && total > 0L) 0L else nextPos
            viewModel.onUpdatePlaybackPosition(looped)

            val dubs = viewModel.uiState.value.dubClips
            for (clip in dubs) {
                val clipEnd = clip.startMs + clip.durationMs
                if (looped in clip.startMs until clipEnd && clip.id !in activePlayers) {
                    try {
                        val mp = MediaPlayer().apply {
                            setDataSource(clip.audioFilePath)
                            setVolume(clip.volume, clip.volume)
                            prepare()
                            val offsetMs = (looped - clip.startMs).toInt()
                            if (offsetMs > 0) seekTo(offsetMs)
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
                val clip = dubs.find { it.id == id }
                clip == null || looped < clip.startMs || looped >= clip.startMs + clip.durationMs
            }
            for (id in toRemove) {
                activePlayers.remove(id)?.apply {
                    try { stop() } catch (_: IllegalStateException) {}
                    release()
                }
            }

            kotlinx.coroutines.delay(50L)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToExport.collect { projectId ->
            onNavigateToExport(projectId)
        }
    }

    LaunchedEffect(appendMode) {
        when (appendMode) {
            AppendPickerTarget.VIDEO -> {
                videoAppendLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
            AppendPickerTarget.PHOTO -> {
                photoAppendLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            null -> {}
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
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->

        val density = LocalDensity.current
        // Compact default — only the video track is shown until the user
        // adds dubs/photos/text/bgm. Each track row appears below the video
        // when it actually has content. Resize handle still allows expanding.
        var timelineHeightDp by remember { mutableStateOf(110f) }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val frameBackgroundColor = remember(state.backgroundColorHex) {
                runCatching { Color(android.graphics.Color.parseColor(state.backgroundColorHex)) }
                    .getOrDefault(Color.Black)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Thin border so the user can see exactly where the export
                // frame ends (esp. against the matching black backdrop).
                val frameBorderColor = Color.White.copy(alpha = 0.5f)
                val frameModifier = if (state.frameAspectRatio > 0f) {
                    Modifier.fillMaxWidth()
                        .aspectRatio(state.frameAspectRatio)
                        .background(frameBackgroundColor)
                        .border(1.dp, frameBorderColor)
                } else {
                    Modifier.matchParentSize()
                        .border(1.dp, frameBorderColor)
                }
                Box(modifier = frameModifier) {
                    if (currentSegment?.type == SegmentType.IMAGE) {
                    AsyncImage(
                        model = currentSegment.sourceUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black)
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.matchParentSize()
                    )
                }
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
                if (currentSegment?.type != SegmentType.IMAGE) {
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
                TextOverlayPreviewLayer(
                    textOverlays = state.textOverlays,
                    playbackPositionMs = state.playbackPositionMs,
                    selectedOverlayId = state.selectedTextOverlayId,
                    onSelect = { viewModel.onSelectTextOverlay(it) },
                    onPositionChanged = { id, x, y ->
                        viewModel.onUpdateTextOverlayScreenPosition(id, x, y)
                    },
                    onFontSizeChanged = { id, sp ->
                        viewModel.onUpdateTextOverlayFontSize(id, sp)
                    },
                    modifier = Modifier.matchParentSize()
                )
                }
            }

            // Action bars + timeline live in their own surfaceVariant-tinted
            // column so they read as separate from the (black) video preview
            // area instead of bleeding into it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
            if (state.isTrimming) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    InsertMenu(
                        bgmEnabled = !state.isAddingBgm,
                        onInsertText = { viewModel.onShowTextOverlaySheetForNew() },
                        onInsertImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onInsertBgm = { bgmPickerLauncher.launch(arrayOf("audio/*")) },
                        onShowFrameSheet = { viewModel.onShowFrameSheet() }
                    )
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

            val selectedDubClip = state.dubClips.find { it.id == state.selectedDubClipId }
            if (selectedDubClip != null) {
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
                            value = selectedDubClip.volume,
                            onValueChange = { viewModel.onUpdateDubClipVolume(selectedDubClip.id, it) },
                            valueRange = 0f..2f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(selectedDubClip.volume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(44.dp)
                        )
                    }
                }
            }

            val selectedImageClip = state.imageClips.find { it.id == state.selectedImageClipId }
            if (selectedImageClip != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.onDuplicateImageClip(selectedImageClip.id) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Image")
                    }
                    IconButton(onClick = { viewModel.onDeleteSelectedClip() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            val selectedBgmClip = state.bgmClips.find { it.id == state.selectedBgmClipId }
            if (selectedBgmClip != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.onDeleteBgmClip(selectedBgmClip.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete BGM")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "BGM · start ${formatTime(selectedBgmClip.startMs)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", modifier = Modifier.width(60.dp))
                        Slider(
                            value = selectedBgmClip.volumeScale,
                            onValueChange = { viewModel.onUpdateBgmVolume(selectedBgmClip.id, it) },
                            valueRange = 0f..2f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(selectedBgmClip.volumeScale * 100).toInt()}%",
                            modifier = Modifier.width(48.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            val selectedTextOverlay = state.textOverlays.find { it.id == state.selectedTextOverlayId }
            if (selectedTextOverlay != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        viewModel.onShowTextOverlaySheetForEdit(selectedTextOverlay.id)
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Text")
                    }
                    IconButton(onClick = {
                        viewModel.onDuplicateTextOverlay(selectedTextOverlay.id)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Text")
                    }
                    IconButton(onClick = {
                        viewModel.onDeleteTextOverlay(selectedTextOverlay.id)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Text")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Text · ${selectedTextOverlay.text.take(16)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            val selectedSegment = state.segments.find { it.id == state.selectedSegmentId }
            // Auto-enter range mode for VIDEO selection, auto-exit when nothing
            // is selected or a non-VIDEO segment is selected, so the green
            // handles and edit panel only appear while editing a video clip.
            LaunchedEffect(selectedSegment?.id, selectedSegment?.type) {
                if (selectedSegment?.type == SegmentType.VIDEO) {
                    if (state.rangeTargetSegmentId != selectedSegment.id) {
                        viewModel.onEnterRangeMode(selectedSegment.id)
                    }
                } else if (state.isRangeSelecting) {
                    viewModel.onCancelRangeMode()
                }
            }
            if (selectedSegment != null && !state.isTrimming) {
                SelectedSegmentActionBar(
                    segment = selectedSegment,
                    canDelete = state.segments.size > 1,
                    onUpdateDuration = { ms ->
                        viewModel.onUpdateImageSegmentDuration(selectedSegment.id, ms)
                    },
                    onDelete = { viewModel.onDeleteSelectedSegment() },
                    rangeStartMs = state.pendingRangeStartMs,
                    rangeEndMs = state.pendingRangeEndMs,
                    rangeVolume = state.pendingRangeVolume,
                    rangeSpeed = state.pendingRangeSpeed,
                    onRangeChange = { s, e ->
                        viewModel.onSetPendingRangeEnd(e)
                        viewModel.onSetPendingRangeStart(s)
                    },
                    onDuplicateRange = { viewModel.onDuplicateRange() },
                    onDeleteRange = { viewModel.onDeleteRange() },
                    onRangeVolumeChange = { viewModel.onUpdatePendingRangeVolume(it) },
                    onRangeSpeedChange = { viewModel.onUpdatePendingRangeSpeed(it) },
                    onApplyRangeVolume = { viewModel.onApplyRangeVolume(it) },
                    onApplyRangeSpeed = { viewModel.onApplyRangeSpeed(it) },
                    onRequestAudioSeparation = {
                        viewModel.onShowAudioSeparationSheet(selectedSegment.id)
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            val deltaDp = with(density) { dragAmount.toDp().value }
                            timelineHeightDp = (timelineHeightDp - deltaDp).coerceIn(60f, 400f)
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

            val displayTrimStart = if (state.isTrimming) state.pendingTrimStartMs else state.trimStartMs
            val displayTrimEnd = if (state.isTrimming) state.pendingTrimEndMs else {
                if (state.trimEndMs <= 0L) state.videoDurationMs else state.trimEndMs
            }

            Timeline(
                totalDurationMs = state.videoDurationMs,
                segments = state.segments,
                dubClips = state.dubClips,
                subtitleClips = state.subtitleClips,
                imageClips = state.imageClips,
                playbackPositionMs = state.playbackPositionMs,
                trimStartMs = displayTrimStart,
                trimEndMs = displayTrimEnd,
                isTrimming = state.isTrimming,
                selectedSegmentId = state.selectedSegmentId,
                selectedDubClipId = state.selectedDubClipId,
                selectedSubtitleClipId = state.selectedSubtitleClipId,
                selectedImageClipId = state.selectedImageClipId,
                onSegmentSelected = { viewModel.onSelectSegment(it) },
                onDubClipSelected = { viewModel.onSelectDubClip(it) },
                onSubtitleClipSelected = { viewModel.onSelectSubtitleClip(it) },
                onImageClipSelected = { viewModel.onSelectImageClip(it) },
                onDubClipMoved = { clipId, newStartMs -> viewModel.onMoveDubClip(clipId, newStartMs) },
                onImageClipMoved = { clipId, newStartMs -> viewModel.onMoveImageClip(clipId, newStartMs) },
                onImageClipResized = { clipId, newEndMs -> viewModel.onResizeImageClipDuration(clipId, newEndMs) },
                onImageClipLaneChanged = { clipId, delta -> viewModel.onChangeImageClipLane(clipId, delta) },
                onImageSegmentResized = { segmentId, newDurationMs ->
                    viewModel.onResizeImageSegmentByDrag(segmentId, newDurationMs)
                },
                textOverlays = state.textOverlays,
                selectedTextOverlayId = state.selectedTextOverlayId,
                onTextOverlaySelected = { viewModel.onSelectTextOverlay(it) },
                onTextOverlayLongPressed = { viewModel.onDuplicateTextOverlay(it) },
                onTextOverlayResized = { id, newEndMs -> viewModel.onResizeTextOverlay(id, newEndMs) },
                onTextOverlayMoved = { id, newStartMs -> viewModel.onMoveTextOverlay(id, newStartMs) },
                onTextOverlayLaneChanged = { id, delta -> viewModel.onChangeTextOverlayLane(id, delta) },
                bgmClips = state.bgmClips,
                selectedBgmClipId = state.selectedBgmClipId,
                onBgmClipSelected = { viewModel.onSelectBgmClip(it) },
                isRangeSelecting = state.isRangeSelecting,
                rangeTargetSegmentId = state.rangeTargetSegmentId,
                // Range coords are now GLOBAL timeline ms — selection can span
                // multiple consecutive video segments. Pass identity offsets
                // so Timeline.kt's local→global conversion is a no-op.
                rangeTargetSegmentStartMs = 0L,
                rangeTargetTrimStartMs = 0L,
                rangeTargetTrimEndMs = state.videoDurationMs,
                pendingRangeStartMs = state.pendingRangeStartMs,
                pendingRangeEndMs = state.pendingRangeEndMs,
                onRangeStartChanged = { viewModel.onSetPendingRangeStart(it) },
                onRangeEndChanged = { viewModel.onSetPendingRangeEnd(it) },
                onAppendRequested = { viewModel.onShowAppendSheet() },
                onSeek = { posMs ->
                    val clamped = posMs.coerceIn(0L, state.videoDurationMs)
                    viewModel.onUpdatePlaybackPosition(clamped)
                    val seg = currentSegmentAt(state.segments, clamped)
                    if (seg?.type == SegmentType.VIDEO) {
                        val segStart = segmentStartOffset(state.segments, seg.id)
                        val localMs = (clamped - segStart + seg.trimStartMs).coerceAtLeast(0L)
                        exoPlayer.seekTo(localMs)
                    }
                },
                onTrimStartChanged = { viewModel.onSetPendingTrimStart(it) },
                onTrimEndChanged = { viewModel.onSetPendingTrimEnd(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineHeightDp.dp)
            )
            } // end of action-bar/timeline surfaceVariant Column
        }
    }

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
            onInsert = { viewModel.onInsertPreviewClip() }
        )
    }

    if (state.showAppendSheet) {
        AppendSegmentSheet(
            onDismiss = { viewModel.onDismissAppendSheet() },
            onPickVideo = {
                viewModel.onDismissAppendSheet()
                appendMode = AppendPickerTarget.VIDEO
            },
            onPickPhoto = {
                viewModel.onDismissAppendSheet()
                appendMode = AppendPickerTarget.PHOTO
            }
        )
    }

    // Range edit modal removed: contents are now inline in SelectedSegmentActionBar
    // for any selected VIDEO segment.

    if (state.showTextOverlaySheet) {
        InsertTextOverlaySheet(
            pendingText = state.pendingOverlayText,
            pendingFontFamily = state.pendingOverlayFontFamily,
            pendingFontSizeSp = state.pendingOverlayFontSizeSp,
            pendingColorHex = state.pendingOverlayColorHex,
            error = state.textOverlayError,
            isEditing = state.editingTextOverlayId != null,
            onTextChange = { viewModel.onTextOverlayTextChanged(it) },
            onFontFamilyChange = { viewModel.onTextOverlayFontFamilyChanged(it) },
            onFontSizeChange = { viewModel.onTextOverlayFontSizeChanged(it) },
            onColorChange = { viewModel.onTextOverlayColorChanged(it) },
            onConfirm = { viewModel.onConfirmTextOverlay() },
            onDismiss = { viewModel.onDismissTextOverlaySheet() }
        )
    }

    if (state.showFrameSheet) {
        FrameSettingsSheet(
            pendingWidth = state.pendingFrameWidth,
            pendingHeight = state.pendingFrameHeight,
            pendingBackgroundColorHex = state.pendingBackgroundColorHex,
            error = state.frameError,
            onWidthChange = { viewModel.onFrameWidthInputChanged(it) },
            onHeightChange = { viewModel.onFrameHeightInputChanged(it) },
            onColorChange = { viewModel.onFrameBackgroundColorChanged(it) },
            onPreset = { viewModel.onApplyFramePreset(it) },
            onConfirm = { viewModel.onConfirmFrame() },
            onDismiss = { viewModel.onDismissFrameSheet() }
        )
    }

    val separationState = state.audioSeparation
    if (separationState != null) {
        AudioSeparationSheet(
            state = separationState,
            onDismiss = { viewModel.onDismissAudioSeparationSheet() },
            onSpeakersChange = { viewModel.onUpdateSeparationSpeakers(it) },
            onStart = { viewModel.onStartSeparation() },
            onToggleStem = { viewModel.onToggleStemSelection(it) },
            onStemVolumeChange = { id, vol -> viewModel.onUpdateStemVolume(id, vol) },
            onToggleMuteOriginal = { viewModel.onToggleMuteOriginalSegmentAudio() },
            onConfirmMix = { viewModel.onConfirmStemMix() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrameSettingsSheet(
    pendingWidth: String,
    pendingHeight: String,
    pendingBackgroundColorHex: String,
    error: String?,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onPreset: (FramePreset) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Frame", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("Preset", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                FramePreset.entries.forEach { preset ->
                    AssistChip(
                        onClick = { onPreset(preset) },
                        label = { Text(preset.label) },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = AssistChipDefaults.assistChipColors()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pendingWidth,
                    onValueChange = onWidthChange,
                    label = { Text("Width") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("×")
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = pendingHeight,
                    onValueChange = onHeightChange,
                    label = { Text("Height") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pendingBackgroundColorHex,
                onValueChange = onColorChange,
                label = { Text("Background (#RRGGBB)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.CenterEnd)) {
                Button(onClick = onConfirm) { Text("Apply") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SelectedSegmentActionBar(
    segment: Segment,
    canDelete: Boolean,
    onUpdateDuration: (Long) -> Unit,
    onDelete: () -> Unit,
    rangeStartMs: Long,
    rangeEndMs: Long,
    rangeVolume: Float,
    rangeSpeed: Float,
    onRangeChange: (Long, Long) -> Unit,
    onDuplicateRange: () -> Unit,
    onDeleteRange: () -> Unit,
    onRangeVolumeChange: (Float) -> Unit,
    onRangeSpeedChange: (Float) -> Unit,
    onApplyRangeVolume: (Float) -> Unit,
    onApplyRangeSpeed: (Float) -> Unit,
    onRequestAudioSeparation: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            val extras = buildString {
                if (segment.speedScale != 1f) append(" · ${"%.2f".format(segment.speedScale)}x")
                if (segment.volumeScale != 1f) append(" · vol ${"%.2f".format(segment.volumeScale)}")
            }
            Text(
                text = "${segment.type.name} · ${formatTime(segment.effectiveDurationMs)}$extras",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (segment.type == SegmentType.VIDEO) {
            VideoRangeEditPanel(
                segment = segment,
                rangeStartMs = rangeStartMs,
                rangeEndMs = rangeEndMs,
                rangeVolume = rangeVolume,
                rangeSpeed = rangeSpeed,
                onRangeChange = onRangeChange,
                onDuplicateRange = onDuplicateRange,
                onDeleteRange = onDeleteRange,
                onRangeVolumeChange = onRangeVolumeChange,
                onRangeSpeedChange = onRangeSpeedChange,
                onApplyRangeVolume = onApplyRangeVolume,
                onApplyRangeSpeed = onApplyRangeSpeed,
                onRequestAudioSeparation = onRequestAudioSeparation
            )
        }

        if (segment.type == SegmentType.IMAGE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(64.dp)
                )
                Slider(
                    value = segment.durationMs.toFloat(),
                    onValueChange = { onUpdateDuration(it.toLong()) },
                    valueRange = 500f..30_000f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(segment.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}

private enum class VideoEditTool { NONE, VOLUME, SPEED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoRangeEditPanel(
    segment: Segment,
    rangeStartMs: Long,
    rangeEndMs: Long,
    rangeVolume: Float,
    rangeSpeed: Float,
    onRangeChange: (Long, Long) -> Unit,
    onDuplicateRange: () -> Unit,
    onDeleteRange: () -> Unit,
    onRangeVolumeChange: (Float) -> Unit,
    onRangeSpeedChange: (Float) -> Unit,
    onApplyRangeVolume: (Float) -> Unit,
    onApplyRangeSpeed: (Float) -> Unit,
    onRequestAudioSeparation: () -> Unit
) {
    val trimStart = segment.trimStartMs
    val trimEnd = if (segment.trimEndMs <= 0L) segment.durationMs else segment.trimEndMs
    val canAct = rangeEndMs - rangeStartMs >= 100L
    var activeTool by remember(segment.id) { mutableStateOf(VideoEditTool.NONE) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        // Range start/end are adjusted by dragging the green handles directly
        // on the timeline track above. Show the current selection as text.
        Text(
            text = "구간 ${formatTime(rangeStartMs)} ~ ${formatTime(rangeEndMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        // CapCut-style horizontal tool row: tap to focus a tool (volume/speed)
        // or fire an immediate action (duplicate/delete range).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = Icons.Default.ContentCopy,
                label = "복제",
                enabled = canAct,
                onClick = { onDuplicateRange(); activeTool = VideoEditTool.NONE }
            )
            ToolButton(
                icon = Icons.Default.ContentCut,
                label = "구간 삭제",
                enabled = canAct,
                onClick = { onDeleteRange(); activeTool = VideoEditTool.NONE }
            )
            ToolButton(
                icon = Icons.Default.VolumeUp,
                label = "볼륨",
                selected = activeTool == VideoEditTool.VOLUME,
                onClick = {
                    activeTool = if (activeTool == VideoEditTool.VOLUME)
                        VideoEditTool.NONE else VideoEditTool.VOLUME
                }
            )
            ToolButton(
                icon = Icons.Default.Speed,
                label = "속도",
                selected = activeTool == VideoEditTool.SPEED,
                onClick = {
                    activeTool = if (activeTool == VideoEditTool.SPEED)
                        VideoEditTool.NONE else VideoEditTool.SPEED
                }
            )
            ToolButton(
                icon = Icons.Default.Hearing,
                label = "음원분리",
                onClick = { onRequestAudioSeparation(); activeTool = VideoEditTool.NONE }
            )
        }

        // Active tool panel — only the focused control is shown to keep the
        // bottom panel light, mirroring CapCut's expand-on-tap behaviour.
        when (activeTool) {
            VideoEditTool.VOLUME -> ToolSliderPanel(
                value = rangeVolume,
                valueRange = 0f..2f,
                steps = 0,
                label = "볼륨",
                valueText = "${(rangeVolume * 100).toInt()}%",
                applyEnabled = canAct,
                onValueChange = onRangeVolumeChange,
                onApply = { onApplyRangeVolume(rangeVolume) }
            )
            VideoEditTool.SPEED -> ToolSliderPanel(
                value = rangeSpeed,
                valueRange = 0.25f..4f,
                steps = 14,
                label = "속도",
                valueText = "${"%.2f".format(rangeSpeed)}x",
                applyEnabled = canAct,
                onValueChange = onRangeSpeedChange,
                onApply = { onApplyRangeSpeed(rangeSpeed) }
            )
            VideoEditTool.NONE -> Unit
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Use Modifier.clickable so onClick is re-bound on each recomposition.
    // pointerInput-based handlers stale-captured the closure and made repeated
    // taps no-ops (e.g. could not toggle the speed tool back off).
    Column(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ToolSliderPanel(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    valueText: String,
    applyEnabled: Boolean,
    onValueChange: (Float) -> Unit,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f)
        )
        Text(valueText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
        androidx.compose.material3.TextButton(onClick = onApply, enabled = applyEnabled) {
            Text("적용")
        }
    }
}

private enum class AppendPickerTarget { VIDEO, PHOTO }

private fun currentSegmentAt(segments: List<Segment>, positionMs: Long): Segment? {
    if (segments.isEmpty()) return null
    var acc = 0L
    for (seg in segments) {
        val next = acc + seg.effectiveDurationMs
        if (positionMs < next) return seg
        acc = next
    }
    return segments.last()
}

private fun segmentStartOffset(segments: List<Segment>, segmentId: String?): Long {
    if (segmentId == null) return 0L
    var acc = 0L
    for (seg in segments) {
        if (seg.id == segmentId) return acc
        acc += seg.effectiveDurationMs
    }
    return acc
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Single + button on the action bar that expands into a vertical menu of
 * insert options (text / image / music / frame ratio). Picking an option
 * fires its callback and dismisses the menu.
 */
@Composable
private fun InsertMenu(
    bgmEnabled: Boolean,
    onInsertText: () -> Unit,
    onInsertImage: () -> Unit,
    onInsertBgm: () -> Unit,
    onShowFrameSheet: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = "Insert")
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("텍스트") },
                leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                onClick = { expanded = false; onInsertText() }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("이미지") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = { expanded = false; onInsertImage() }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("음악") },
                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                enabled = bgmEnabled,
                onClick = { expanded = false; onInsertBgm() }
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("영상비율") },
                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
                onClick = { expanded = false; onShowFrameSheet() }
            )
        }
    }
}
