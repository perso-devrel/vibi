package com.example.dubcast.ui.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.repository.BgmClipRepository
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.EditProjectRepository
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.repository.SegmentRepository
import com.example.dubcast.domain.repository.SubtitleClipRepository
import com.example.dubcast.domain.repository.TextOverlayRepository
import com.example.dubcast.domain.usecase.export.ExportWithDubbingUseCase
import com.example.dubcast.domain.usecase.export.FrameInput
import com.example.dubcast.domain.usecase.export.SegmentInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val imageClipRepository: ImageClipRepository,
    private val segmentRepository: SegmentRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val bgmClipRepository: BgmClipRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        loadPreviewData()
    }

    private fun loadPreviewData() {
        viewModelScope.launch {
            editProjectRepository.getProject(projectId) ?: return@launch
            val firstVideo = segmentRepository.getByProjectId(projectId)
                .firstOrNull { it.type == SegmentType.VIDEO }
            _uiState.value = _uiState.value.copy(videoUri = firstVideo?.sourceUri.orEmpty())

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

                val segments = segmentRepository.getByProjectId(projectId)
                require(segments.isNotEmpty()) { "Project has no segments" }

                val frame = if (project.frameWidth > 0 && project.frameHeight > 0) {
                    FrameInput(
                        width = project.frameWidth,
                        height = project.frameHeight,
                        backgroundColorHex = project.backgroundColorHex
                    )
                } else null

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
                val segmentInputs = segments.map { segment ->
                    val localPath = resolveSegmentSource(segment, cacheDir)
                        ?: throw IllegalStateException("Failed to read segment source: ${segment.sourceUri}")
                    segment.toInput(localPath)
                }

                val outputPath = File(cacheDir, "export_${System.currentTimeMillis()}.mp4").absolutePath

                val imageClips = imageClipRepository.observeClips(projectId).first()
                val textOverlays = textOverlayRepository.observeOverlays(projectId).first()
                val bgmClips = bgmClipRepository.observeClips(projectId).first()

                val needsAss = subtitleClips.isNotEmpty() || textOverlays.isNotEmpty()
                val assFilePath = if (needsAss) {
                    File(cacheDir, "subtitles_${projectId}.ass").absolutePath
                } else null

                val fontDir = if (needsAss) {
                    File(cacheDir, "fonts").apply { mkdirs() }.absolutePath.also {
                        copyFontFromAssets(it)
                    }
                } else null

                val result = exportWithDubbingUseCase.execute(
                    segments = segmentInputs,
                    dubClips = dubClips,
                    subtitleClips = subtitleClips,
                    outputPath = outputPath,
                    assFilePath = assFilePath,
                    fontDir = fontDir,
                    frame = frame,
                    imageClips = imageClips,
                    textOverlays = textOverlays,
                    bgmClips = bgmClips,
                    resolveImagePath = { uri -> copyContentUriToCache(uri, cacheDir, prefix = "image") },
                    resolveAudioPath = { uri -> copyContentUriToCache(uri, cacheDir, prefix = "bgm") },
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

    private suspend fun resolveSegmentSource(segment: Segment, cacheDir: File): String? {
        val uri = segment.sourceUri
        return if (uri.startsWith("content://")) {
            val prefix = if (segment.type == SegmentType.VIDEO) "seg_video" else "seg_image"
            copyContentUriToCache(uri, cacheDir, prefix = prefix)
        } else {
            uri
        }
    }

    private suspend fun copyContentUriToCache(
        uri: String,
        cacheDir: File,
        prefix: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!uri.startsWith("content://")) return@withContext uri
            val safeName = "${prefix}_${java.util.UUID.nameUUIDFromBytes(uri.toByteArray())}.bin"
            val dest = File(cacheDir, safeName)
            if (!dest.exists()) {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
            }
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun Segment.toInput(localPath: String) = SegmentInput(
        sourceFilePath = localPath,
        type = type,
        order = order,
        durationMs = durationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        width = width,
        height = height,
        imageXPct = imageXPct,
        imageYPct = imageYPct,
        imageWidthPct = imageWidthPct,
        imageHeightPct = imageHeightPct,
        volumeScale = volumeScale,
        speedScale = speedScale
    )

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
