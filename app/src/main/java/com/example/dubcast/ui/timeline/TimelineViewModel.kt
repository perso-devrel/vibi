package com.example.dubcast.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.repository.SegmentRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TextOverlayRepository
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.usecase.image.AddImageClipUseCase
import com.example.dubcast.domain.usecase.image.DeleteImageClipUseCase
import com.example.dubcast.domain.usecase.image.UpdateImageClipUseCase
import com.example.dubcast.domain.usecase.input.ImageMetadataExtractor
import com.example.dubcast.domain.usecase.input.SetProjectFrameUseCase
import com.example.dubcast.domain.usecase.input.VideoMetadataExtractor
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.UndoRedoManager
import com.example.dubcast.domain.usecase.text.AddTextOverlayUseCase
import com.example.dubcast.domain.usecase.text.DeleteTextOverlayUseCase
import com.example.dubcast.domain.usecase.text.DuplicateTextOverlayUseCase
import com.example.dubcast.domain.usecase.text.UpdateTextOverlayUseCase
import com.example.dubcast.domain.usecase.timeline.AddImageSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.AddVideoSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.DeleteDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.DuplicateSegmentRangeUseCase
import com.example.dubcast.domain.usecase.timeline.MoveDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.RemoveSegmentRangeUseCase
import com.example.dubcast.domain.usecase.timeline.RemoveSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.SplitDubTextToSubtitlesUseCase
import com.example.dubcast.domain.usecase.timeline.SplitSegmentUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentSpeedUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.example.dubcast.domain.usecase.timeline.UpdateSegmentVolumeUseCase
import com.example.dubcast.domain.usecase.tts.GetVoiceListUseCase
import com.example.dubcast.domain.usecase.tts.SynthesizeDubClipUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewDubClip(
    val text: String,
    val voiceId: String,
    val voiceName: String,
    val audioFilePath: String,
    val durationMs: Long
)

data class TimelineUiState(
    val projectId: String = "",
    val segments: List<Segment> = emptyList(),
    val selectedSegmentId: String? = null,
    val videoUri: String = "",
    val videoDurationMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val showAppendSheet: Boolean = false,
    val dubClips: List<DubClip> = emptyList(),
    val subtitleClips: List<SubtitleClip> = emptyList(),
    val imageClips: List<ImageClip> = emptyList(),
    val playbackPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedDubClipId: String? = null,
    val selectedSubtitleClipId: String? = null,
    val selectedImageClipId: String? = null,
    val voices: List<Voice> = emptyList(),
    val isVoicesLoading: Boolean = false,
    val showDubbingSheet: Boolean = false,
    val showSubtitleSheet: Boolean = false,
    val isSynthesizing: Boolean = false,
    val synthError: String? = null,
    val previewClip: PreviewDubClip? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isVideoSelected: Boolean = false,
    val videoVolume: Float = 1.0f,
    val showVideoVolumeSlider: Boolean = false,
    val showDubVolumeSlider: Boolean = false,
    val isTrimming: Boolean = false,
    val pendingTrimStartMs: Long = 0L,
    val pendingTrimEndMs: Long = 0L,
    val isRangeSelecting: Boolean = false,
    val rangeTargetSegmentId: String? = null,
    val pendingRangeStartMs: Long = 0L,
    val pendingRangeEndMs: Long = 0L,
    val showRangeActionSheet: Boolean = false,
    val pendingRangeVolume: Float = 1.0f,
    val pendingRangeSpeed: Float = 1.0f,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val showFrameSheet: Boolean = false,
    val pendingFrameWidth: String = "",
    val pendingFrameHeight: String = "",
    val pendingBackgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val frameError: String? = null,
    val textOverlays: List<TextOverlay> = emptyList(),
    val selectedTextOverlayId: String? = null,
    val showTextOverlaySheet: Boolean = false,
    val editingTextOverlayId: String? = null,
    val pendingOverlayText: String = "",
    val pendingOverlayFontFamily: String = TextOverlay.DEFAULT_FONT_FAMILY,
    val pendingOverlayFontSizeSp: Float = TextOverlay.DEFAULT_FONT_SIZE_SP,
    val pendingOverlayColorHex: String = TextOverlay.DEFAULT_COLOR_HEX,
    val pendingOverlayStartMs: Long = 0L,
    val pendingOverlayEndMs: Long = 0L,
    val textOverlayError: String? = null
) {
    val effectiveTrimEndMs: Long get() = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
    val frameAspectRatio: Float
        get() = if (frameWidth > 0 && frameHeight > 0) {
            frameWidth.toFloat() / frameHeight.toFloat()
        } else 0f
}

