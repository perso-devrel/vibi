package com.vibi.shared.data.repository

import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.api.BinaryPart
import com.vibi.shared.data.remote.dto.RenderBgmClip
import com.vibi.shared.data.remote.dto.RenderConfig
import com.vibi.shared.data.remote.dto.RenderDubClip
import com.vibi.shared.data.remote.dto.RenderFrame
import com.vibi.shared.data.remote.dto.RenderImageClip
import com.vibi.shared.data.remote.dto.RenderSegment
import com.vibi.shared.data.remote.dto.RenderSeparationDirective
import com.vibi.shared.data.remote.dto.RenderSeparationStem
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.usecase.export.BgmClipMixInput
import com.vibi.shared.domain.usecase.export.DubClipMixInput
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.domain.usecase.export.FrameInput
import com.vibi.shared.domain.usecase.export.ImageClipMixInput
import com.vibi.shared.domain.usecase.export.SegmentInput
import com.vibi.shared.domain.usecase.export.SeparationDirectiveInput
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.platform.readFileBytes
import com.vibi.shared.platform.saveBytesToCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * BFF render 잡 제출 진입점. 두 사용 패턴:
 *  - [renderProject] (FfmpegExecutor 인터페이스): 제출 + 폴링 + 결과 mp4 다운로드 + 캐시 저장 후
 *    파일 경로 반환. ExportWithDubbingUseCase 가 사용.
 *  - [submitAndAwaitJobId]: 제출 + 폴링까지만, jobId 만 반환. RenderRepositoryImpl 가
 *    "편집 영상" source 보장 (자막/더빙/분리 직전 EnsureLatestRender) 흐름에서 사용. 결과 파일은
 *    BFF 가 inputId 로 캐시하고 후속 잡이 재사용.
 *
 * 두 진입점은 동일 multipart 빌드를 공유 ([buildRenderRequest]).
 */
