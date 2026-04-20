package com.example.dubcast.ui.export

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.usecase.export.ExportWithDubbingUseCase
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ExportMode {
    ORIGINAL_ONLY,
    WITH_TRANSLATION
}

enum class VoiceLanguage(val label: String) {
    KEEP_ORIGINAL("Keep original"),
    DUBBING_LANGUAGE("Use dub language")
}

data class TargetLanguage(val code: String, val label: String)

val AVAILABLE_LANGUAGES = listOf(
    TargetLanguage("ko", "한국어"),
    TargetLanguage("en", "English"),
    TargetLanguage("ja", "日本語"),
    TargetLanguage("zh", "中文"),
    TargetLanguage("es", "Español"),
    TargetLanguage("fr", "Français"),
    TargetLanguage("de", "Deutsch")
)

data class ExportUiState(
    val exportMode: ExportMode = ExportMode.ORIGINAL_ONLY,
    val targetLanguage: TargetLanguage = AVAILABLE_LANGUAGES[1],
    val enableDubbing: Boolean = true,
    val enableLipSync: Boolean = false,
    val enableAutoSubtitles: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.KEEP_ORIGINAL,
    val videoUri: String = "",
    val dubClips: List<DubClip> = emptyList(),
    val isExporting: Boolean = false,
    val progressPercent: Int = 0,
    val statusMessage: String? = null,
    val outputPath: String? = null,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val exportWithDubbingUseCase: ExportWithDubbingUseCase,
    private val editProjectRepository: EditProjectRepository,
    private val dubClipRepository: DubClipRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val imageClipRepository: ImageClipRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        loadPreviewData()
    }

    private fun loadPreviewData() {
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            _uiState.value = _uiState.value.copy(videoUri = project.videoUri)

            dubClipRepository.observeClips(projectId).collect { clips ->
                _uiState.value = _uiState.value.copy(dubClips = clips)
            }
        }
    }

    fun onSelectExportMode(mode: ExportMode) {
        _uiState.value = _uiState.value.copy(exportMode = mode)
    }

    fun onSelectTargetLanguage(language: TargetLanguage) {
        _uiState.value = _uiState.value.copy(targetLanguage = language)
    }

    fun onToggleDubbing(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            enableDubbing = enabled,
            enableLipSync = if (!enabled) false else _uiState.value.enableLipSync
        )
    }

    fun onToggleLipSync(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableLipSync = enabled)
    }

    fun onToggleAutoSubtitles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAutoSubtitles = enabled)
    }

    fun onSelectVoiceLanguage(language: VoiceLanguage) {
        _uiState.value = _uiState.value.copy(voiceLanguage = language)
    }

    fun startExport() {
        if (projectId.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "No project ID provided")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null, statusMessage = "Getting ready...")

            try {
                val project = editProjectRepository.getProject(projectId)
                    ?: throw IllegalStateException("Project not found: $projectId")

                val options = _uiState.value
                val isTranslation = options.exportMode == ExportMode.WITH_TRANSLATION

                _uiState.value = _uiState.value.copy(statusMessage = "Mixing dubs...")
                val dubClips = dubClipRepository.observeClips(projectId).first()

                val subtitleClips = if (isTranslation && options.enableAutoSubtitles) {
                    _uiState.value = _uiState.value.copy(statusMessage = "Making subs...")
                    subtitleClipRepository.observeClips(projectId).first()
                } else emptyList()

                if (isTranslation && options.enableLipSync && dubClips.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(statusMessage = "Syncing lips...")
                    // TODO: Lip sync processing
                }

                _uiState.value = _uiState.value.copy(statusMessage = "Rendering...")

                val cacheDir = context.cacheDir

                // Copy content:// URI to a local file for server upload
                val videoInputPath = if (project.videoUri.startsWith("content://")) {
                    val tempVideo = File(cacheDir, "input_video.mp4")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(Uri.parse(project.videoUri))?.use { input ->
                            tempVideo.outputStream().use { output -> input.copyTo(output) }
                        } ?: throw IllegalStateException("Cannot open video URI")
                    }
                    tempVideo.absolutePath
                } else {
                    project.videoUri
                }

                val outputPath = File(cacheDir, "export_${System.currentTimeMillis()}.mp4").absolutePath

                val assFilePath = if (subtitleClips.isNotEmpty()) {
                    File(cacheDir, "subtitles_${projectId}.ass").absolutePath
                } else null

                val fontDir = if (subtitleClips.isNotEmpty()) {
                    File(cacheDir, "fonts").apply { mkdirs() }.absolutePath.also {
                        copyFontFromAssets(it)
                    }
                } else null

                val imageClips = imageClipRepository.observeClips(projectId).first()

                val result = exportWithDubbingUseCase.execute(
                    inputVideoPath = videoInputPath,
                    dubClips = dubClips,
                    subtitleClips = subtitleClips,
                    videoWidth = project.videoWidth,
                    videoHeight = project.videoHeight,
                    videoDurationMs = project.videoDurationMs,
                    trimStartMs = project.trimStartMs,
                    trimEndMs = project.effectiveTrimEndMs,
                    outputPath = outputPath,
                    assFilePath = assFilePath,
                    fontDir = fontDir,
                    imageClips = imageClips,
                    resolveImagePath = { uri -> copyImageToCache(uri, cacheDir) },
                    onProgress = { percent ->
                        _uiState.value = _uiState.value.copy(progressPercent = percent)
                    }
                )

                result.fold(
                    onSuccess = { path ->
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            outputPath = path,
                            statusMessage = null
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            error = e.message ?: "Export failed",
                            statusMessage = null
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = e.message ?: "Export failed",
                    statusMessage = null
                )
            }
        }
    }

    private suspend fun copyImageToCache(imageUri: String, cacheDir: File): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (!imageUri.startsWith("content://")) return@withContext imageUri
                val safeName = "image_${java.util.UUID.nameUUIDFromBytes(imageUri.toByteArray())}.bin"
                val dest = File(cacheDir, safeName)
                if (!dest.exists()) {
                    context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                }
                dest.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun copyFontFromAssets(fontDir: String) {
        try {
            val fontFile = File(fontDir, "NotoSansKR-Regular.otf")
            if (!fontFile.exists()) {
                context.assets.open("fonts/NotoSansKR-Regular.otf").use { input ->
                    fontFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {
            // Font not bundled yet — ffmpeg will use fallback
        }
    }
}
