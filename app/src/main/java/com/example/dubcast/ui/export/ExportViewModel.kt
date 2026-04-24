package com.example.dubcast.ui.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.TargetLanguage
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

// Export 화면에서 "원본 그대로(ORIGINAL)" 는 exportMode 로 표현되므로
// 언어 드롭다운 옵션은 번역 가능 언어만 노출한다.
val EXPORT_TARGET_LANGUAGES: List<TargetLanguage> = TargetLanguage.AVAILABLE
    .filter { it.code != TargetLanguage.CODE_ORIGINAL }

data class ExportUiState(
    val exportMode: ExportMode = ExportMode.ORIGINAL_ONLY,
    val targetLanguage: TargetLanguage = EXPORT_TARGET_LANGUAGES[1],
    val enableDubbing: Boolean = true,
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
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            val firstVideo = segmentRepository.getByProjectId(projectId)
                .firstOrNull { it.type == SegmentType.VIDEO }

            // Input/Timeline 에서 결정된 프로젝트 옵션을 Export 초기값으로 hydrate.
            // ORIGINAL 언어코드면 ORIGINAL_ONLY 모드로 자동 매핑, 그 외엔 WITH_TRANSLATION.
            val isTranslation = project.targetLanguageCode != TargetLanguage.CODE_ORIGINAL
            val hydratedLanguage = if (isTranslation) {
                EXPORT_TARGET_LANGUAGES.firstOrNull { it.code == project.targetLanguageCode }
                    ?: _uiState.value.targetLanguage
            } else {
                _uiState.value.targetLanguage
            }
            _uiState.value = _uiState.value.copy(
                videoUri = firstVideo?.sourceUri.orEmpty(),
                exportMode = if (isTranslation) ExportMode.WITH_TRANSLATION else ExportMode.ORIGINAL_ONLY,
                targetLanguage = hydratedLanguage,
                enableDubbing = project.enableAutoDubbing,
                enableAutoSubtitles = project.enableAutoSubtitles
            )

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
        _uiState.value = _uiState.value.copy(enableDubbing = enabled)
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

                // Auto-dub override: only honor when the user actually
                // enabled dubbing for this export and the BFF pipeline
                // produced a local file. Otherwise the source audio plays.
                val audioOverridePath = if (isTranslation && options.enableDubbing) {
                    project.dubbedAudioPath?.takeIf { File(it).exists() }
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
                    audioOverridePath = audioOverridePath,
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
