package com.vibi.shared.domain.usecase.save

import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.isProjectEdited
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.usecase.share.GallerySaver
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.ui.export.ExportPlatformAdapter
import com.vibi.shared.ui.export.ExportRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 저장 흐름 — 무편집이면 원본 sourceUri 그대로 갤러리 저장, 편집 있으면 BFF render 후 결과 저장.
 *
 * "편집됨" 판정은 도메인 헬퍼 [isProjectEdited] 가 SSOT — trim/volume/speed/BGM/separation/frame/
 * scale/offset/background 까지 정확히 다룸 (trimEndMs == durationMs sentinel 등 포함).
 *
 * textOverlay / imageClip 는 BFF render 파이프라인이 처리하지 않으므로 (preview 전용) 저장에 영향
 * 없음. 사용자가 추가한 overlay 는 갤러리 결과물에 burn 되지 않는다 — UI 단에서 명시 필요.
 */
class SaveAllVariantsUseCase(
    private val platformAdapter: ExportPlatformAdapter,
    private val gallerySaver: GallerySaver,
    private val editProjectRepository: EditProjectRepository,
    private val segmentRepository: SegmentRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
    private val renderCache: ExportRenderCache,
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        projectId: String,
        onProgress: (percent: Int) -> Unit,
        saveToGallery: Boolean = true,
    ): Result<List<SavedVariant>> {
        return try {
            onProgress(0)
            val project = editProjectRepository.getProject(projectId)
                ?: error("Project not found: $projectId")
            val segments = segmentRepository.getByProjectId(projectId)
            require(segments.isNotEmpty()) { "Project has no segments" }

            val bgmClips = bgmClipRepository.observeClips(projectId).first()
            val separationDirectives = separationDirectiveRepository.getByProject(projectId)

            val firstSeg = segments[0]
            // bypass 조건: 무편집 + 단일 VIDEO segment. sourceUri 형식 변환은 gallerySaver /
            // shareSheetLauncher 가 platform-side resolver 로 처리하므로 가드 불필요.
            val canBypass = segments.size == 1 &&
                firstSeg.type == SegmentType.VIDEO &&
                !isProjectEdited(project, segments, bgmClips, separationDirectives)

            val renderedPath: String = if (canBypass) {
                onProgress(100)
                firstSeg.sourceUri
            } else {
                // 편집 상태 시그니처가 같고 직전 산출물이 남아 있으면 재렌더 없이 재사용 —
                // 공유→저장(또는 반복 export) 의 중복 렌더 제거. 편집이 바뀌면 시그니처가 달라져 자동 재렌더.
                val signature = exportSignature(project, segments, bgmClips, separationDirectives)
                val cached = renderCache.get(signature)
                if (cached != null) {
                    onProgress(90)
                    cached
                } else {
                    val request = ExportRequest(
                        projectId = "$projectId#${ExportVariant.KEY_ORIGINAL}",
                        segments = segments,
                        bgmClips = bgmClips,
                        separationDirectives = separationDirectives,
                        frameWidth = project.frameWidth,
                        frameHeight = project.frameHeight,
                        backgroundColorHex = project.backgroundColorHex,
                    )
                    val out = platformAdapter.executeExport(request) { p ->
                        onProgress((p.coerceIn(0, 100) * 90 / 100))
                    }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        error("Render failed: ${e.message}")
                    }
                    renderCache.put(signature, out)
                    out
                }
            }

            if (saveToGallery) {
                // hashCode + ms timestamp + UUID prefix — race / collision / 동일 ms 재호출 모두 안전.
                val displayName = "VID_${projectId.hashCode().toUInt()}_${currentTimeMillis()}_${Uuid.random().toString().take(8)}"
                gallerySaver.saveVideo(renderedPath, displayName).getOrElse { e ->
                    if (e is CancellationException) throw e
                    error("Gallery save failed: ${e.message}")
                }
            }
            onProgress(100)
            Result.success(listOf(SavedVariant(languageCode = ExportVariant.KEY_ORIGINAL, outputPath = renderedPath)))
        } catch (e: CancellationException) {
            // 구조적 동시성 보존 — viewModelScope cancel 시 caller 가 알아야 함.
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

data class SavedVariant(
    val languageCode: String,
    val outputPath: String,
)

/**
 * 렌더 출력에 영향을 주는 편집 상태 전부를 결정적 문자열로 직렬화 — [ExportRenderCache] 의 키.
 *
 * 불변식: **시그니처는 [ExportRequest] 로 실제 전송되는 입력과 정확히 일치**한다(같은 입력 → 같은 출력).
 * 따라서 ExportRequest 에 들어가는 필드만 넣는다 — projectId·segments·bgmClips·separationDirectives·
 * frameWidth·frameHeight·backgroundColorHex. videoScale/offset 은 ExportRequest 에 없어(렌더 미전송,
 * 출력 무관) 제외 — 포함하면 프리뷰 줌/팬만 해도 헛재렌더가 발생. textOverlay/imageClip 도 BFF 렌더
 * 비처리라 제외. 리스트는 (순서 흔들림 방지) 결정적으로 정렬.
 */
internal fun exportSignature(
    project: EditProject,
    segments: List<Segment>,
    bgmClips: List<BgmClip>,
    directives: List<SeparationDirective>,
): String = buildString {
    append("p=").append(project.projectId)
    append("|frame=").append(project.frameWidth).append('x').append(project.frameHeight)
        .append('@').append(project.backgroundColorHex)
    segments.sortedWith(compareBy({ it.order }, { it.id })).forEach { s ->
        append("|seg=").append(s.sourceUri).append(';').append(s.order).append(';').append(s.type)
            .append(';').append(s.durationMs).append(';').append(s.trimStartMs).append(';').append(s.trimEndMs)
            .append(';').append(s.volumeScale).append(';').append(s.speedScale)
    }
    bgmClips.sortedBy { it.id }.forEach { c ->
        append("|bgm=").append(c.sourceUri).append(';').append(c.startMs).append(';').append(c.volumeScale)
            .append(';').append(c.speedScale).append(';').append(c.sourceTrimStartMs).append(';').append(c.sourceTrimEndMs)
    }
    val speedBySegmentId = segments.associate { it.id to it.speedScale }
    directives.sortedBy { it.id }.forEach { d ->
        // appliedSpeedScale 은 앵커 세그먼트에서 파생돼 ExportRequest 로 전송된다 — 시그니처도 동일하게
        // resolve 해 포함(불변식: 시그니처 == 전송 입력). 세그먼트 speedScale 도 위에서 이미 키에 들어가나,
        // directive→segment 앵커 식별까지 키에 묶어 헛캐시히트 방지.
        val dirSpeed = speedBySegmentId[d.segmentId]?.takeIf { it > 0f } ?: 1f
        append("|dir=").append(d.rangeStartMs).append(';').append(d.rangeEndMs).append(';').append(d.numberOfSpeakers)
            .append(';').append(d.muteOriginalSegmentAudio).append(';').append(d.sourceOffsetMs)
            .append(';').append(dirSpeed)
        d.selections.sortedBy { it.stemId }.forEach { sel ->
            append(";s=").append(sel.stemId).append(',').append(sel.volume)
                .append(',').append(sel.selected).append(',').append(sel.audioUrl ?: "")
        }
    }
}
