package com.dubcast.shared.domain.usecase.save

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.api.BinaryPart
import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.repository.BgmClipRepository
import com.dubcast.shared.domain.repository.DubClipRepository
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.ImageClipRepository
import com.dubcast.shared.domain.repository.SegmentRepository
import com.dubcast.shared.domain.repository.SeparationDirectiveRepository
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import com.dubcast.shared.domain.repository.TextOverlayRepository
import com.dubcast.shared.domain.usecase.share.GallerySaver
import com.dubcast.shared.platform.readFileBytes
import com.dubcast.shared.ui.export.ExportPlatformAdapter
import com.dubcast.shared.ui.export.ExportRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * 저장 흐름 — 사용자가 timeline 헤더의 "저장" 버튼을 누르면 BG 에서 호출.
 *
 * 동작:
 *  1. variant 결정: `original` + (subtitleClips ∪ dubClips ∪ project.dubbedAudioPaths) 안의 lang.
 *  2. 변종 2+ 면 BFF input cache 1회 업로드 후 모든 variant 가 inputId 재사용.
 *  3. variant 마다 [ExportPlatformAdapter.executeExport] 병렬 실행 → 결과 파일 path.
 *  4. 결과 파일을 [GallerySaver.saveVideo] 로 갤러리에 저장.
 *  5. 한 건이라도 실패하면 message 와 함께 Result.failure (호출자가 EditProject 보존 결정).
 *
 * 진행 상태 (0..100) 는 [onProgress] 로 보고. 모든 variant 의 평균 percent.
 */
