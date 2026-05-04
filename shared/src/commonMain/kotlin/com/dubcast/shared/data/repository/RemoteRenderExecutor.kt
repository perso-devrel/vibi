package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.api.BinaryPart
import com.dubcast.shared.data.remote.dto.RenderBgmClip
import com.dubcast.shared.data.remote.dto.RenderConfig
import com.dubcast.shared.data.remote.dto.RenderDubClip
import com.dubcast.shared.data.remote.dto.RenderFrame
import com.dubcast.shared.data.remote.dto.RenderImageClip
import com.dubcast.shared.data.remote.dto.RenderSegment
import com.dubcast.shared.data.remote.dto.RenderSeparationDirective
import com.dubcast.shared.data.remote.dto.RenderSeparationStem
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.usecase.export.BgmClipMixInput
import com.dubcast.shared.domain.usecase.export.DubClipMixInput
import com.dubcast.shared.domain.usecase.export.FfmpegExecutor
import com.dubcast.shared.domain.usecase.export.FrameInput
import com.dubcast.shared.domain.usecase.export.ImageClipMixInput
import com.dubcast.shared.domain.usecase.export.SegmentInput
import com.dubcast.shared.domain.usecase.export.SeparationDirectiveInput
import com.dubcast.shared.platform.currentTimeMillis
import com.dubcast.shared.platform.readFileBytes
import com.dubcast.shared.platform.saveBytesToCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

            val sortedSegments = segments.sortedBy { it.order }
            val videoParts = mutableListOf<BinaryPart>()
            val segmentImageParts = mutableListOf<BinaryPart>()
            val renderSegments = mutableListOf<RenderSegment>()

            // preUploadedInputId 가 있으면 video/audio bytes 는 BFF 캐시에서 재사용 — 여기서 read 도, multipart 도 skip.
            // 단, segment 가 IMAGE 일 때는 캐시 대상이 아니므로 항상 multipart 로 전송 (아래 분기).
            for (seg in sortedSegments) {
                when (seg.type) {
                    SegmentType.VIDEO -> {
                        val key = "video_${seg.order}"
                        if (preUploadedInputId == null) {
                            val bytes = readFileBytes(seg.sourceFilePath)
                            videoParts += BinaryPart(key, "$key.mp4", bytes, "video/mp4")
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
                )
            }

            val subtitlePart = assFilePath?.let { path ->
                runCatching { readFileBytes(path) }.getOrNull()?.let { bytes ->
                    BinaryPart("subtitles", "subtitles.ass", bytes, "text/plain")
                }
            }

            // BFF (RenderRoutes.kt) 가 `audio_override` snake_case 로 매칭. 이전엔 camelCase
            // `audioOverride` 라 BFF 가 silent drop → 자동더빙 결과가 원본 사운드 위에 안 덮여 씌워졌음.
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
                    }
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
                separationDirectives = renderSeparationDirectives
            )

            onProgress(5)
            val jobId = api.submitRenderJob(
                videoFiles = videoParts,
                audioFiles = audioParts,
                subtitles = subtitlePart,
                imageFiles = imageParts,
                segmentImageFiles = segmentImageParts,
                bgmFiles = bgmParts,
                audioOverride = audioOverridePart,
                config = config,
                inputId = preUploadedInputId,
            ).jobId
            onProgress(10)

            val maxPollMs = 15 * 60 * 1000L
            val startTime = currentTimeMillis()

            while (currentCoroutineContext().isActive) {
                if (currentTimeMillis() - startTime > maxPollMs) {
                    throw RuntimeException("렌더링 시간 초과 (15분)")
                }
                val status = api.getRenderStatus(jobId)
                when (status.status) {
                    "COMPLETED" -> { onProgress(90); break }
                    "FAILED" -> throw RuntimeException(status.error ?: "서버 렌더링 실패")
                    else -> {
                        val mapped = 10 + (status.progress * 0.8f).toInt()
                        onProgress(mapped.coerceIn(10, 89))
                    }
                }
                delay(2000)
            }

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
}
