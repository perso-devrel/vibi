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
import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.BgmClipRepository
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.repository.MixStatus
import com.example.dubcast.domain.repository.SegmentRepository
import com.example.dubcast.domain.repository.SeparationStatus
import com.example.dubcast.domain.repository.StemSelection
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TextOverlayRepository
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.model.SeparationMediaType
import com.example.dubcast.domain.usecase.image.AddImageClipUseCase
import com.example.dubcast.domain.usecase.image.DeleteImageClipUseCase
import com.example.dubcast.domain.usecase.image.UpdateImageClipUseCase
import com.example.dubcast.domain.usecase.bgm.AddBgmClipUseCase
import com.example.dubcast.domain.usecase.bgm.DeleteBgmClipUseCase
import com.example.dubcast.domain.usecase.bgm.UpdateBgmClipUseCase
import com.example.dubcast.domain.usecase.input.AudioMetadataExtractor
import com.example.dubcast.domain.usecase.input.ImageMetadataExtractor
import com.example.dubcast.domain.usecase.input.SetProjectFrameUseCase
import com.example.dubcast.domain.usecase.input.VideoMetadataExtractor
import com.example.dubcast.domain.usecase.separation.ApplyMixAsBgmUseCase
import com.example.dubcast.domain.usecase.separation.PollMixUseCase
import com.example.dubcast.domain.usecase.separation.PollSeparationUseCase
import com.example.dubcast.domain.usecase.separation.RequestStemMixUseCase
import com.example.dubcast.domain.usecase.separation.StartAudioSeparationUseCase
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val textOverlayError: String? = null,
    val bgmClips: List<BgmClip> = emptyList(),
    val selectedBgmClipId: String? = null,
    val isAddingBgm: Boolean = false,
    val bgmError: String? = null,
    val audioSeparation: AudioSeparationUiState? = null
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
    val textOverlays: List<TextOverlay> = emptyList(),
    val bgmClips: List<BgmClip> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX
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
    private val bgmClipRepository: BgmClipRepository,
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
    private val addBgmClip: AddBgmClipUseCase,
    private val updateBgmClip: UpdateBgmClipUseCase,
    private val deleteBgmClip: DeleteBgmClipUseCase,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    private val imageMetadataExtractor: ImageMetadataExtractor,
    private val audioMetadataExtractor: AudioMetadataExtractor,
    private val startAudioSeparation: StartAudioSeparationUseCase,
    private val pollSeparation: PollSeparationUseCase,
    private val requestStemMix: RequestStemMixUseCase,
    private val pollMix: PollMixUseCase,
    private val applyMixAsBgm: ApplyMixAsBgmUseCase
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _uiState = MutableStateFlow(TimelineUiState(projectId = projectId))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _navigateToExport = MutableSharedFlow<String>()
    val navigateToExport: SharedFlow<String> = _navigateToExport.asSharedFlow()

    private val undoRedoManager = UndoRedoManager<TimelineSnapshot>(maxHistory = 50)
    private var hasSeededUndoSnapshot = false

    init {
        loadSegments()
        loadVoices()
        observeClips()
        observeProject()
        observeTextOverlays()
        observeBgmClips()
    }

    private fun observeBgmClips() {
        viewModelScope.launch {
            bgmClipRepository.observeClips(projectId).collect { clips ->
                _uiState.value = _uiState.value.copy(bgmClips = clips)
            }
        }
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
                    if (!hasSeededUndoSnapshot) {
                        hasSeededUndoSnapshot = true
                        pushUndoState()
                    }
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
                // Only keep an existing selection if it still references a real
                // segment. No fallback to `first?.id` — the screen should open
                // with nothing selected so the inline edit panel stays hidden
                // until the user actually taps a segment.
                val selectedId = currentSelectedId?.takeIf { id -> segments.any { it.id == id } }
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
        // Read every repository concurrently — sequential .first() calls
        // serialised on dispatcher hops added measurable latency on every
        // mutating handler (which is hot path during pinch/drag).
        val (segs, dubs, subs, images, texts, bgms, project) = coroutineScope {
            val segsAsync = async { segmentRepository.getByProjectId(projectId) }
            val dubsAsync = async { dubClipRepository.observeClips(projectId).first() }
            val subsAsync = async { subtitleClipRepository.observeClips(projectId).first() }
            val imgsAsync = async { imageClipRepository.observeClips(projectId).first() }
            val textsAsync = async { textOverlayRepository.observeOverlays(projectId).first() }
            val bgmsAsync = async { bgmClipRepository.observeClips(projectId).first() }
            val projectAsync = async { editProjectRepository.getProject(projectId) }
            UndoSnapshotInputs(
                segs = segsAsync.await(),
                dubs = dubsAsync.await(),
                subs = subsAsync.await(),
                images = imgsAsync.await(),
                texts = textsAsync.await(),
                bgms = bgmsAsync.await(),
                project = projectAsync.await()
            )
        }
        undoRedoManager.pushState(
            TimelineSnapshot(
                segments = segs,
                dubClips = dubs,
                subtitleClips = subs,
                imageClips = images,
                textOverlays = texts,
                bgmClips = bgms,
                frameWidth = project?.frameWidth ?: 0,
                frameHeight = project?.frameHeight ?: 0,
                backgroundColorHex = project?.backgroundColorHex
                    ?: EditProject.DEFAULT_BACKGROUND_COLOR_HEX
            )
        )
        updateUndoRedoState()
    }

    /** Destructuring shim for the parallel repository fan-out in [pushUndoState]. */
    private data class UndoSnapshotInputs(
        val segs: List<Segment>,
        val dubs: List<DubClip>,
        val subs: List<SubtitleClip>,
        val images: List<ImageClip>,
        val texts: List<TextOverlay>,
        val bgms: List<BgmClip>,
        val project: EditProject?
    )

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
            pushUndoState()
        }
    }

    fun onAddSubtitle(text: String, startMs: Long, endMs: Long, position: SubtitlePosition) {
        viewModelScope.launch {
            addSubtitleClip(projectId, text, startMs, endMs, position)
            _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
            pushUndoState()
        }
    }

    fun onMoveDubClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            moveDubClip(clip, newStartMs, _uiState.value.videoDurationMs)
            pushUndoState()
        }
    }

    /**
     * Single-selection model: at any moment at most one of segment / dub /
     * subtitle / image / text-overlay / bgm may be selected. This helper
     * applies a tap-toggle on the chosen target while clearing every other
     * selected*Id, so each handler is a one-line wrapper.
     */
    private enum class SelectionTarget {
        Segment, Dub, Subtitle, Image, TextOverlay, Bgm
    }

    private fun selectExclusively(target: SelectionTarget, id: String?) {
        val state = _uiState.value
        // Tap-toggle: tapping the already-selected element clears it.
        val current = when (target) {
            SelectionTarget.Segment -> state.selectedSegmentId
            SelectionTarget.Dub -> state.selectedDubClipId
            SelectionTarget.Subtitle -> state.selectedSubtitleClipId
            SelectionTarget.Image -> state.selectedImageClipId
            SelectionTarget.TextOverlay -> state.selectedTextOverlayId
            SelectionTarget.Bgm -> state.selectedBgmClipId
        }
        val next = if (id != null && id == current) null else id
        _uiState.value = state.copy(
            selectedSegmentId = if (target == SelectionTarget.Segment) next else null,
            selectedDubClipId = if (target == SelectionTarget.Dub) next else null,
            selectedSubtitleClipId = if (target == SelectionTarget.Subtitle) next else null,
            selectedImageClipId = if (target == SelectionTarget.Image) next else null,
            selectedTextOverlayId = if (target == SelectionTarget.TextOverlay) next else null,
            selectedBgmClipId = if (target == SelectionTarget.Bgm) next else null,
            isVideoSelected = false,
            showVideoVolumeSlider = false,
            showDubVolumeSlider = false
        )
    }

    fun onSelectDubClip(clipId: String?) = selectExclusively(SelectionTarget.Dub, clipId)

    fun onSelectSubtitleClip(clipId: String?) = selectExclusively(SelectionTarget.Subtitle, clipId)

    fun onSelectImageClip(clipId: String?) = selectExclusively(SelectionTarget.Image, clipId)

    fun onUpdateDubClipVolume(clipId: String, volume: Float) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            val clamped = volume.coerceIn(0f, 2f)
            if (clamped == clip.volume) return@launch
            dubClipRepository.updateClip(clip.copy(volume = clamped))
            pushUndoState()
        }
    }

    fun onDeleteSelectedClip() {
        viewModelScope.launch {
            val state = _uiState.value
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
            pushUndoState()
        }
    }

    fun onInsertImage(uri: String, defaultDurationMs: Long = 3000L) {
        viewModelScope.launch {
            val state = _uiState.value
            val videoDurationMs = state.videoDurationMs
            val maxStart = if (videoDurationMs > 0L) (videoDurationMs - 500L).coerceAtLeast(0L) else Long.MAX_VALUE
            // Avoid stacking new sticker on top of an existing one at the same
            // start time — shift right in 250ms increments until a free spot.
            val taken = state.imageClips.map { it.startMs }.toSet()
            var startMs = state.playbackPositionMs.coerceIn(0L, maxStart)
            while (startMs in taken && startMs < maxStart) startMs = (startMs + 250L).coerceAtMost(maxStart)
            val maxEnd = if (videoDurationMs > 0L) videoDurationMs else (startMs + defaultDurationMs)
            val endMs = (startMs + defaultDurationMs)
                .coerceAtMost(maxEnd)
                .coerceAtLeast(startMs + 500L)
            addImageClip(
                projectId = projectId,
                imageUri = uri,
                startMs = startMs,
                endMs = endMs,
                lane = pickFreeOverlayLane(startMs, endMs)
            )
            pushUndoState()
        }
    }

    fun onMoveImageClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.imageClips.find { it.id == clipId } ?: return@launch
            val duration = clip.endMs - clip.startMs
            val videoDuration = _uiState.value.videoDurationMs
            val coercedStart = newStartMs.coerceAtLeast(0L).let {
                if (videoDuration > 0L) it.coerceAtMost((videoDuration - duration).coerceAtLeast(0L)) else it
            }
            updateImageClip(clip.copy(startMs = coercedStart, endMs = coercedStart + duration))
            pushUndoState()
        }
    }

    /**
     * Pick the lowest lane number free of time-overlap with both image clips
     * AND text overlays — they share lanes on the merged overlay track.
     */
    private fun pickFreeOverlayLane(startMs: Long, endMs: Long): Int {
        val state = _uiState.value
        // Treat both lists as one virtual collection of (lane, start, end)
        // tuples and reuse the shared lane-packing helper.
        data class LaneItem(val lane: Int, val start: Long, val end: Long)
        val combined = state.imageClips.map { LaneItem(it.lane, it.startMs, it.endMs) } +
            state.textOverlays.map { LaneItem(it.lane, it.startMs, it.endMs) }
        return com.example.dubcast.domain.util.pickLowestFreeLane(
            existing = combined,
            startMs = startMs,
            endMs = endMs,
            laneOf = { it.lane },
            startOf = { it.start },
            endOf = { it.end }
        )
    }

    fun onChangeImageClipLane(clipId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val clip = imageClipRepository.getClip(clipId) ?: return@launch
            val newLane = (clip.lane + delta).coerceAtLeast(0)
            if (newLane == clip.lane) return@launch
            updateImageClip(clip.copy(lane = newLane))
            pushUndoState()
        }
    }

    fun onDuplicateImageClip(clipId: String) {
        viewModelScope.launch {
            val clip = imageClipRepository.getClip(clipId) ?: return@launch
            // Place the duplicate at the same time position so it sits directly
            // beside the source on the timeline. AddImageClipUseCase auto-picks
            // the lowest free lane that doesn't time-conflict, which pushes the
            // copy to the row right below the original.
            addImageClip(
                projectId = projectId,
                imageUri = clip.imageUri,
                startMs = clip.startMs,
                endMs = clip.endMs,
                xPct = clip.xPct,
                yPct = clip.yPct,
                widthPct = clip.widthPct,
                heightPct = clip.heightPct,
                lane = pickFreeOverlayLane(clip.startMs, clip.endMs)
            )
            pushUndoState()
        }
    }

    fun onResizeImageClipDuration(clipId: String, newEndMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.imageClips.find { it.id == clipId } ?: return@launch
            val minEnd = clip.startMs + 500L
            val videoDuration = _uiState.value.videoDurationMs
            val coercedEnd = newEndMs.coerceAtLeast(minEnd).let {
                if (videoDuration > 0L) it.coerceAtMost(videoDuration) else it
            }
            updateImageClip(clip.copy(endMs = coercedEnd))
            pushUndoState()
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
            pushUndoState()
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
            pushUndoState()
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
        bgmClipRepository.deleteAllByProjectId(projectId)
        for (bgm in snapshot.bgmClips) {
            bgmClipRepository.addClip(bgm)
        }
        if (snapshot.frameWidth > 0 && snapshot.frameHeight > 0) {
            // Frame is restored directly via the repository (not SetProjectFrameUseCase)
            // because snapshot values already passed validation when first applied;
            // re-validating could reject a legitimate prior state if validation rules
            // change in the future.
            val current = editProjectRepository.getProject(projectId)
            if (current != null) {
                editProjectRepository.updateProject(
                    current.copy(
                        frameWidth = snapshot.frameWidth,
                        frameHeight = snapshot.frameHeight,
                        backgroundColorHex = snapshot.backgroundColorHex,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
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
            pushUndoState()
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
            pushUndoState()
        }
    }

    fun onAppendImageSegment(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAppendSheet = false, isPlaying = false)
            val info = imageMetadataExtractor.extract(uri) ?: return@launch
            addImageSegment(projectId = projectId, imageInfo = info)
            pushUndoState()
        }
    }

    fun onSelectSegment(segmentId: String?) {
        selectExclusively(SelectionTarget.Segment, segmentId)
        // Segment selection additionally seeds the trim fields used by the
        // trim-mode overlay; the helper handles only the selected*Id fields.
        val selected = _uiState.value.segments.firstOrNull { it.id == _uiState.value.selectedSegmentId }
        _uiState.value = _uiState.value.copy(
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
            pushUndoState()
        }
    }

    fun onUpdateImageSegmentDuration(segmentId: String, durationMs: Long) {
        viewModelScope.launch {
            updateImageSegmentDuration(segmentId, durationMs)
            pushUndoState()
        }
    }

    fun onResizeImageSegmentByDrag(segmentId: String, requestedDurationMs: Long) {
        viewModelScope.launch {
            val seg = _uiState.value.segments.firstOrNull { it.id == segmentId } ?: return@launch
            if (seg.type != SegmentType.IMAGE) return@launch
            val clamped = requestedDurationMs.coerceIn(MIN_IMAGE_DURATION_MS, MAX_IMAGE_DURATION_MS)
            if (clamped == seg.durationMs) return@launch
            updateImageSegmentDuration(segmentId, clamped)
            pushUndoState()
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
            pushUndoState()
        }
    }

    fun onEnterRangeMode(segmentId: String) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        // Default range begins at the playhead so a tap on a specific spot
        // opens the 1s window right there (not at the leftmost segment edge).
        val total = state.videoDurationMs.coerceAtLeast(1L)
        val defaultStart = state.playbackPositionMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        val defaultEnd = (defaultStart + 1000L).coerceAtMost(total)
        _uiState.value = state.copy(
            isRangeSelecting = true,
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            pendingRangeStartMs = defaultStart,
            pendingRangeEndMs = defaultEnd,
            showRangeActionSheet = false,
            pendingRangeVolume = seg.volumeScale,
            pendingRangeSpeed = seg.speedScale,
            isPlaying = false
        )
    }

    fun onSetPendingRangeStart(globalMs: Long) {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(0L)
        val upper = (state.pendingRangeEndMs - MIN_RANGE_MS).coerceAtLeast(0L)
        val clamped = globalMs.coerceIn(0L, upper.coerceAtMost(total))
        _uiState.value = state.copy(pendingRangeStartMs = clamped)
    }

    fun onSetPendingRangeEnd(globalMs: Long) {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(0L)
        val lower = (state.pendingRangeStartMs + MIN_RANGE_MS).coerceAtMost(total)
        val clamped = globalMs.coerceIn(lower.coerceAtLeast(0L), total)
        _uiState.value = state.copy(pendingRangeEndMs = clamped)
    }

    /**
     * Slice the global range [globalStart, globalEnd] into per-segment local
     * ranges. Returns segments that overlap, sorted by `order`. Skips IMAGE
     * and segments where the overlap is below MIN_RANGE_MS.
     */
    private data class SegmentRangeSlice(
        val segmentId: String,
        val order: Int,
        val localStart: Long,
        val localEnd: Long
    )

    private fun sliceGlobalRange(globalStart: Long, globalEnd: Long): List<SegmentRangeSlice> {
        val out = mutableListOf<SegmentRangeSlice>()
        var acc = 0L
        for (seg in _uiState.value.segments) {
            val segDur = seg.effectiveDurationMs
            val segGlobalStart = acc
            val segGlobalEnd = acc + segDur
            acc += segDur
            if (seg.type != SegmentType.VIDEO) continue
            val overlapStart = maxOf(segGlobalStart, globalStart)
            val overlapEnd = minOf(segGlobalEnd, globalEnd)
            if (overlapEnd - overlapStart < MIN_RANGE_MS) continue
            val localStart = seg.trimStartMs + (overlapStart - segGlobalStart)
            val localEnd = seg.trimStartMs + (overlapEnd - segGlobalStart)
            out += SegmentRangeSlice(seg.id, seg.order, localStart, localEnd)
        }
        return out
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
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        // Hide UI immediately for instant feedback; the actual split work
        // continues in the background.
        resetRangeMode()
        viewModelScope.launch {
            slices.forEach { s -> duplicateSegmentRange(s.segmentId, s.localStart, s.localEnd) }
            pushUndoState()
        }
    }

    fun onDeleteRange() {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        resetRangeMode()
        viewModelScope.launch {
            slices.forEach { s -> removeSegmentRange(s.segmentId, s.localStart, s.localEnd) }
            pushUndoState()
        }
    }

    fun onApplyRangeVolume(value: Float) {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        resetRangeMode()
        viewModelScope.launch {
            slices.forEach { s ->
                val r = splitSegment(s.segmentId, s.localStart, s.localEnd)
                updateSegmentVolume(r.middle.id, value)
            }
            pushUndoState()
        }
    }

    fun onApplyRangeSpeed(value: Float) {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        resetRangeMode()
        viewModelScope.launch {
            slices.forEach { s ->
                val r = splitSegment(s.segmentId, s.localStart, s.localEnd)
                updateSegmentSpeed(r.middle.id, value)
            }
            pushUndoState()
        }
    }

    private fun resetRangeMode() {
        // Also clear segment selection so the inline action bar disappears
        // alongside the range handles (apply means "I'm done editing").
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
            selectedSegmentId = null
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
                setProjectFrame(projectId, width, height, color)
                _uiState.value = _uiState.value.copy(showFrameSheet = false, frameError = null)
                pushUndoState()
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
        val playhead = state.playbackPositionMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        // Avoid stacking on top of an existing overlay at the same start time.
        // Shift the new clip rightward in 250ms steps until a free spot opens
        // (or until we run out of timeline).
        val taken = state.textOverlays.map { it.startMs }.toSet()
        var start = playhead
        while (start in taken && start < total - 1L) start += 250L
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
                if (state.editingTextOverlayId == null) {
                    addPendingTextOverlay(state, text)
                } else {
                    updatePendingTextOverlay(state, state.editingTextOverlayId, text)
                }
                _uiState.value = _uiState.value.copy(
                    showTextOverlaySheet = false,
                    textOverlayError = null
                )
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    textOverlayError = e.message ?: "Invalid text overlay"
                )
            }
        }
    }

    private suspend fun addPendingTextOverlay(state: TimelineUiState, text: String) {
        addTextOverlay(
            projectId = projectId,
            text = text,
            startMs = state.pendingOverlayStartMs,
            endMs = state.pendingOverlayEndMs,
            fontFamily = state.pendingOverlayFontFamily,
            fontSizeSp = state.pendingOverlayFontSizeSp,
            colorHex = state.pendingOverlayColorHex,
            lane = pickFreeOverlayLane(state.pendingOverlayStartMs, state.pendingOverlayEndMs)
        )
    }

    private suspend fun updatePendingTextOverlay(
        state: TimelineUiState,
        editId: String,
        text: String
    ) {
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

    fun onSelectTextOverlay(overlayId: String?) =
        selectExclusively(SelectionTarget.TextOverlay, overlayId)

    fun onUpdateTextOverlayScreenPosition(overlayId: String, xPct: Float, yPct: Float) {
        viewModelScope.launch {
            try {
                updateTextOverlay(overlayId, xPct = xPct, yPct = yPct)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onUpdateTextOverlayFontSize(overlayId: String, fontSizeSp: Float) {
        viewModelScope.launch {
            try {
                updateTextOverlay(overlayId, fontSizeSp = fontSizeSp)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onMoveTextOverlay(overlayId: String, newStartMs: Long) {
        viewModelScope.launch {
            val overlay = _uiState.value.textOverlays.firstOrNull { it.id == overlayId } ?: return@launch
            val duration = overlay.endMs - overlay.startMs
            val total = _uiState.value.videoDurationMs
            val maxStart = (total - duration).coerceAtLeast(0L)
            val coercedStart = newStartMs.coerceIn(0L, maxStart)
            try {
                updateTextOverlay(
                    overlayId,
                    startMs = coercedStart,
                    endMs = coercedStart + duration
                )
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onResizeTextOverlay(overlayId: String, newEndMs: Long) {
        viewModelScope.launch {
            try {
                updateTextOverlay(overlayId, endMs = newEndMs)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onDuplicateTextOverlay(overlayId: String) {
        viewModelScope.launch {
            val src = textOverlayRepository.getOverlay(overlayId) ?: return@launch
            // Place duplicate at the same time position; AddTextOverlayUseCase
            // auto-picks the lowest free lane so the copy lands directly below.
            try {
                addTextOverlay(
                    projectId = projectId,
                    text = src.text,
                    startMs = src.startMs,
                    endMs = src.endMs,
                    fontFamily = src.fontFamily,
                    fontSizeSp = src.fontSizeSp,
                    colorHex = src.colorHex,
                    xPct = src.xPct,
                    yPct = src.yPct,
                    lane = pickFreeOverlayLane(src.startMs, src.endMs)
                )
                pushUndoState()
            } catch (_: IllegalArgumentException) {
                // overlay disappeared between selection and action; safe to ignore
            }
        }
    }

    fun onChangeTextOverlayLane(overlayId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val overlay = textOverlayRepository.getOverlay(overlayId) ?: return@launch
            val newLane = (overlay.lane + delta).coerceAtLeast(0)
            if (newLane == overlay.lane) return@launch
            try {
                updateTextOverlay(overlayId, lane = newLane)
                pushUndoState()
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    fun onDeleteTextOverlay(overlayId: String) {
        viewModelScope.launch {
            deleteTextOverlay(overlayId)
            if (_uiState.value.selectedTextOverlayId == overlayId) {
                _uiState.value = _uiState.value.copy(selectedTextOverlayId = null)
            }
            pushUndoState()
        }
    }

    fun onPickBgmAudio(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingBgm = true, bgmError = null)
            try {
                val info = audioMetadataExtractor.extract(uri)
                if (info == null || info.durationMs <= 0L) {
                    _uiState.value = _uiState.value.copy(
                        isAddingBgm = false,
                        bgmError = "오디오 메타데이터를 읽지 못함"
                    )
                    return@launch
                }
                val startMs = _uiState.value.playbackPositionMs
                addBgmClip(
                    projectId = projectId,
                    sourceUri = uri,
                    sourceDurationMs = info.durationMs,
                    startMs = startMs,
                    volumeScale = 1.0f
                )
                _uiState.value = _uiState.value.copy(isAddingBgm = false)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    isAddingBgm = false,
                    bgmError = e.message ?: "BGM을 추가하지 못함"
                )
            }
        }
    }

    fun onSelectBgmClip(clipId: String?) = selectExclusively(SelectionTarget.Bgm, clipId)

    fun onUpdateBgmStartMs(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, startMs = newStartMs.coerceAtLeast(0L))
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    fun onUpdateBgmVolume(clipId: String, newVolume: Float) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, volumeScale = newVolume)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    fun onDeleteBgmClip(clipId: String) {
        viewModelScope.launch {
            deleteBgmClip(clipId)
            if (_uiState.value.selectedBgmClipId == clipId) {
                _uiState.value = _uiState.value.copy(selectedBgmClipId = null)
            }
            pushUndoState()
        }
    }

    // --- Audio separation (per-segment voice/background split) ---

    fun onShowAudioSeparationSheet(segmentId: String) {
        val seg = _uiState.value.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        _uiState.value = _uiState.value.copy(
            audioSeparation = AudioSeparationUiState(segmentId = segmentId),
            isPlaying = false
        )
    }

    fun onDismissAudioSeparationSheet() {
        _uiState.value = _uiState.value.copy(audioSeparation = null)
    }

    fun onUpdateSeparationSpeakers(count: Int) {
        val sep = _uiState.value.audioSeparation ?: return
        _uiState.value = _uiState.value.copy(
            audioSeparation = sep.copy(numberOfSpeakers = count.coerceIn(1, 10))
        )
    }

    fun onStartSeparation() {
        val state = _uiState.value
        val sep = state.audioSeparation ?: return
        val segment = state.segments.firstOrNull { it.id == sep.segmentId } ?: return
        val hasTrim = segment.trimStartMs > 0L || segment.trimEndMs > 0L
        viewModelScope.launch {
            updateSeparation { it.copy(step = AudioSeparationStep.PROCESSING, errorMessage = null) }
            val startResult = startAudioSeparation(
                sourceUri = segment.sourceUri,
                mediaType = SeparationMediaType.VIDEO,
                numberOfSpeakers = sep.numberOfSpeakers,
                trimStartMs = if (hasTrim) segment.trimStartMs else null,
                trimEndMs = if (hasTrim) segment.effectiveTrimEndMs else null
            )
            val jobId = startResult.getOrElse { err ->
                updateSeparation {
                    it.copy(step = AudioSeparationStep.FAILED, errorMessage = err.message)
                }
                return@launch
            }
            updateSeparation { it.copy(jobId = jobId) }
            pollSeparationFlow(jobId)
        }
    }

    private suspend fun pollSeparationFlow(jobId: String) {
        try {
            pollSeparation(jobId).collect { status ->
                when (status) {
                    is SeparationStatus.Processing -> updateSeparation {
                        it.copy(progress = status.progress, progressReason = status.progressReason)
                    }
                    is SeparationStatus.Ready -> updateSeparation {
                        val defaults = status.stems.associate { stem ->
                            stem.stemId to StemSelectionUi(stem.stemId, selected = false, volume = 1.0f)
                        }
                        it.copy(
                            step = AudioSeparationStep.PICK_STEMS,
                            progress = 100,
                            progressReason = null,
                            stems = status.stems,
                            selections = defaults
                        )
                    }
                    is SeparationStatus.Failed -> updateSeparation {
                        it.copy(
                            step = AudioSeparationStep.FAILED,
                            errorMessage = status.progressReason ?: "분리에 실패했습니다"
                        )
                    }
                    is SeparationStatus.Consumed -> updateSeparation {
                        it.copy(
                            step = AudioSeparationStep.FAILED,
                            errorMessage = "이 작업은 이미 합성에 사용되어 더 이상 사용할 수 없습니다"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            updateSeparation { it.copy(step = AudioSeparationStep.FAILED, errorMessage = e.message) }
        }
    }

    fun onToggleStemSelection(stemId: String) {
        val sep = _uiState.value.audioSeparation ?: return
        val current = sep.selections[stemId] ?: return
        val next = sep.selections + (stemId to current.copy(selected = !current.selected))
        updateSeparation { it.copy(selections = next) }
    }

    fun onUpdateStemVolume(stemId: String, volume: Float) {
        val sep = _uiState.value.audioSeparation ?: return
        val current = sep.selections[stemId] ?: return
        val clamped = volume.coerceIn(0f, 2f)
        val next = sep.selections + (stemId to current.copy(volume = clamped))
        updateSeparation { it.copy(selections = next) }
    }

    fun onToggleMuteOriginalSegmentAudio() {
        updateSeparation { it.copy(muteOriginalSegmentAudio = !it.muteOriginalSegmentAudio) }
    }

    fun onConfirmStemMix() {
        val state = _uiState.value
        val sep = state.audioSeparation ?: return
        val jobId = sep.jobId ?: return
        val segment = state.segments.firstOrNull { it.id == sep.segmentId }
        val selections = sep.selections.values
            .filter { it.selected }
            .map { StemSelection(it.stemId, it.volume) }
        if (selections.isEmpty()) return
        viewModelScope.launch {
            updateSeparation { it.copy(step = AudioSeparationStep.MIXING, mixProgress = 0) }
            val mixIdResult = requestStemMix(jobId, selections)
            val mixJobId = mixIdResult.getOrElse { err ->
                updateSeparation {
                    it.copy(step = AudioSeparationStep.FAILED, errorMessage = err.message)
                }
                return@launch
            }
            updateSeparation { it.copy(mixJobId = mixJobId) }
            try {
                pollMix(mixJobId).collect { status ->
                    when (status) {
                        is MixStatus.Processing -> updateSeparation {
                            it.copy(mixProgress = status.progress)
                        }
                        is MixStatus.Completed -> {
                            // Anchor the mix to the segment's global offset so the
                            // resulting BGM clip starts at the same moment as the
                            // source video segment. The BGM lane plays on top of
                            // the segment's original audio; muting that audio is
                            // a follow-up UX choice handled elsewhere.
                            val segStart = segment?.let { s -> segmentStartOffsetMs(state.segments, s.id) } ?: 0L
                            applyMixAsBgm(
                                projectId = projectId,
                                mixJobId = mixJobId,
                                downloadUrl = status.downloadUrl,
                                startMs = segStart
                            ).fold(
                                onSuccess = {
                                    if (sep.muteOriginalSegmentAudio && segment != null) {
                                        updateSegmentVolume(segment.id, 0f)
                                    }
                                    updateSeparation { it.copy(step = AudioSeparationStep.DONE) }
                                    // Audio separation is a paid one-way commit: undoing past
                                    // this point would strand the BGM clip and invite re-runs
                                    // that re-charge BFF credits. Freeze everything up to here
                                    // as the new baseline.
                                    undoRedoManager.clear()
                                    pushUndoState()
                                },
                                onFailure = { err ->
                                    updateSeparation {
                                        it.copy(
                                            step = AudioSeparationStep.FAILED,
                                            errorMessage = err.message
                                        )
                                    }
                                }
                            )
                        }
                        is MixStatus.Failed -> updateSeparation {
                            it.copy(step = AudioSeparationStep.FAILED, errorMessage = "합성 실패")
                        }
                    }
                }
            } catch (e: Exception) {
                updateSeparation {
                    it.copy(step = AudioSeparationStep.FAILED, errorMessage = e.message)
                }
            }
        }
    }

    private fun updateSeparation(transform: (AudioSeparationUiState) -> AudioSeparationUiState) {
        val current = _uiState.value.audioSeparation ?: return
        _uiState.value = _uiState.value.copy(audioSeparation = transform(current))
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