class SaveAllVariantsUseCase(
    private val platformAdapter: ExportPlatformAdapter,
    private val gallerySaver: GallerySaver,
    private val editProjectRepository: EditProjectRepository,
    private val dubClipRepository: DubClipRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val imageClipRepository: ImageClipRepository,
    private val segmentRepository: SegmentRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
    private val bffApi: BffApi,
) {

    suspend operator fun invoke(
        projectId: String,
        onProgress: (percent: Int) -> Unit,
    ): Result<List<SavedVariant>> = runCatching {
        val project = editProjectRepository.getProject(projectId)
            ?: error("Project not found: $projectId")
        val segments = segmentRepository.getByProjectId(projectId)
        require(segments.isNotEmpty()) { "Project has no segments" }

        val allSubtitleClips = subtitleClipRepository.observeClips(projectId).first()
        val langsWithSubtitle = allSubtitleClips
            .map { it.languageCode }
            .filter { it.isNotBlank() }
            .toSet()
        val langsWithDub = (project.dubbedAudioPaths.keys + project.dubbedVideoPaths.keys)
            .filter { it.isNotBlank() }
            .toSet()
        val translationLangs = project.effectiveTargetLanguages
            .filter { it.isNotBlank() && !it.equals("original", ignoreCase = true) }
            .filter { it in langsWithSubtitle || it in langsWithDub }
        val targetLanguages: List<String> = listOf("original") + translationLangs

        val dubClips = dubClipRepository.observeClips(projectId).first()
        val imageClips = imageClipRepository.observeClips(projectId).first()
        val textOverlays = textOverlayRepository.observeOverlays(projectId).first()
        val bgmClips = bgmClipRepository.observeClips(projectId).first()
        val separationDirectives = separationDirectiveRepository.getByProject(projectId)

        val noEdits = imageClips.isEmpty() && textOverlays.isEmpty() && bgmClips.isEmpty() &&
            separationDirectives.isEmpty() && segments.size == 1 &&
            segments[0].trimStartMs == 0L && segments[0].trimEndMs == 0L

        val renderVariantCount = targetLanguages.count { lang ->
            !(lang == "original" && noEdits)
        }
        val preUploadedInputId: String? = if (renderVariantCount >= 2) {
            runCatching { uploadInputCacheOnce(segments, dubClips) }.getOrNull()
        } else null

        // variant 별 진행률 (0..100). 평균값을 onProgress 로 알림.
        val variantProgress = IntArray(targetLanguages.size)
        fun publishProgress() {
            val avg = if (variantProgress.isEmpty()) 0
            else variantProgress.sum() / variantProgress.size
            onProgress(avg.coerceIn(0, 100))
        }

        // 1단계 — 모든 variant 병렬 렌더.
        val renderedPaths: List<String> = coroutineScope {
            targetLanguages.mapIndexed { index, languageCode ->
                async {
                    val isOriginal = languageCode == "original"
                    if (isOriginal && noEdits) {
                        variantProgress[index] = 100
                        publishProgress()
                        return@async segments[0].sourceUri
                    }
                    val variantSubtitles = if (isOriginal) emptyList()
                        else allSubtitleClips.filter { it.languageCode == languageCode }
                    val audioOverridePath: String? = if (isOriginal) null
                        else project.dubbedAudioPaths[languageCode] ?: project.dubbedAudioPath

                    val request = ExportRequest(
                        projectId = "$projectId#$languageCode",
                        outputLanguageCode = languageCode,
                        segments = segments,
                        dubClips = dubClips,
                        subtitleClips = variantSubtitles,
                        imageClips = imageClips,
                        textOverlays = textOverlays,
                        bgmClips = bgmClips,
                        separationDirectives = separationDirectives,
                        frameWidth = project.frameWidth,
                        frameHeight = project.frameHeight,
                        backgroundColorHex = project.backgroundColorHex,
                        audioOverridePath = audioOverridePath,
                        burnSubtitles = variantSubtitles.isNotEmpty() || textOverlays.isNotEmpty(),
                        preUploadedInputId = preUploadedInputId,
                    )
                    val outcome = platformAdapter.executeExport(request) { p ->
                        // 렌더는 0..90 매핑 — 갤러리 저장 단계 10% 여유.
                        variantProgress[index] = (p.coerceIn(0, 100) * 90 / 100)
                        publishProgress()
                    }
                    val path = outcome.getOrElse { e ->
                        error("Render failed for $languageCode: ${e.message}")
                    }
                    variantProgress[index] = 90
                    publishProgress()
                    path
                }
            }.awaitAll()
        }

        // 2단계 — 갤러리 저장 (직렬). 실패 1건이라도 실패로 간주.
        val saved = mutableListOf<SavedVariant>()
        targetLanguages.forEachIndexed { i, languageCode ->
            val path = renderedPaths[i]
            val displayName = "DubCast_${projectId.hashCode().toUInt()}_${languageCode}_$i"
            gallerySaver.saveVideo(path, displayName).getOrElse { e ->
                error("Gallery save failed for $languageCode: ${e.message}")
            }
            variantProgress[i] = 100
            publishProgress()
            saved += SavedVariant(languageCode = languageCode, outputPath = path)
        }
        onProgress(100)
        saved
    }

    /**
     * Multi-variant 시 video + dub audios 를 BFF 에 1회 업로드. 실패하면 caller 가 inputId 없이
     * 진행 — 각 variant 가 자체 multipart 업로드로 fallback. (ExportViewModel 동치 로직.)
     */
    private suspend fun uploadInputCacheOnce(
        segments: List<Segment>,
        dubClips: List<DubClip>,
    ): String {
        val firstVideo = segments
            .filter { it.type == SegmentType.VIDEO }
            .minByOrNull { it.order }
            ?: error("No VIDEO segment to cache for multi-variant save")

        val videoBytes = readFileBytes(firstVideo.sourceUri)
        val videoPart = BinaryPart(
            fieldName = "video",
            filename = "video.mp4",
            bytes = videoBytes,
            contentType = "video/mp4",
        )
        val audioParts = dubClips.mapIndexed { i, clip ->
            BinaryPart(
                fieldName = "audios",
                filename = "audio_$i.mp3",
                bytes = readFileBytes(clip.audioFilePath),
                contentType = "audio/mpeg",
            )
        }
        return bffApi.uploadRenderInputs(video = videoPart, audios = audioParts).inputId
    }
}

data class SavedVariant(
    val languageCode: String,
    val outputPath: String,
)
