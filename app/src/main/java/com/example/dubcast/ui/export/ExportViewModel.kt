package com.example.dubcast.ui.export

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.usecase.export.ExportWithDubbingUseCase
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
    KEEP_ORIGINAL("원어 유지"),
    DUBBING_LANGUAGE("더빙 언어로 변경")
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
    val targetLanguage: TargetLanguage = AVAILABLE_LANGUAGES[1], // default English
    val enableDubbing: Boolean = true,
    val enableLipSync: Boolean = false,
    val enableAutoSubtitles: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.KEEP_ORIGINAL,
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
    private val subtitleClipRepository: SubtitleClipRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

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
            _uiState.value = _uiState.value.copy(isExporting = true, error = null, statusMessage = "준비 중...")

            try {
                val project = editProjectRepository.getProject(projectId)
                    ?: throw IllegalStateException("Project not found: $projectId")

                val options = _uiState.value
                val isTranslation = options.exportMode == ExportMode.WITH_TRANSLATION

                val dubClips = if (isTranslation && options.enableDubbing) {
                    _uiState.value = _uiState.value.copy(statusMessage = "더빙 처리 중...")
                    dubClipRepository.observeClips(projectId).first()
                } else emptyList()

                val subtitleClips = if (isTranslation && options.enableAutoSubtitles) {
                    _uiState.value = _uiState.value.copy(statusMessage = "자막 생성 중...")
                    subtitleClipRepository.observeClips(projectId).first()
                } else emptyList()

                if (isTranslation && options.enableLipSync && dubClips.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(statusMessage = "립싱크 적용 중...")
                    // TODO: Lip sync processing
                }

                _uiState.value = _uiState.value.copy(statusMessage = "영상 합성 중...")

                val cacheDir = context.cacheDir
                val outputPath = File(cacheDir, "export_${System.currentTimeMillis()}.mp4").absolutePath

                val assFilePath = if (subtitleClips.isNotEmpty()) {
                    File(cacheDir, "subtitles_${projectId}.ass").absolutePath
                } else null

                val fontDir = if (subtitleClips.isNotEmpty()) {
                    File(cacheDir, "fonts").apply { mkdirs() }.absolutePath.also {
                        copyFontFromAssets(it)
                    }
                } else null

                val result = exportWithDubbingUseCase.execute(
                    inputVideoPath = project.videoUri,
                    dubClips = dubClips,
                    subtitleClips = subtitleClips,
                    videoWidth = project.videoWidth,
                    videoHeight = project.videoHeight,
                    videoDurationMs = project.videoDurationMs,
                    outputPath = outputPath,
                    assFilePath = assFilePath,
                    fontDir = fontDir,
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
