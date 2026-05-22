package com.vibi.shared.domain.usecase.save

import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.repository.TextOverlayRepository
import com.vibi.shared.domain.repository.ImageClipRepository
import com.vibi.shared.domain.usecase.share.GallerySaver
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.ui.export.ExportPlatformAdapter
import com.vibi.shared.ui.export.ExportRequest
import kotlinx.coroutines.flow.first

/**
 * 저장 흐름 — 자막/더빙 제거 후 단일 variant (원본 영상) 만 처리.
 *
 * 1. 편집 없음 (BGM 0, separation 0, segment 1 + trim 0) → 원본 파일 그대로 갤러리 저장.
 * 2. 편집 있음 → BFF render 후 결과 갤러리 저장.
 */
class SaveAllVariantsUseCase(
    private val platformAdapter: ExportPlatformAdapter,
    private val gallerySaver: GallerySaver,
    private val editProjectRepository: EditProjectRepository,
    private val segmentRepository: SegmentRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val imageClipRepository: ImageClipRepository,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
) {

    suspend operator fun invoke(
        projectId: String,
        onProgress: (percent: Int) -> Unit,
        saveToGallery: Boolean = true,
    ): Result<List<SavedVariant>> = runCatching {
        onProgress(0)
        val project = editProjectRepository.getProject(projectId)
            ?: error("Project not found: $projectId")
        val segments = segmentRepository.getByProjectId(projectId)
        require(segments.isNotEmpty()) { "Project has no segments" }

        val bgmClips = bgmClipRepository.observeClips(projectId).first()
        val separationDirectives = separationDirectiveRepository.getByProject(projectId)
        val textOverlays = textOverlayRepository.observeOverlays(projectId).first()
        val imageClips = imageClipRepository.observeClips(projectId).first()

        val renderedPath: String = if (canCopySourceDirectly(project, segments, bgmClips, separationDirectives, textOverlays, imageClips)) {
            onProgress(100)
            segments[0].sourceUri
        } else {
            val request = ExportRequest(
                projectId = "$projectId#${ExportVariant.KEY_ORIGINAL}",
                segments = segments,
                bgmClips = bgmClips,
                separationDirectives = separationDirectives,
                frameWidth = project.frameWidth,
                frameHeight = project.frameHeight,
                backgroundColorHex = project.backgroundColorHex,
                preUploadedInputId = null,
            )
            platformAdapter.executeExport(request) { p ->
                onProgress((p.coerceIn(0, 100) * 90 / 100))
            }.getOrElse { e ->
                error("Render failed: ${e.message}")
            }
        }

        if (saveToGallery) {
            // hashCode 충돌 + 동일 projectId 재저장 race 방지 위해 timestamp 포함.
            val displayName = "VID_${projectId.hashCode().toUInt()}_${currentTimeMillis()}"
            gallerySaver.saveVideo(renderedPath, displayName).getOrElse { e ->
                error("Gallery save failed: ${e.message}")
            }
        }
        onProgress(100)
        listOf(SavedVariant(languageCode = ExportVariant.KEY_ORIGINAL, outputPath = renderedPath))
    }

    /**
     * 사용자가 timeline 에 가한 모든 mutation 을 검사 — segment 자체 (trim/volume/speed) 와
     * project 레벨 frame/scale/배경, BGM/separation/text overlay/image clip 까지 포함. 무편집이고
     * 단일 VIDEO segment 면 BFF render 우회하고 원본 파일을 그대로 갤러리 저장 가능.
     */
    private fun canCopySourceDirectly(
        project: EditProject,
        segments: List<Segment>,
        bgmClips: List<*>,
        separationDirectives: List<*>,
        textOverlays: List<*>,
        imageClips: List<*>,
    ): Boolean {
        if (segments.size != 1) return false
        val seg = segments[0]
        if (seg.type != SegmentType.VIDEO) return false
        if (seg.trimStartMs != 0L || seg.trimEndMs != 0L) return false
        if (seg.volumeScale != 1f || seg.speedScale != 1f) return false
        if (bgmClips.isNotEmpty() || separationDirectives.isNotEmpty()) return false
        if (textOverlays.isNotEmpty() || imageClips.isNotEmpty()) return false
        // Frame override: 0×0 = "원본 그대로". non-zero 인데 segment 와 다르면 frame edit 적용된 것.
        if (project.frameWidth != 0 && project.frameWidth != seg.width) return false
        if (project.frameHeight != 0 && project.frameHeight != seg.height) return false
        if (project.videoScale != EditProject.DEFAULT_VIDEO_SCALE) return false
        if (project.videoOffsetXPct != 0f || project.videoOffsetYPct != 0f) return false
        if (project.backgroundColorHex != EditProject.DEFAULT_BACKGROUND_COLOR_HEX) return false
        return true
    }
}

data class SavedVariant(
    val languageCode: String,
    val outputPath: String,
)
