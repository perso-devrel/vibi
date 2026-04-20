package com.example.dubcast.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isExtracting: Boolean = false
)

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
                _uiState.value = InputUiState(
                    validationResult = ValidationResult.Invalid(ValidationError.METADATA_UNREADABLE)
                )
                return@launch
            }

            val result = validateVideo(videoInfo)
            _uiState.value = InputUiState(
                selectedVideo = videoInfo,
                validationResult = result
            )
        }
    }

    fun onContinue() {
        val video = _uiState.value.selectedVideo ?: return
        viewModelScope.launch {
            val projectId = createProjectWithInitialVideoSegment(video)
            _navigateToTimeline.emit(projectId)
        }
    }
}
