package com.example.dubcast.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.usecase.image.AddImageClipUseCase
import com.example.dubcast.domain.usecase.image.DeleteImageClipUseCase
import com.example.dubcast.domain.usecase.image.UpdateImageClipUseCase
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.UndoRedoManager
import com.example.dubcast.domain.usecase.timeline.DeleteDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.MoveDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.SplitDubTextToSubtitlesUseCase
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
    val videoUri: String = "",
    val videoDurationMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
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
    val pendingTrimEndMs: Long = 0L
) {
    val effectiveTrimEndMs: Long get() = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
}

data class TimelineSnapshot(
    val dubClips: List<DubClip>,
    val subtitleClips: List<SubtitleClip>,
    val imageClips: List<ImageClip>
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editProjectRepository: EditProjectRepository,
    private val dubClipRepository: DubClipRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val imageClipRepository: ImageClipRepository,
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
    private val deleteImageClip: DeleteImageClipUseCase
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _uiState = MutableStateFlow(TimelineUiState(projectId = projectId))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _navigateToExport = MutableSharedFlow<String>()
    val navigateToExport: SharedFlow<String> = _navigateToExport.asSharedFlow()

    private val undoRedoManager = UndoRedoManager<TimelineSnapshot>(maxHistory = 50)

    init {
        loadProject()
        loadVoices()
        observeClips()
    }

    private fun loadProject() {
        viewModelScope.launch {
            editProjectRepository.observeProject(projectId).collect { project ->
                if (project != null) {
                    _uiState.value = _uiState.value.copy(
                        videoUri = project.videoUri,
                        videoDurationMs = project.videoDurationMs,
                        videoWidth = project.videoWidth,
                        videoHeight = project.videoHeight,
                        trimStartMs = project.trimStartMs,
                        trimEndMs = project.trimEndMs
                    )
                }
            }
        }
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

    companion object {
        private val DEFAULT_VOICES = listOf(
            Voice("EXAVITQu4vr4xnSDxMaL", "Sarah", null, "en"),
            Voice("TX3LPaxmHKxFdv7VOQHJ", "Liam", null, "en"),
            Voice("pFZP5JQG7iQjIQuC4Bku", "Lily", null, "en"),
            Voice("bIHbv24MWmeRgasZH58o", "Will", null, "en"),
            Voice("default-ko-1", "Jimin", null, "ko"),
            Voice("default-ko-2", "Seoyeon", null, "ko"),
        )
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
        undoRedoManager.pushState(TimelineSnapshot(dubs, subs, images))
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
            val startMs = state.playbackPositionMs.coerceAtLeast(0L)
            val maxEnd = if (state.videoDurationMs > 0L) state.videoDurationMs else (startMs + defaultDurationMs)
            val endMs = (startMs + defaultDurationMs).coerceAtMost(maxEnd).let {
                if (it <= startMs) startMs + 500L else it
            }
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
        _uiState.value = state.copy(
            isTrimming = true,
            pendingTrimStartMs = state.trimStartMs,
            pendingTrimEndMs = state.effectiveTrimEndMs,
            isPlaying = false
        )
    }

    fun onSetPendingTrimStart(ms: Long) {
        val state = _uiState.value
        val pendingEnd = state.pendingTrimEndMs
        val newStart = ms.coerceIn(0L, pendingEnd - 500L)
        _uiState.value = state.copy(pendingTrimStartMs = newStart)
    }

    fun onSetPendingTrimEnd(ms: Long) {
        val state = _uiState.value
        val newEnd = ms.coerceIn(state.pendingTrimStartMs + 500L, state.videoDurationMs)
        _uiState.value = state.copy(pendingTrimEndMs = newEnd)
    }

    fun onConfirmTrim() {
        val state = _uiState.value
        _uiState.value = state.copy(
            trimStartMs = state.pendingTrimStartMs,
            trimEndMs = state.pendingTrimEndMs,
            isTrimming = false,
            isVideoSelected = false
        )
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            editProjectRepository.updateProject(
                project.copy(
                    trimStartMs = state.pendingTrimStartMs,
                    trimEndMs = state.pendingTrimEndMs,
                    updatedAt = System.currentTimeMillis()
                )
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
}
