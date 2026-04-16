package com.example.dubcast.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.example.dubcast.domain.usecase.subtitle.UndoRedoManager
import com.example.dubcast.domain.usecase.timeline.DeleteDubClipUseCase
import com.example.dubcast.domain.usecase.timeline.MoveDubClipUseCase
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
    val dubClips: List<DubClip> = emptyList(),
    val subtitleClips: List<SubtitleClip> = emptyList(),
    val playbackPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedDubClipId: String? = null,
    val selectedSubtitleClipId: String? = null,
    val voices: List<Voice> = emptyList(),
    val isVoicesLoading: Boolean = false,
    val showDubbingSheet: Boolean = false,
    val showSubtitleSheet: Boolean = false,
    val isSynthesizing: Boolean = false,
    val synthError: String? = null,
    val previewClip: PreviewDubClip? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

data class TimelineSnapshot(
    val dubClips: List<DubClip>,
    val subtitleClips: List<SubtitleClip>
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editProjectRepository: EditProjectRepository,
    private val dubClipRepository: DubClipRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val ttsRepository: TtsRepository,
    private val synthesizeDubClip: SynthesizeDubClipUseCase,
    private val getVoiceList: GetVoiceListUseCase,
    private val moveDubClip: MoveDubClipUseCase,
    private val deleteDubClip: DeleteDubClipUseCase,
    private val addSubtitleClip: AddSubtitleClipUseCase,
    private val deleteSubtitleClip: DeleteSubtitleClipUseCase
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
                        videoHeight = project.videoHeight
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
                subtitleClipRepository.observeClips(projectId)
            ) { dubs, subs -> dubs to subs }
                .collect { (dubs, subs) ->
                    _uiState.value = _uiState.value.copy(
                        dubClips = dubs,
                        subtitleClips = subs
                    )
                }
        }
    }

    private suspend fun pushUndoState() {
        // Read directly from DB to avoid race with async Flow emission
        val dubs = dubClipRepository.observeClips(projectId).first()
        val subs = subtitleClipRepository.observeClips(projectId).first()
        undoRedoManager.pushState(TimelineSnapshot(dubs, subs))
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

    fun onInsertPreviewClip() {
        val preview = _uiState.value.previewClip ?: return
        viewModelScope.launch {
            pushUndoState()
            val clip = DubClip(
                id = java.util.UUID.randomUUID().toString(),
                projectId = projectId,
                text = preview.text,
                voiceId = preview.voiceId,
                voiceName = preview.voiceName,
                audioFilePath = preview.audioFilePath,
                startMs = _uiState.value.playbackPositionMs,
                durationMs = preview.durationMs
            )
            dubClipRepository.addClip(clip)
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
        _uiState.value = _uiState.value.copy(
            selectedDubClipId = clipId,
            selectedSubtitleClipId = null
        )
    }

    fun onSelectSubtitleClip(clipId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedSubtitleClipId = clipId,
            selectedDubClipId = null
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
            state.selectedDubClipId?.let { deleteDubClip(it) }
            state.selectedSubtitleClipId?.let { deleteSubtitleClip(it) }
            _uiState.value = _uiState.value.copy(
                selectedDubClipId = null,
                selectedSubtitleClipId = null
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
    }

    fun onNavigateToExport() {
        viewModelScope.launch {
            _navigateToExport.emit(projectId)
        }
    }
}
