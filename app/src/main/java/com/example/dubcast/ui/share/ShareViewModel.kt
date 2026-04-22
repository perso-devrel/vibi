package com.example.dubcast.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.usecase.share.GallerySaver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareUiState(
    val isSaving: Boolean = false,
    val savedToGallery: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gallerySaver: GallerySaver
) : ViewModel() {

    val outputPath: String = savedStateHandle.get<String>("outputPath") ?: ""

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    fun saveToGallery() {
        if (outputPath.isEmpty()) return
        val displayName = "dubcast_${System.currentTimeMillis()}.mp4"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            gallerySaver.saveVideo(outputPath, displayName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedToGallery = true
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to save"
                    )
                }
            )
        }
    }
}
