package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SeparationDirective
import com.dubcast.shared.domain.model.TextOverlay
import com.dubcast.shared.domain.repository.RenderRepository
import com.dubcast.shared.domain.usecase.render.RenderKind
import com.dubcast.shared.domain.usecase.export.BgmClipMixInput
import com.dubcast.shared.domain.usecase.export.FrameInput
import com.dubcast.shared.domain.usecase.export.ImageClipMixInput
import com.dubcast.shared.domain.usecase.export.SegmentInput
import com.dubcast.shared.domain.usecase.export.SeparationDirectiveInput
import com.dubcast.shared.domain.usecase.export.SeparationStemInput

/**
 * `RenderRepository` 의 commonMain 구현. 자막/더빙/분리 가 source 로 쓸 "편집 영상" 1개만 만든다.
 *
 * 제외:
 *  - subtitleClips: 자막은 BFF 가 본 source 위에 STT/번역으로 새로 생성.
 *  - textOverlays: 시각 오버레이 — 편집 source 에는 포함 X (BFF 가 최종 output 만들 때 별도 합성).
 *  - dubClips / audioOverridePath: 더빙은 본 source 의 audio 를 STT 후 재합성.
 *
 * 포함 (사용자가 timeline 에 만든 결과를 STT 가 듣게):
 *  - segments (trim/speed/volume)
 *  - frame (캔버스 비율 / 배경)
 *  - imageClips: 시각만 영향, audio 무관 — 포함해 일관된 영상 출력.
 *  - bgmClips: 본 BG 음악이 STT 에 영향. 포함해야 사용자 의도 반영.
 *  - separationDirectives: 음원분리 결과 stem mix — 본 source 에 반영.
 *
 * imageClips / bgmClips 는 플랫폼별 path 해결이 필요 (Android `content://` URI). 본 구현은
 * 기본적으로 path 해결 안 함 — 따라서 이미지/BGM 이 필요한 프로젝트는 platform-specific resolver
 * 를 주입해야 함. 현재 자막/더빙/분리 흐름에서 이 케이스가 작아 미해결로 남김 (NEXT — TODO).
 */
class RenderRepositoryImpl(
    private val submitter: RenderJobSubmitter,
    /**
     * `content://` 또는 `file://` URI 를 절대 경로로 변환. null 을 반환하면 해당 클립 skip.
     * 기본 구현은 항상 null (이미지/오디오 클립 미포함).
     */
    private val resolveImagePath: suspend (uri: String) -> String? = { null },
    private val resolveAudioPath: suspend (uri: String) -> String? = { null },
) : RenderRepository {

    override suspend fun submitForEditedSource(
        project: EditProject,
        segments: List<Segment>,
        imageClips: List<ImageClip>,
        bgmClips: List<BgmClip>,
        textOverlays: List<TextOverlay>,
        separationDirectives: List<SeparationDirective>,
        kind: RenderKind,
        onProgress: (percent: Int) -> Unit,
    ): Result<String> {
        require(segments.isNotEmpty()) { "Cannot render: project has no segments" }

        val segmentInputs = segments.map { seg ->
            SegmentInput(
                sourceFilePath = seg.sourceUri,
                type = seg.type,
                order = seg.order,
                durationMs = seg.durationMs,
                trimStartMs = seg.trimStartMs,
                trimEndMs = seg.trimEndMs,
                width = seg.width,
                height = seg.height,
                imageXPct = seg.imageXPct,
                imageYPct = seg.imageYPct,
                imageWidthPct = seg.imageWidthPct,
                imageHeightPct = seg.imageHeightPct,
                volumeScale = seg.volumeScale,
                speedScale = seg.speedScale,
            )
        }

        val firstSegment = segmentInputs.minByOrNull { it.order } ?: segmentInputs.first()
        val frame = if (project.frameWidth > 0 && project.frameHeight > 0) {
            FrameInput(
                width = project.frameWidth,
                height = project.frameHeight,
                backgroundColorHex = project.backgroundColorHex,
            )
        } else FrameInput(
            width = firstSegment.width,
            height = firstSegment.height,
            backgroundColorHex = project.backgroundColorHex,
        )

        val imageMixInputs = imageClips.mapNotNull { clip ->
            val localPath = resolveImagePath(clip.imageUri) ?: return@mapNotNull null
            ImageClipMixInput(
                imageFilePath = localPath,
                startMs = clip.startMs,
                endMs = clip.endMs,
                xPct = clip.xPct,
                yPct = clip.yPct,
                widthPct = clip.widthPct,
                heightPct = clip.heightPct,
            )
        }

        val bgmMixInputs = bgmClips.mapNotNull { clip ->
            val localPath = resolveAudioPath(clip.sourceUri) ?: return@mapNotNull null
            BgmClipMixInput(
                audioFilePath = localPath,
                startMs = clip.startMs,
                volume = clip.volumeScale,
                speed = clip.speedScale,
            )
        }

        val separationInputs = separationDirectives.mapNotNull { d ->
            val stems = d.selections.mapNotNull { sel ->
                if (!sel.selected) return@mapNotNull null
                val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SeparationStemInput(
                    stemId = sel.stemId,
                    audioUrl = url,
                    volume = sel.volume,
                )
            }
            if (stems.isEmpty()) null else SeparationDirectiveInput(
                id = d.id,
                rangeStartMs = d.rangeStartMs,
                rangeEndMs = d.rangeEndMs,
                numberOfSpeakers = d.numberOfSpeakers,
                muteOriginalSegmentAudio = d.muteOriginalSegmentAudio,
                selections = stems,
            )
        }

        return submitter.submitAndAwaitJobId(
            segments = segmentInputs,
            dubClips = emptyList(),
            imageClips = imageMixInputs,
            assFilePath = null,
            frame = frame,
            bgmClips = bgmMixInputs,
            audioOverridePath = null,
            separationDirectives = separationInputs,
            preUploadedInputId = null,
            outputKind = when (kind) {
                RenderKind.AUDIO -> "audio"
                RenderKind.VIDEO -> "video"
            },
            onProgress = onProgress,
        )
    }
}
