package com.example.dubcast.ui.timeline

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    val currentSegment = currentSegmentAt(state.segments, state.playbackPositionMs)
    val currentSegmentId = currentSegment?.id
    val currentSegmentStart = remember(state.segments, currentSegmentId) {
        segmentStartOffset(state.segments, currentSegmentId)
    }

    // Swap ExoPlayer to the current VIDEO segment's media and toggle
    // playWhenReady in a single coordinated effect so the two signals
    // cannot race across segment transitions.
    LaunchedEffect(currentSegmentId, currentSegment?.sourceUri, state.isPlaying) {
        val seg = currentSegment
        if (seg == null) {
            exoPlayer.clearMediaItems()
            exoPlayer.playWhenReady = false
            return@LaunchedEffect
        }
        if (seg.type == SegmentType.VIDEO) {
            val localMs = (state.playbackPositionMs - currentSegmentStart + seg.trimStartMs)
                .coerceAtLeast(0L)
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(seg.sourceUri)), localMs)
            exoPlayer.prepare()
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
        var timelineHeightDp by remember { mutableStateOf(120f) }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
            }

            if (state.isTrimming) {
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
                    IconButton(onClick = { viewModel.onDeleteSelectedClip() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            val selectedSegment = state.segments.find { it.id == state.selectedSegmentId }
            if (selectedSegment != null && !state.isTrimming) {
                SelectedSegmentActionBar(
                    segment = selectedSegment,
                    canDelete = state.segments.size > 1,
                    onEnterTrim = { viewModel.onEnterTrimMode() },
                    onEnterRange = { viewModel.onEnterRangeMode(selectedSegment.id) },
                    onUpdateDuration = { ms ->
                        viewModel.onUpdateImageSegmentDuration(selectedSegment.id, ms)
                    },
                    onDelete = { viewModel.onDeleteSelectedSegment() }
                )
            }

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
            onInsert = { showOnScreen -> viewModel.onInsertPreviewClip(showOnScreen) }
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

    if (state.isRangeSelecting) {
        val rangeSeg = state.segments.find { it.id == state.rangeTargetSegmentId }
        if (rangeSeg != null) {
            RangeActionSheet(
                segment = rangeSeg,
                pendingStartMs = state.pendingRangeStartMs,
                pendingEndMs = state.pendingRangeEndMs,
                pendingVolume = state.pendingRangeVolume,
                pendingSpeed = state.pendingRangeSpeed,
                onRangeChange = { s, e ->
                    viewModel.onSetPendingRangeEnd(e)
                    viewModel.onSetPendingRangeStart(s)
                },
                onDuplicate = { viewModel.onDuplicateRange() },
                onDelete = { viewModel.onDeleteRange() },
                onVolumeChange = { viewModel.onUpdatePendingRangeVolume(it) },
                onSpeedChange = { viewModel.onUpdatePendingRangeSpeed(it) },
                onApplyVolume = { viewModel.onApplyRangeVolume(it) },
                onApplySpeed = { viewModel.onApplyRangeSpeed(it) },
                onDismiss = { viewModel.onCancelRangeMode() }
            )
        }
    }
}

@Composable
private fun SelectedSegmentActionBar(
    segment: Segment,
    canDelete: Boolean,
    onEnterTrim: () -> Unit,
    onEnterRange: () -> Unit,
    onUpdateDuration: (Long) -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (segment.type == SegmentType.VIDEO) {
                IconButton(onClick = onEnterTrim) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Trim")
                }
                IconButton(onClick = onEnterRange) {
                    Icon(Icons.Default.Tune, contentDescription = "Range edit")
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
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