class RemoteRenderExecutor(
    private val api: BffApi
) : FfmpegExecutor {

    override suspend fun renderProject(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput>,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        audioOverridePath: String?,
        separationDirectives: List<SeparationDirectiveInput>,
        preUploadedInputId: String?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        try {
            require(segments.isNotEmpty()) { "segments must not be empty" }
            onProgress(0)

            val req = buildRenderRequest(
                segments = segments,
                dubClips = dubClips,
                imageClips = imageClips,
                assFilePath = assFilePath,
                frame = frame,
                bgmClips = bgmClips,
                audioOverridePath = audioOverridePath,
                separationDirectives = separationDirectives,
                preUploadedInputId = preUploadedInputId,
                outputKind = null,
            )

            onProgress(5)
            val jobId = api.submitRenderJob(
                videoFiles = req.videoParts,
                audioFiles = req.audioParts,
                subtitles = req.subtitlePart,
                imageFiles = req.imageParts,
                segmentImageFiles = req.segmentImageParts,
                bgmFiles = req.bgmParts,
                audioOverride = req.audioOverridePart,
                config = req.config,
                inputId = preUploadedInputId,
            ).jobId
            onProgress(10)

            pollUntilDone(jobId, downloadProgressMax = 89, completedProgress = 90, onProgress = onProgress)

            val bytes = api.downloadRenderResult(jobId)
            val outPath = saveBytesToCache(outputPath.substringAfterLast('/'), bytes)
            onProgress(100)
            return Result.success(outPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Render 제출 + COMPLETED 폴링까지만. 다운로드는 안 함. 결과 파일은 BFF 가 inputId 로 캐시.
     *
     * 진행률 매핑:
     *  - 0..9: 업로드 + 큐 진입
     *  - 10..99: BFF 진행률 비례
     *  - 100: COMPLETED
     */
    suspend fun submitAndAwaitJobId(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput>,
        assFilePath: String?,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        audioOverridePath: String?,
        separationDirectives: List<SeparationDirectiveInput>,
        preUploadedInputId: String?,
        outputKind: String = "video",
        onProgress: (percent: Int) -> Unit,
    ): Result<String> {
        try {
            require(segments.isNotEmpty()) { "segments must not be empty" }
            onProgress(0)

            val req = buildRenderRequest(
                segments = segments,
                dubClips = dubClips,
                imageClips = imageClips,
                assFilePath = assFilePath,
                frame = frame,
                bgmClips = bgmClips,
                audioOverridePath = audioOverridePath,
                separationDirectives = separationDirectives,
                preUploadedInputId = preUploadedInputId,
                outputKind = outputKind,
            )

            onProgress(5)
            val jobId = api.submitRenderJob(
                videoFiles = req.videoParts,
                audioFiles = req.audioParts,
                subtitles = req.subtitlePart,
                imageFiles = req.imageParts,
                segmentImageFiles = req.segmentImageParts,
                bgmFiles = req.bgmParts,
                audioOverride = req.audioOverridePart,
                config = req.config,
                inputId = preUploadedInputId,
            ).jobId
            onProgress(10)

            pollUntilDone(jobId, downloadProgressMax = 99, completedProgress = 100, onProgress = onProgress)
            return Result.success(jobId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend fun pollUntilDone(
        jobId: String,
        downloadProgressMax: Int,
        completedProgress: Int,
        onProgress: (percent: Int) -> Unit,
    ) {
        val maxPollMs = 15 * 60 * 1000L
        val startTime = currentTimeMillis()
        // BFF progress 0..100 → 10..(downloadProgressMax) 로 매핑.
        val span = (downloadProgressMax - 10).coerceAtLeast(1)
        val ratio = span / 100f
        while (currentCoroutineContext().isActive) {
            if (currentTimeMillis() - startTime > maxPollMs) {
                throw RuntimeException("렌더링 시간 초과 (15분)")
            }
            val status = api.getRenderStatus(jobId)
            when (status.status) {
                "COMPLETED" -> { onProgress(completedProgress); return }
                "FAILED" -> throw RuntimeException(status.error ?: "서버 렌더링 실패")
                else -> {
                    val mapped = 10 + (status.progress * ratio).toInt()
                    onProgress(mapped.coerceIn(10, downloadProgressMax))
                }
            }
            delay(2000)
        }
        // 코루틴 취소: 호출자에게 CancellationException 으로 전달.
        throw CancellationException("Render polling cancelled")
    }

    private data class RenderRequest(
        val videoParts: List<BinaryPart>,
        val audioParts: List<BinaryPart>,
        val subtitlePart: BinaryPart?,
        val imageParts: List<BinaryPart>,
        val segmentImageParts: List<BinaryPart>,
        val bgmParts: List<BinaryPart>,
        val audioOverridePart: BinaryPart?,
        val config: RenderConfig,
    )

    private suspend fun buildRenderRequest(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput>,
        assFilePath: String?,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        audioOverridePath: String?,
        separationDirectives: List<SeparationDirectiveInput>,
        preUploadedInputId: String?,
        outputKind: String?,
    ): RenderRequest {
        val sortedSegments = segments.sortedBy { it.order }
        val videoParts = mutableListOf<BinaryPart>()
        val segmentImageParts = mutableListOf<BinaryPart>()
        val renderSegments = mutableListOf<RenderSegment>()

        // 같은 source video 를 가리키는 N 개 segment 가 동일 71MB 파일을 N번 read+upload 하지
        // 않도록 sourceFilePath → key 1대1 매핑. server 는 segments[i].sourceFileKey 로 videoFiles
        // map 을 lookup 하므로 한 key 를 여러 segment 가 공유해도 정상 동작.
        val videoKeyByPath = mutableMapOf<String, String>()

        // preUploadedInputId 가 있으면 video/audio bytes 는 BFF 캐시에서 재사용 — 여기서 read 도, multipart 도 skip.
        // 단, segment 가 IMAGE 일 때는 캐시 대상이 아니므로 항상 multipart 로 전송 (아래 분기).
        for (seg in sortedSegments) {
            when (seg.type) {
                SegmentType.VIDEO -> {
                    val key = videoKeyByPath.getOrPut(seg.sourceFilePath) {
                        val k = "video_${seg.order}"
                        if (preUploadedInputId == null) {
                            val bytes = readFileBytes(seg.sourceFilePath)
                            videoParts += BinaryPart(k, "$k.mp4", bytes, "video/mp4")
                        }
                        k
                    }
                    renderSegments += RenderSegment(
                        sourceFileKey = key,
                        type = "VIDEO",
                        order = seg.order,
                        durationMs = seg.durationMs,
                        trimStartMs = seg.trimStartMs,
                        trimEndMs = seg.effectiveTrimEndMs,
                        width = seg.width,
                        height = seg.height,
                        volumeScale = seg.volumeScale,
                        speedScale = seg.speedScale
                    )
                }
                SegmentType.IMAGE -> {
                    val key = "segment_image_${seg.order}"
                    // IMAGE segment 는 input 캐시 대상이 아님 — 항상 read + multipart.
                    val bytes = readFileBytes(seg.sourceFilePath)
                    segmentImageParts += BinaryPart(key, "$key.img", bytes, "image/*")
                    renderSegments += RenderSegment(
                        sourceFileKey = key,
                        type = "IMAGE",
                        order = seg.order,
                        durationMs = seg.durationMs,
                        width = seg.width,
                        height = seg.height,
                        imageXPct = seg.imageXPct,
                        imageYPct = seg.imageYPct,
                        imageWidthPct = seg.imageWidthPct,
                        imageHeightPct = seg.imageHeightPct
                    )
                }
            }
        }

        val audioParts = mutableListOf<BinaryPart>()
        val renderClips = mutableListOf<RenderDubClip>()
        for ((index, clip) in dubClips.withIndex()) {
            val key = "audio_$index"
            if (preUploadedInputId == null) {
                audioParts += BinaryPart(key, "$key.mp3", readFileBytes(clip.audioFilePath), "audio/mpeg")
            }
            renderClips += RenderDubClip(
                audioFileKey = key,
                startMs = clip.startMs,
                durationMs = 0,
                volume = clip.volume
            )
        }

        val imageParts = mutableListOf<BinaryPart>()
        val renderImageClips = mutableListOf<RenderImageClip>()
        for ((index, clip) in imageClips.withIndex()) {
            val key = "image_$index"
            imageParts += BinaryPart(key, "$key.img", readFileBytes(clip.imageFilePath), "image/*")
            renderImageClips += RenderImageClip(
                imageFileKey = key,
                startMs = clip.startMs,
                endMs = clip.endMs,
                xPct = clip.xPct,
                yPct = clip.yPct,
                widthPct = clip.widthPct,
                heightPct = clip.heightPct
            )
        }

        val bgmParts = mutableListOf<BinaryPart>()
        val renderBgmClips = mutableListOf<RenderBgmClip>()
        for ((index, clip) in bgmClips.withIndex()) {
            val key = "bgm_$index"
            bgmParts += BinaryPart(key, "$key.audio", readFileBytes(clip.audioFilePath), "audio/*")
            renderBgmClips += RenderBgmClip(
                audioFileKey = key,
                startMs = clip.startMs,
                volume = clip.volume,
                speed = clip.speed,
                sourceTrimStartMs = clip.sourceTrimStartMs,
                sourceTrimEndMs = clip.sourceTrimEndMs,
            )
        }

        val subtitlePart = assFilePath?.let { path ->
            runCatching { readFileBytes(path) }.getOrNull()?.let { bytes ->
                BinaryPart("subtitles", "subtitles.ass", bytes, "text/plain")
            }
        }

        // audio_override 는 BFF snake_case 매칭 — 키 변경 시 BFF 동시 수정.
        val audioOverridePart = audioOverridePath?.let { path ->
            runCatching { readFileBytes(path) }.getOrNull()?.let { bytes ->
                BinaryPart("audio_override", "audio_override.mp3", bytes, "audio/mpeg")
            }
        }

        // selected=false 필터는 호출자(ExportWithDubbingUseCase) 단계에서 이미 적용 — 여기로
        // 넘어온 SeparationDirectiveInput 의 selections 는 mix 에 들어갈 stem 만 남아있음.
        val renderSeparationDirectives = separationDirectives.map { d ->
            RenderSeparationDirective(
                id = d.id,
                rangeStartMs = d.rangeStartMs,
                rangeEndMs = d.rangeEndMs,
                numberOfSpeakers = d.numberOfSpeakers,
                muteOriginalSegmentAudio = d.muteOriginalSegmentAudio,
                selections = d.selections.map { s ->
                    RenderSeparationStem(
                        stemId = s.stemId,
                        audioUrl = s.audioUrl,
                        volume = s.volume
                    )
                },
                sourceOffsetMs = d.sourceOffsetMs,
            )
        }

        val config = RenderConfig(
            dubClips = renderClips,
            segments = renderSegments,
            imageClips = renderImageClips,
            frame = frame?.let {
                RenderFrame(
                    width = it.width,
                    height = it.height,
                    backgroundColorHex = it.backgroundColorHex
                )
            },
            bgmClips = renderBgmClips,
            // multipart audio_override 가 있을 때 BFF 가 audio_override 슬롯을 활성화하기
            // 위해 같은 키를 config 에도 반드시 명시. 누락 시 BFF 는 multipart 파일을 무시.
            audioOverrideKey = audioOverridePart?.let { "audio_override" },
            separationDirectives = renderSeparationDirectives,
            outputKind = outputKind ?: "video",
        )

        return RenderRequest(
            videoParts = videoParts,
            audioParts = audioParts,
            subtitlePart = subtitlePart,
            imageParts = imageParts,
            segmentImageParts = segmentImageParts,
            bgmParts = bgmParts,
            audioOverridePart = audioOverridePart,
            config = config,
        )
    }
}
