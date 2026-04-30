@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dubcast.shared.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dubcast.shared.domain.usecase.share.GallerySaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class ShareUiState(
    val isSaving: Boolean = false,
    val savedToGallery: Boolean = false,
    val error: String? = null
)

class ShareViewModel(
    val outputPath: String,
    private val gallerySaver: GallerySaver
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    fun saveToGallery() {
        if (outputPath.isEmpty()) return
        val displayName = "dubcast_${Clock.System.now().toEpochMilliseconds()}.mp4"

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
