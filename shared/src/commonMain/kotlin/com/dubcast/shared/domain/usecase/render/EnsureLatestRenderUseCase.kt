package com.dubcast.shared.domain.usecase.render

import com.dubcast.shared.domain.model.isProjectEdited
import com.dubcast.shared.domain.repository.BgmClipRepository
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.ImageClipRepository
import com.dubcast.shared.domain.repository.RenderRepository
import com.dubcast.shared.domain.repository.SegmentRepository
import com.dubcast.shared.domain.repository.SeparationDirectiveRepository
import com.dubcast.shared.domain.repository.TextOverlayRepository
import kotlinx.coroutines.flow.first

/**
 * BFF render 출력 종류. AUDIO 는 ffmpeg 의 audio-only m4a 추출 (5–10x 빠름) 으로 자막/STT/음성분리에
 * 사용. VIDEO 는 풀 mp4 mux 로 자동 더빙에 사용. caller 는 어느 후속 작업에 쓰일지에 따라 선택한다.
 */
enum class RenderKind { AUDIO, VIDEO }

/**
 * 자막/더빙/분리 시작 직전에 호출. 편집 영상 source 가 필요하면 BFF 에 render 잡 1개를 보내고
 * jobId 를 보장한 뒤 반환한다. [kind] 에 따라 audio-only 또는 video render 를 별도 슬롯으로 캐싱.
 *
 * 동작:
 *  1. project + segments + bgm/image/text/separation 조회. `isProjectEdited(...) == false` 면
 *     단일 segment + 모든 default + 추가 합성 항목 없음 → 원본 영상 그대로 사용 가능.
 *     `null` 반환 (호출자가 segment[0].sourceUri 사용).
 *  2. `project.isRenderStale == false` 이고 [kind] 에 해당하는 jobId 가 있으면 그대로 재사용.
 *     (BFF 측 lastAccessedAt 자동 갱신은 자막/더빙/분리 endpoint 가 호출되는 시점에 수행됨.)
 *  3. 그 외 → 새로 [RenderRepository.submitForEditedSource] 를 outputKind 와 함께 호출 →
 *     COMPLETED 까지 폴링 → project 의 해당 kind 슬롯에 jobId 저장 + isRenderStale=false 표시 →
 *     jobId 반환. 다른 kind 슬롯은 보존 (재사용 가능 시 추후 호출에서 hit 가능).
 *
 * @param onProgress 0..100 의 render 진행률 — UI 가 "편집 영상 준비 중…" 표시용.
 */
class EnsureLatestRenderUseCase(
    private val renderRepository: RenderRepository,
    private val editProjectRepository: EditProjectRepository,
    private val segmentRepository: SegmentRepository,
    private val imageClipRepository: ImageClipRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
) {
    /**
     * @return Result.success(jobId) — 새 render 또는 캐시된 jobId.
     *         Result.success(null) — 편집 없음 (원본 영상 그대로).
     *         Result.failure — render 또는 폴링 실패.
     */
    suspend operator fun invoke(
        projectId: String,
        kind: RenderKind,
        onProgress: (percent: Int) -> Unit = {},
    ): Result<String?> = runCatching {
        val project = editProjectRepository.getProject(projectId)
            ?: throw IllegalStateException("Project not found: $projectId")
        val segments = segmentRepository.getByProjectId(projectId)
        if (segments.isEmpty()) {
            throw IllegalStateException("Project has no segments")
        }

        // 편집 검사를 위해 합성 항목들 선조회 — segment-local 외에 BGM / image / text / separation /
        // project frame 설정도 모두 "편집됨" 으로 간주해야 BFF 가 사용자 미리보기와 동일한 영상을 받음.
        val imageClips = imageClipRepository.observeClips(projectId).first()
        val bgmClips = bgmClipRepository.observeClips(projectId).first()
        val textOverlays = textOverlayRepository.observeOverlays(projectId).first()
        val separationDirectives = separationDirectiveRepository.getByProject(projectId)

        // 편집 안 됨 — 원본 사용. caller 가 segments[0].sourceUri 로 multipart 업로드.
        if (!isProjectEdited(
                project = project,
                segments = segments,
                bgmClips = bgmClips,
                imageClips = imageClips,
                textOverlays = textOverlays,
                separationDirectives = separationDirectives,
            )
        ) {
            return@runCatching null as String?
        }

        // 캐시 hit — 직전 render 가 여전히 유효 + 요청한 kind 의 jobId 가 있을 때만.
        if (!project.isRenderStale) {
            val cached = when (kind) {
                RenderKind.AUDIO -> project.currentAudioRenderJobId
                RenderKind.VIDEO -> project.currentVideoRenderJobId
            }
            if (cached != null) {
                return@runCatching cached
            }
        }

        val jobId = renderRepository.submitForEditedSource(
            project = project,
            segments = segments,
            imageClips = imageClips,
            bgmClips = bgmClips,
            textOverlays = textOverlays,
            separationDirectives = separationDirectives,
            kind = kind,
            onProgress = onProgress,
        ).getOrThrow()

        // jobId 영속화 — 해당 kind 슬롯만 갱신, 다른 kind 슬롯은 보존. stale=false.
        val updated = when (kind) {
            RenderKind.AUDIO -> project.copy(
                currentAudioRenderJobId = jobId,
                isRenderStale = false,
            )
            RenderKind.VIDEO -> project.copy(
                currentVideoRenderJobId = jobId,
                isRenderStale = false,
            )
        }
        editProjectRepository.updateProject(updated)
        jobId
    }
}
