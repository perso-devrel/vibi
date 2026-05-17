@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.vibi.shared.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.domain.usecase.share.GallerySaver
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

data class ShareUiState(
    val isSaving: Boolean = false,
    val savedToGallery: Boolean = false,
    val isSharing: Boolean = false,
    val error: String? = null
)

class ShareViewModel(
    val outputPath: String,
    private val gallerySaver: GallerySaver,
    private val shareSheetLauncher: ShareSheetLauncher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    fun saveToGallery() {
        if (outputPath.isEmpty()) return
        val displayName = "vibi_${Clock.System.now().toEpochMilliseconds()}.mp4"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            gallerySaver.saveVideo(outputPath, displayName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedToGallery = true
                    )
                },
                onFailure = { _ ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Failed to save"
                    )
                }
            )
        }
    }

    fun shareVideo() {
        if (outputPath.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSharing = true, error = null)

            shareSheetLauncher.shareVideo(
                sourcePath = outputPath,
                mimeType = "video/mp4",
                title = "vibi"
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSharing = false)
                },
                onFailure = { _ ->
                    _uiState.value = _uiState.value.copy(
                        isSharing = false,
                        error = "Failed to share"
                    )
                }
            )
        }
    }
}