data class TimelineSnapshot(
    val segments: List<Segment>,
    val dubClips: List<DubClip>,
    val subtitleClips: List<SubtitleClip>,
    val imageClips: List<ImageClip>,
    val textOverlays: List<TextOverlay> = emptyList()
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val segmentRepository: SegmentRepository,
    private val dubClipRepository: DubClipRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val imageClipRepository: ImageClipRepository,
    private val editProjectRepository: EditProjectRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val ttsRepository: TtsRepository,
    private val synthesizeDubClip: SynthesizeDubClipUseCase,
    private val getVoiceList: GetVoiceListUseCase,
    private val moveDubClip: MoveDubClipUseCase,
    private val deleteDubClip: DeleteDubClipUseCase,
    private val addSubtitleClip: AddSubtitleClipUseCase,
    private val deleteSubtitleClip: DeleteSubtitleClipUseCase,
    private val splitDubTextToSubtitles: SplitDubTextToSubtitlesUseCase,
    private val addImageClip: AddImageClipUseCase,
    private val updateImageClip: UpdateImageClipUseCase,
    private val deleteImageClip: DeleteImageClipUseCase,
    private val updateSegmentTrim: UpdateSegmentTrimUseCase,
    private val addVideoSegment: AddVideoSegmentUseCase,
    private val addImageSegment: AddImageSegmentUseCase,
    private val removeSegment: RemoveSegmentUseCase,
    private val updateImageSegmentDuration: UpdateImageSegmentDurationUseCase,
    private val updateImageSegmentPosition: UpdateImageSegmentPositionUseCase,
    private val splitSegment: SplitSegmentUseCase,
    private val duplicateSegmentRange: DuplicateSegmentRangeUseCase,
    private val removeSegmentRange: RemoveSegmentRangeUseCase,
    private val updateSegmentVolume: UpdateSegmentVolumeUseCase,
    private val updateSegmentSpeed: UpdateSegmentSpeedUseCase,
    private val setProjectFrame: SetProjectFrameUseCase,
    private val addTextOverlay: AddTextOverlayUseCase,
    private val updateTextOverlay: UpdateTextOverlayUseCase,
    private val deleteTextOverlay: DeleteTextOverlayUseCase,
    private val duplicateTextOverlay: DuplicateTextOverlayUseCase,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    private val imageMetadataExtractor: ImageMetadataExtractor
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _uiState = MutableStateFlow(TimelineUiState(projectId = projectId))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _navigateToExport = MutableSharedFlow<String>()
    val navigateToExport: SharedFlow<String> = _navigateToExport.asSharedFlow()

    private val undoRedoManager = UndoRedoManager<TimelineSnapshot>(maxHistory = 50)

    init {
        loadSegments()
        loadVoices()
        observeClips()
        observeProject()
        observeTextOverlays()
    }

    private fun observeProject() {
        viewModelScope.launch {
            editProjectRepository.observeProject(projectId).collect { project ->
                if (project != null) {
                    _uiState.value = _uiState.value.copy(
                        frameWidth = project.frameWidth,
                        frameHeight = project.frameHeight,
                        backgroundColorHex = project.backgroundColorHex
                    )
                }
            }
        }
    }

    private fun observeTextOverlays() {
        viewModelScope.launch {
            textOverlayRepository.observeOverlays(projectId).collect { overlays ->
                _uiState.value = _uiState.value.copy(textOverlays = overlays)
            }
        }
    }

    private fun loadSegments() {
        viewModelScope.launch {
            segmentRepository.observeByProjectId(projectId).collect { segments ->
                val first = segments.firstOrNull()
                val total = segments.sumOf { it.effectiveDurationMs }
                val currentSelectedId = _uiState.value.selectedSegmentId
                val selectedId = currentSelectedId?.takeIf { id -> segments.any { it.id == id } }
                    ?: first?.id
                val selected = segments.firstOrNull { it.id == selectedId }
                val (globalTrimStart, globalTrimEnd) = selectedSegmentGlobalTrim(segments, selected)
                _uiState.value = _uiState.value.copy(
                    segments = segments,
                    selectedSegmentId = selectedId,
                    videoUri = first?.sourceUri.orEmpty(),
                    videoDurationMs = total,
                    videoWidth = first?.width ?: 0,
                    videoHeight = first?.height ?: 0,
                    trimStartMs = globalTrimStart,
                    trimEndMs = globalTrimEnd
                )
            }
        }
    }

    private fun selectedSegmentGlobalTrim(
        segments: List<Segment>,
        selected: Segment?
    ): Pair<Long, Long> {
        if (selected == null || selected.type != SegmentType.VIDEO) return 0L to 0L
        val segStart = segmentStartOffsetMs(segments, selected.id)
        val trimStart = selected.trimStartMs
        val trimEnd = if (selected.trimEndMs <= 0L) selected.durationMs else selected.trimEndMs
        val hasTrim = trimStart > 0L || trimEnd < selected.durationMs
        return if (hasTrim) {
            (segStart + trimStart) to (segStart + trimEnd)
        } else {
            0L to 0L
        }
    }

    private fun segmentStartOffsetMs(segments: List<Segment>, segmentId: String): Long {
        var acc = 0L
        for (seg in segments) {
            if (seg.id == segmentId) return acc
            acc += seg.effectiveDurationMs
        }
        return acc
    }

    private fun loadVoices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVoicesLoading = true)
            val result = getVoiceList()
            val voices = result.getOrDefault(emptyList()).ifEmpty { DEFAULT_VOICES }
            _uiState.value = _uiState.value.copy(
                voices = voices,
                isVoicesLoading = false
            )
        }
    }

    private fun observeClips() {
        viewModelScope.launch {
            combine(
                dubClipRepository.observeClips(projectId),
                subtitleClipRepository.observeClips(projectId),
                imageClipRepository.observeClips(projectId)
            ) { dubs, subs, images -> Triple(dubs, subs, images) }
                .collect { (dubs, subs, images) ->
                    _uiState.value = _uiState.value.copy(
                        dubClips = dubs,
                        subtitleClips = subs,
                        imageClips = images
                    )
                }
        }
    }

    private suspend fun pushUndoState() {
        // Read directly from DB to avoid race with async Flow emission
        val dubs = dubClipRepository.observeClips(projectId).first()
        val subs = subtitleClipRepository.observeClips(projectId).first()
        val images = imageClipRepository.observeClips(projectId).first()
        val segs = segmentRepository.getByProjectId(projectId)
        val texts = textOverlayRepository.observeOverlays(projectId).first()
        undoRedoManager.pushState(TimelineSnapshot(segs, dubs, subs, images, texts))
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.value = _uiState.value.copy(
            canUndo = undoRedoManager.canUndo,
            canRedo = undoRedoManager.canRedo
        )
    }

    fun onUpdatePlaybackPosition(positionMs: Long) {
        _uiState.value = _uiState.value.copy(playbackPositionMs = positionMs)
    }

    fun onTogglePlayback() {
        _uiState.value = _uiState.value.copy(isPlaying = !_uiState.value.isPlaying)
    }

    fun onShowDubbingSheet() {
        _uiState.value = _uiState.value.copy(showDubbingSheet = true, synthError = null)
    }

    fun onDismissDubbingSheet() {
        _uiState.value = _uiState.value.copy(
            showDubbingSheet = false,
            synthError = null,
            previewClip = null
        )
    }

    fun onShowSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleSheet = true)
    }

    fun onDismissSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
    }

    fun onSynthesize(text: String, voiceId: String, voiceName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSynthesizing = true, synthError = null, previewClip = null)

            val result = ttsRepository.synthesize(text, voiceId)

            result.fold(
                onSuccess = { ttsResult ->
                    _uiState.value = _uiState.value.copy(
                        isSynthesizing = false,
                        previewClip = PreviewDubClip(
                            text = text,
                            voiceId = voiceId,
                            voiceName = voiceName,
                            audioFilePath = ttsResult.localAudioPath,
                            durationMs = ttsResult.durationMs
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSynthesizing = false,
                        synthError = error.message
                    )
                }
            )
        }
    }

    fun onInsertPreviewClip(showOnScreen: Boolean = false) {
        val preview = _uiState.value.previewClip ?: return
        viewModelScope.launch {
            pushUndoState()
            val dubClipId = java.util.UUID.randomUUID().toString()
            val startMs = _uiState.value.playbackPositionMs
            val clip = DubClip(
                id = dubClipId,
                projectId = projectId,
                text = preview.text,
                voiceId = preview.voiceId,
                voiceName = preview.voiceName,
                audioFilePath = preview.audioFilePath,
                startMs = startMs,
                durationMs = preview.durationMs
            )
            dubClipRepository.addClip(clip)

            if (showOnScreen) {
                val autoSubtitles = splitDubTextToSubtitles(
                    text = preview.text,
                    startMs = startMs,
                    durationMs = preview.durationMs,
                    dubClipId = dubClipId,
                    projectId = projectId
                )
                for (subtitle in autoSubtitles) {
                    subtitleClipRepository.addClip(subtitle)
                }
            }

            _uiState.value = _uiState.value.copy(
                showDubbingSheet = false,
                previewClip = null
            )
        }
    }

    fun onAddSubtitle(text: String, startMs: Long, endMs: Long, position: SubtitlePosition) {
        viewModelScope.launch {
            pushUndoState()
            addSubtitleClip(projectId, text, startMs, endMs, position)
            _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
        }
    }

    fun onMoveDubClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            pushUndoState()
            moveDubClip(clip, newStartMs, _uiState.value.videoDurationMs)
        }
    }

    fun onSelectDubClip(clipId: String?) {
        val currentSelected = _uiState.value.selectedDubClipId
        val newSelected = if (currentSelected == clipId) null else clipId
        _uiState.value = _uiState.value.copy(
            selectedDubClipId = newSelected,
            selectedSubtitleClipId = null,
            selectedImageClipId = null,
            isVideoSelected = false,
            showVideoVolumeSlider = false,
            showDubVolumeSlider = false
        )
    }

    fun onSelectSubtitleClip(clipId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedSubtitleClipId = clipId,
            selectedDubClipId = null,
            selectedImageClipId = null,
            isVideoSelected = false,
            showVideoVolumeSlider = false,
            showDubVolumeSlider = false
        )
    }

    fun onSelectImageClip(clipId: String?) {
        val currentSelected = _uiState.value.selectedImageClipId
        val newSelected = if (currentSelected == clipId) null else clipId
        _uiState.value = _uiState.value.copy(
            selectedImageClipId = newSelected,
            selectedDubClipId = null,
            selectedSubtitleClipId = null,
            isVideoSelected = false,
            showVideoVolumeSlider = false,
            showDubVolumeSlider = false
        )
    }

    fun onUpdateDubClipVolume(clipId: String, volume: Float) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            dubClipRepository.updateClip(clip.copy(volume = volume.coerceIn(0f, 2f)))
        }
    }

    fun onDeleteSelectedClip() {
        viewModelScope.launch {
            val state = _uiState.value
            pushUndoState()
            state.selectedDubClipId?.let { dubClipId ->
                deleteDubClip(dubClipId)
                subtitleClipRepository.deleteClipsBySourceDubClipId(dubClipId)
            }
            state.selectedSubtitleClipId?.let { deleteSubtitleClip(it) }
            state.selectedImageClipId?.let { deleteImageClip(it) }
            _uiState.value = _uiState.value.copy(
                selectedDubClipId = null,
                selectedSubtitleClipId = null,
                selectedImageClipId = null
            )
        }
    }

    fun onInsertImage(uri: String, defaultDurationMs: Long = 3000L) {
        viewModelScope.launch {
            pushUndoState()
            val state = _uiState.value
            val videoDurationMs = state.videoDurationMs
            val maxStart = if (videoDurationMs > 0L) (videoDurationMs - 500L).coerceAtLeast(0L) else Long.MAX_VALUE
            val startMs = state.playbackPositionMs.coerceIn(0L, maxStart)
            val maxEnd = if (videoDurationMs > 0L) videoDurationMs else (startMs + defaultDurationMs)
            val endMs = (startMs + defaultDurationMs)
                .coerceAtMost(maxEnd)
                .coerceAtLeast(startMs + 500L)
            addImageClip(projectId = projectId, imageUri = uri, startMs = startMs, endMs = endMs)
        }
    }

    fun onMoveImageClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.imageClips.find { it.id == clipId } ?: return@launch
            pushUndoState()
            val duration = clip.endMs - clip.startMs
            val videoDuration = _uiState.value.videoDurationMs
            val coercedStart = newStartMs.coerceAtLeast(0L).let {
                if (videoDuration > 0L) it.coerceAtMost((videoDuration - duration).coerceAtLeast(0L)) else it
            }
            updateImageClip(clip.copy(startMs = coercedStart, endMs = coercedStart + duration))
        }
    }

    fun onResizeImageClipDuration(clipId: String, newEndMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.imageClips.find { it.id == clipId } ?: return@launch
            pushUndoState()
            val minEnd = clip.startMs + 500L
            val videoDuration = _uiState.value.videoDurationMs
            val coercedEnd = newEndMs.coerceAtLeast(minEnd).let {
                if (videoDuration > 0L) it.coerceAtMost(videoDuration) else it
            }
            updateImageClip(clip.copy(endMs = coercedEnd))
        }
    }

    fun onUpdateImageClipPosition(
        clipId: String,
        xPct: Float,
        yPct: Float,
        widthPct: Float,
        heightPct: Float
    ) {
        viewModelScope.launch {
            val clip = imageClipRepository.getClip(clipId) ?: return@launch
            updateImageClip(
                clip.copy(xPct = xPct, yPct = yPct, widthPct = widthPct, heightPct = heightPct)
            )
        }
    }

    fun onUpdateSubtitlePosition(
        clipId: String,
        xPct: Float,
        yPct: Float,
        widthPct: Float,
        heightPct: Float
    ) {
        viewModelScope.launch {
            val clip = subtitleClipRepository.getClip(clipId) ?: return@launch
            subtitleClipRepository.updateClip(
                clip.copy(xPct = xPct, yPct = yPct, widthPct = widthPct, heightPct = heightPct)
            )
        }
    }

    fun onUndo() {
        viewModelScope.launch {
            val snapshot = undoRedoManager.undo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
        }
    }

    fun onRedo() {
        viewModelScope.launch {
            val snapshot = undoRedoManager.redo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
        }
    }

    private suspend fun restoreSnapshot(snapshot: TimelineSnapshot) {
        dubClipRepository.deleteAllClips(projectId)
        for (clip in snapshot.dubClips) {
            dubClipRepository.addClip(clip)
        }
        subtitleClipRepository.deleteAllClips(projectId)
        for (clip in snapshot.subtitleClips) {
            subtitleClipRepository.addClip(clip)
        }
        imageClipRepository.deleteAllClips(projectId)
        for (clip in snapshot.imageClips) {
            imageClipRepository.addClip(clip)
        }
        segmentRepository.deleteAllByProjectId(projectId)
        for (seg in snapshot.segments) {
            segmentRepository.addSegment(seg)
        }
        textOverlayRepository.deleteAllByProjectId(projectId)
        for (overlay in snapshot.textOverlays) {
            textOverlayRepository.addOverlay(overlay)
        }
    }

    fun onVideoTrackTapped() {
        if (_uiState.value.isVideoSelected) {
            onDeselectVideo()
        } else {
            _uiState.value = _uiState.value.copy(
                isVideoSelected = true,
                selectedDubClipId = null,
                selectedSubtitleClipId = null,
                selectedImageClipId = null
            )
        }
    }

    fun onDeselectVideo() {
        _uiState.value = _uiState.value.copy(isVideoSelected = false, showVideoVolumeSlider = false)
    }

    fun onToggleVideoVolumeSlider() {
        _uiState.value = _uiState.value.copy(showVideoVolumeSlider = !_uiState.value.showVideoVolumeSlider)
    }

    fun onToggleDubVolumeSlider() {
        _uiState.value = _uiState.value.copy(showDubVolumeSlider = !_uiState.value.showDubVolumeSlider)
    }

    fun onUpdateVideoVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(videoVolume = volume.coerceIn(0f, 2f))
    }

    fun onEnterTrimMode() {
        val state = _uiState.value
        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId }
            ?: state.segments.firstOrNull { it.type == SegmentType.VIDEO }
            ?: return
        if (selected.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, selected.id)
        val trimEndLocal = if (selected.trimEndMs <= 0L) selected.durationMs else selected.trimEndMs
        _uiState.value = state.copy(
            isTrimming = true,
            selectedSegmentId = selected.id,
            pendingTrimStartMs = segStart + selected.trimStartMs,
            pendingTrimEndMs = segStart + trimEndLocal,
            isPlaying = false
        )
    }

    fun onSetPendingTrimStart(ms: Long) {
        val state = _uiState.value
        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId } ?: return
        if (selected.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, selected.id)
        val segEnd = segStart + selected.durationMs
        val upperBound = (state.pendingTrimEndMs - 500L).coerceAtMost(segEnd - 500L)
        val newStart = ms.coerceIn(segStart, upperBound.coerceAtLeast(segStart))
        _uiState.value = state.copy(pendingTrimStartMs = newStart)
    }

    fun onSetPendingTrimEnd(ms: Long) {
        val state = _uiState.value
        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId } ?: return
        if (selected.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, selected.id)
        val segEnd = segStart + selected.durationMs
        val lowerBound = (state.pendingTrimStartMs + 500L).coerceAtLeast(segStart + 500L)
        val newEnd = ms.coerceIn(lowerBound.coerceAtMost(segEnd), segEnd)
        _uiState.value = state.copy(pendingTrimEndMs = newEnd)
    }

    fun onConfirmTrim() {
        val state = _uiState.value
        val segmentId = state.selectedSegmentId
            ?: state.segments.firstOrNull { it.type == SegmentType.VIDEO }?.id
            ?: return
        val segStart = segmentStartOffsetMs(state.segments, segmentId)
        val localTrimStart = (state.pendingTrimStartMs - segStart).coerceAtLeast(0L)
        val localTrimEnd = (state.pendingTrimEndMs - segStart).coerceAtLeast(localTrimStart + 500L)
        _uiState.value = state.copy(
            trimStartMs = state.pendingTrimStartMs,
            trimEndMs = state.pendingTrimEndMs,
            isTrimming = false,
            isVideoSelected = false
        )
        viewModelScope.launch {
            updateSegmentTrim(
                segmentId = segmentId,
                trimStartMs = localTrimStart,
                trimEndMs = localTrimEnd
            )
        }
    }

    fun onCancelTrim() {
        _uiState.value = _uiState.value.copy(isTrimming = false, isVideoSelected = false)
    }

    fun onNavigateToExport() {
        viewModelScope.launch {
            _navigateToExport.emit(projectId)
        }
    }

    fun onShowAppendSheet() {
        _uiState.value = _uiState.value.copy(showAppendSheet = true)
    }

    fun onDismissAppendSheet() {
        _uiState.value = _uiState.value.copy(showAppendSheet = false)
    }

    fun onAppendVideoSegment(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAppendSheet = false, isPlaying = false)
            val info = videoMetadataExtractor.extract(uri) ?: return@launch
            addVideoSegment(projectId = projectId, videoInfo = info)
        }
    }

    fun onAppendImageSegment(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAppendSheet = false, isPlaying = false)
            val info = imageMetadataExtractor.extract(uri) ?: return@launch
            addImageSegment(projectId = projectId, imageInfo = info)
        }
    }

    fun onSelectSegment(segmentId: String?) {
        val current = _uiState.value.selectedSegmentId
        val next = if (current == segmentId) null else segmentId
        val selected = _uiState.value.segments.firstOrNull { it.id == next }
        _uiState.value = _uiState.value.copy(
            selectedSegmentId = next,
            selectedDubClipId = null,
            selectedSubtitleClipId = null,
            selectedImageClipId = null,
            isVideoSelected = false,
            trimStartMs = selected?.trimStartMs ?: 0L,
            trimEndMs = selected?.trimEndMs ?: 0L
        )
    }

    fun onDeleteSelectedSegment() {
        val segmentId = _uiState.value.selectedSegmentId ?: return
        val segments = _uiState.value.segments
        if (segments.size <= 1) return
        viewModelScope.launch {
            removeSegment(segmentId)
            _uiState.value = _uiState.value.copy(
                selectedSegmentId = null,
                isPlaying = false,
                playbackPositionMs = 0L
            )
        }
    }

    fun onUpdateImageSegmentDuration(segmentId: String, durationMs: Long) {
        viewModelScope.launch {
            updateImageSegmentDuration(segmentId, durationMs)
        }
    }

    fun onResizeImageSegmentByDrag(segmentId: String, requestedDurationMs: Long) {
        viewModelScope.launch {
            val seg = _uiState.value.segments.firstOrNull { it.id == segmentId } ?: return@launch
            if (seg.type != SegmentType.IMAGE) return@launch
            val clamped = requestedDurationMs.coerceIn(MIN_IMAGE_DURATION_MS, MAX_IMAGE_DURATION_MS)
            if (clamped == seg.durationMs) return@launch
            pushUndoState()
            updateImageSegmentDuration(segmentId, clamped)
        }
    }

    fun onUpdateImageSegmentPosition(
        segmentId: String,
        xPct: Float,
        yPct: Float,
        widthPct: Float,
        heightPct: Float
    ) {
        viewModelScope.launch {
            updateImageSegmentPosition(segmentId, xPct, yPct, widthPct, heightPct)
        }
    }

    fun onEnterRangeMode(segmentId: String) {
        val seg = _uiState.value.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        val trimStart = seg.trimStartMs
        val trimEnd = if (seg.trimEndMs <= 0L) seg.durationMs else seg.trimEndMs
        val defaultEnd = (trimStart + 1000L).coerceAtMost(trimEnd)
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = true,
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            pendingRangeStartMs = trimStart,
            pendingRangeEndMs = defaultEnd,
            showRangeActionSheet = false,
            pendingRangeVolume = seg.volumeScale,
            pendingRangeSpeed = seg.speedScale,
            isPlaying = false
        )
    }

    fun onSetPendingRangeStart(localMs: Long) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == state.rangeTargetSegmentId } ?: return
        val trimStart = seg.trimStartMs
        val trimEnd = if (seg.trimEndMs <= 0L) seg.durationMs else seg.trimEndMs
        val upper = (state.pendingRangeEndMs - MIN_RANGE_MS).coerceAtLeast(trimStart)
        val clamped = localMs.coerceIn(trimStart, upper.coerceAtMost(trimEnd))
        _uiState.value = state.copy(pendingRangeStartMs = clamped)
    }

    fun onSetPendingRangeEnd(localMs: Long) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == state.rangeTargetSegmentId } ?: return
        val trimStart = seg.trimStartMs
        val trimEnd = if (seg.trimEndMs <= 0L) seg.durationMs else seg.trimEndMs
        val lower = (state.pendingRangeStartMs + MIN_RANGE_MS).coerceAtMost(trimEnd)
        val clamped = localMs.coerceIn(lower.coerceAtLeast(trimStart), trimEnd)
        _uiState.value = state.copy(pendingRangeEndMs = clamped)
    }

    fun onConfirmRangeSelection() {
        _uiState.value = _uiState.value.copy(showRangeActionSheet = true)
    }

    fun onCancelRangeMode() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false
        )
    }

    fun onUpdatePendingRangeVolume(value: Float) {
        _uiState.value = _uiState.value.copy(pendingRangeVolume = value.coerceIn(0f, 2f))
    }

    fun onUpdatePendingRangeSpeed(value: Float) {
        _uiState.value = _uiState.value.copy(pendingRangeSpeed = value.coerceIn(0.25f, 4f))
    }

    fun onDuplicateRange() {
        val state = _uiState.value
        val segmentId = state.rangeTargetSegmentId ?: return
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        viewModelScope.launch {
            pushUndoState()
            duplicateSegmentRange(segmentId, start, end)
            resetRangeMode()
        }
    }

    fun onDeleteRange() {
        val state = _uiState.value
        val segmentId = state.rangeTargetSegmentId ?: return
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        viewModelScope.launch {
            pushUndoState()
            removeSegmentRange(segmentId, start, end)
            resetRangeMode()
        }
    }

    fun onApplyRangeVolume(value: Float) {
        val state = _uiState.value
        val segmentId = state.rangeTargetSegmentId ?: return
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        viewModelScope.launch {
            pushUndoState()
            val result = splitSegment(segmentId, start, end)
            updateSegmentVolume(result.middle.id, value)
            resetRangeMode()
        }
    }

    fun onApplyRangeSpeed(value: Float) {
        val state = _uiState.value
        val segmentId = state.rangeTargetSegmentId ?: return
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        viewModelScope.launch {
            pushUndoState()
            val result = splitSegment(segmentId, start, end)
            updateSegmentSpeed(result.middle.id, value)
            resetRangeMode()
        }
    }

    private fun resetRangeMode() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false
        )
    }

    fun segmentStartMs(segmentId: String): Long {
        val segments = _uiState.value.segments
        var acc = 0L
        for (seg in segments) {
            if (seg.id == segmentId) return acc
            acc += seg.effectiveDurationMs
        }
        return acc
    }

    fun currentSegmentAt(positionMs: Long): Segment? {
        val segments = _uiState.value.segments
        var acc = 0L
        for (seg in segments) {
            val next = acc + seg.effectiveDurationMs
            if (positionMs < next) return seg
            acc = next
        }
        return segments.lastOrNull()
    }

    fun onShowFrameSheet() {
        val state = _uiState.value
        _uiState.value = state.copy(
            showFrameSheet = true,
            pendingFrameWidth = if (state.frameWidth > 0) state.frameWidth.toString() else "",
            pendingFrameHeight = if (state.frameHeight > 0) state.frameHeight.toString() else "",
            pendingBackgroundColorHex = state.backgroundColorHex,
            frameError = null
        )
    }

    fun onDismissFrameSheet() {
        _uiState.value = _uiState.value.copy(showFrameSheet = false, frameError = null)
    }

    fun onFrameWidthInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingFrameWidth = value, frameError = null)
    }

    fun onFrameHeightInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingFrameHeight = value, frameError = null)
    }

    fun onFrameBackgroundColorChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingBackgroundColorHex = value, frameError = null)
    }

    fun onApplyFramePreset(preset: FramePreset) {
        val state = _uiState.value
        val basis = state.frameLongestSidePx().coerceAtLeast(MIN_FRAME_DIMENSION)
        val (w, h) = preset.toDimensions(basis)
        _uiState.value = state.copy(
            pendingFrameWidth = w.toString(),
            pendingFrameHeight = h.toString(),
            frameError = null
        )
    }

    fun onConfirmFrame() {
        val state = _uiState.value
        val width = state.pendingFrameWidth.toIntOrNull()
        val height = state.pendingFrameHeight.toIntOrNull()
        if (width == null || width <= 0 || height == null || height <= 0) {
            _uiState.value = state.copy(frameError = "Width와 Height는 양의 정수")
            return
        }
        if (width > MAX_FRAME_DIMENSION || height > MAX_FRAME_DIMENSION) {
            _uiState.value = state.copy(frameError = "최대 ${MAX_FRAME_DIMENSION}px")
            return
        }
        val color = state.pendingBackgroundColorHex.trim()
        viewModelScope.launch {
            try {
                pushUndoState()
                setProjectFrame(projectId, width, height, color)
                _uiState.value = _uiState.value.copy(showFrameSheet = false, frameError = null)
            } catch (e: IllegalArgumentException) {
                // SetProjectFrameUseCase is the source of truth for color/dimension
                // validation; keep its message as-is for the UI.
                _uiState.value = _uiState.value.copy(frameError = e.message ?: "Invalid frame")
            }
        }
    }

    private fun TimelineUiState.frameLongestSidePx(): Int {
        val segMax = segments.firstOrNull()?.let { maxOf(it.width, it.height) } ?: 0
        return maxOf(frameWidth, frameHeight, segMax)
    }

    fun onShowTextOverlaySheetForNew() {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(1L)
        val start = state.playbackPositionMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        val end = (start + DEFAULT_OVERLAY_DURATION_MS).coerceAtMost(total)
        _uiState.value = state.copy(
            showTextOverlaySheet = true,
            editingTextOverlayId = null,
            pendingOverlayText = "",
            pendingOverlayFontFamily = TextOverlay.DEFAULT_FONT_FAMILY,
            pendingOverlayFontSizeSp = TextOverlay.DEFAULT_FONT_SIZE_SP,
            pendingOverlayColorHex = TextOverlay.DEFAULT_COLOR_HEX,
            pendingOverlayStartMs = start,
            pendingOverlayEndMs = end,
            textOverlayError = null
        )
    }

    fun onShowTextOverlaySheetForEdit(overlayId: String) {
        val overlay = _uiState.value.textOverlays.firstOrNull { it.id == overlayId } ?: return
        _uiState.value = _uiState.value.copy(
            showTextOverlaySheet = true,
            editingTextOverlayId = overlay.id,
            pendingOverlayText = overlay.text,
            pendingOverlayFontFamily = overlay.fontFamily,
            pendingOverlayFontSizeSp = overlay.fontSizeSp,
            pendingOverlayColorHex = overlay.colorHex,
            pendingOverlayStartMs = overlay.startMs,
            pendingOverlayEndMs = overlay.endMs,
            textOverlayError = null
        )
    }

    fun onDismissTextOverlaySheet() {
        _uiState.value = _uiState.value.copy(
            showTextOverlaySheet = false,
            textOverlayError = null
        )
    }

    fun onTextOverlayTextChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingOverlayText = value, textOverlayError = null)
    }

    fun onTextOverlayFontFamilyChanged(family: String) {
        _uiState.value = _uiState.value.copy(pendingOverlayFontFamily = family, textOverlayError = null)
    }

    fun onTextOverlayFontSizeChanged(sizeSp: Float) {
        _uiState.value = _uiState.value.copy(
            pendingOverlayFontSizeSp = sizeSp.coerceIn(
                TextOverlay.MIN_FONT_SIZE_SP, TextOverlay.MAX_FONT_SIZE_SP
            ),
            textOverlayError = null
        )
    }

    fun onTextOverlayColorChanged(colorHex: String) {
        _uiState.value = _uiState.value.copy(pendingOverlayColorHex = colorHex, textOverlayError = null)
    }

    fun onConfirmTextOverlay() {
        val state = _uiState.value
        val text = state.pendingOverlayText.trim()
        if (text.isEmpty()) {
            _uiState.value = state.copy(textOverlayError = "Text는 비어있을 수 없음")
            return
        }
        if (state.pendingOverlayEndMs <= state.pendingOverlayStartMs) {
            _uiState.value = state.copy(textOverlayError = "End는 Start보다 커야 함")
            return
        }
        viewModelScope.launch {
            try {
                pushUndoState()
                val editId = state.editingTextOverlayId
                if (editId == null) {
                    addTextOverlay(
                        projectId = projectId,
                        text = text,
                        startMs = state.pendingOverlayStartMs,
                        endMs = state.pendingOverlayEndMs,
                        fontFamily = state.pendingOverlayFontFamily,
                        fontSizeSp = state.pendingOverlayFontSizeSp,
                        colorHex = state.pendingOverlayColorHex
                    )
                } else {
                    updateTextOverlay(
                        overlayId = editId,
                        text = text,
                        fontFamily = state.pendingOverlayFontFamily,
                        fontSizeSp = state.pendingOverlayFontSizeSp,
                        colorHex = state.pendingOverlayColorHex,
                        startMs = state.pendingOverlayStartMs,
                        endMs = state.pendingOverlayEndMs
                    )
                }
                _uiState.value = _uiState.value.copy(
                    showTextOverlaySheet = false,
                    textOverlayError = null
                )
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    textOverlayError = e.message ?: "Invalid text overlay"
                )
            }
        }
    }

    fun onSelectTextOverlay(overlayId: String?) {
        _uiState.value = _uiState.value.copy(selectedTextOverlayId = overlayId)
    }

    fun onDuplicateTextOverlay(overlayId: String) {
        viewModelScope.launch {
            pushUndoState()
            try {
                duplicateTextOverlay(overlayId)
            } catch (_: IllegalArgumentException) {
                // overlay disappeared between selection and action; safe to ignore
            }
        }
    }

    fun onDeleteTextOverlay(overlayId: String) {
        viewModelScope.launch {
            pushUndoState()
            deleteTextOverlay(overlayId)
            if (_uiState.value.selectedTextOverlayId == overlayId) {
                _uiState.value = _uiState.value.copy(selectedTextOverlayId = null)
            }
        }
    }

    companion object {
        const val MIN_RANGE_MS = SplitSegmentUseCase.MIN_RANGE_MS
        const val MIN_IMAGE_DURATION_MS = 500L
        const val MAX_IMAGE_DURATION_MS = 30_000L
        const val MIN_FRAME_DIMENSION = 16
        const val MAX_FRAME_DIMENSION = 7680
        const val DEFAULT_OVERLAY_DURATION_MS = 3_000L

        private val DEFAULT_VOICES = listOf(
            Voice("EXAVITQu4vr4xnSDxMaL", "Sarah", null, "en"),
            Voice("TX3LPaxmHKxFdv7VOQHJ", "Liam", null, "en"),
            Voice("pFZP5JQG7iQjIQuC4Bku", "Lily", null, "en"),
            Voice("bIHbv24MWmeRgasZH58o", "Will", null, "en"),
            Voice("default-ko-1", "Jimin", null, "ko"),
            Voice("default-ko-2", "Seoyeon", null, "ko"),
        )
    }
}

enum class FramePreset(val ratioW: Int, val ratioH: Int, val label: String) {
    PORTRAIT_9_16(9, 16, "9:16"),
    SQUARE_1_1(1, 1, "1:1"),
    LANDSCAPE_16_9(16, 9, "16:9"),
    PORTRAIT_4_5(4, 5, "4:5");

    fun toDimensions(longestSide: Int): Pair<Int, Int> {
        val maxRatio = maxOf(ratioW, ratioH)
        val unit = (longestSide.toDouble() / maxRatio).coerceAtLeast(1.0)
        val w = (unit * ratioW).toInt().coerceAtLeast(1)
        val h = (unit * ratioH).toInt().coerceAtLeast(1)
        return w to h
    }
}
