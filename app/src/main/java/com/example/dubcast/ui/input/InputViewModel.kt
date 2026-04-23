package com.example.dubcast.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.TargetLanguage
import com.example.dubcast.domain.model.ValidationError
import com.example.dubcast.domain.model.ValidationResult
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.usecase.input.CreateProjectWithInitialVideoSegmentUseCase
import com.example.dubcast.domain.usecase.input.ValidateVideoUseCase
import com.example.dubcast.domain.usecase.input.VideoMetadataExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InputUiState(
    val selectedVideo: VideoInfo? = null,
    val validationResult: ValidationResult? = null,
    val isExtracting: Boolean = false,
    val targetLanguage: TargetLanguage = TargetLanguage.ORIGINAL,
    val enableAutoSubtitles: Boolean = false,
    val enableAutoDubbing: Boolean = false
) {
    val isTranslationLanguage: Boolean
        get() = targetLanguage.code != TargetLanguage.CODE_ORIGINAL
}

@HiltViewModel
class InputViewModel @Inject constructor(
    private val extractor: VideoMetadataExtractor,
    private val validateVideo: ValidateVideoUseCase,
    private val createProjectWithInitialVideoSegment: CreateProjectWithInitialVideoSegmentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(InputUiState())
    val uiState: StateFlow<InputUiState> = _uiState.asStateFlow()

    private val _navigateToTimeline = MutableSharedFlow<String>()
    val navigateToTimeline: SharedFlow<String> = _navigateToTimeline.asSharedFlow()

    fun onVideoPicked(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true)

            val videoInfo = extractor.extract(uri)
            if (videoInfo == null) {
                _uiState.value = _uiState.value.copy(
                    selectedVideo = null,
                    validationResult = ValidationResult.Invalid(ValidationError.METADATA_UNREADABLE),
                    isExtracting = false
                )
                return@launch
            }

            val result = validateVideo(videoInfo)
            _uiState.value = _uiState.value.copy(
                selectedVideo = videoInfo,
                validationResult = result,
                isExtracting = false
            )
        }
    }

    fun onSelectTargetLanguage(language: TargetLanguage) {
        val isOriginal = language.code == TargetLanguage.CODE_ORIGINAL
        _uiState.value = _uiState.value.copy(
            targetLanguage = language,
            // "원본 그대로" 를 고르면 번역 파이프라인이 의미 없으므로 자동 OFF.
            enableAutoSubtitles = if (isOriginal) false else _uiState.value.enableAutoSubtitles,
            enableAutoDubbing = if (isOriginal) false else _uiState.value.enableAutoDubbing
        )
    }

    fun onToggleAutoSubtitles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAutoSubtitles = enabled)
    }

    fun onToggleAutoDubbing(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAutoDubbing = enabled)
    }

    fun onContinue() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        viewModelScope.launch {
            val projectId = createProjectWithInitialVideoSegment(
                videoInfo = video,
                targetLanguageCode = state.targetLanguage.code,
                enableAutoDubbing = state.enableAutoDubbing,
                enableAutoSubtitles = state.enableAutoSubtitles
            )
            _navigateToTimeline.emit(projectId)
        }
    }
}
