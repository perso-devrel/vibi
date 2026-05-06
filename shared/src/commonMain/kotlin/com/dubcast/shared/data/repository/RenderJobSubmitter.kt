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
import com.dubcast.shared.domain.usecase.export.FrameInput
import com.dubcast.shared.domain.usecase.export.ImageClipMixInput
import com.dubcast.shared.domain.usecase.export.SegmentInput
import com.dubcast.shared.domain.usecase.export.SeparationDirectiveInput
import com.dubcast.shared.platform.currentTimeMillis
import com.dubcast.shared.platform.readFileBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json

/**
 * BFF render 잡 제출 + 폴링까지만 처리하는 헬퍼. `RemoteRenderExecutor` 와 다른 점:
 * 결과 파일을 다운로드하지 않고 `jobId` 만 반환. 자막/더빙/분리 가 `editedRenderJobId` 로
 * 재사용할 때 사용.
 *
 * `RemoteRenderExecutor` 와 멀티파트 빌드 로직이 동일하므로 향후 둘을 합쳐도 됨. 지금은 download
 * 단계 유무만 갈리는 형태로 분리.
 */
class RenderJobSubmitter(
    private val api: BffApi,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {

    /**
     * Render 제출 후 BFF 가 COMPLETED 응답을 줄 때까지 폴링. 다운로드는 하지 않음.
     *
     * 진행률 매핑:
     *  - 0..9: 업로드 + 큐 진입
     *  - 10..89: BFF 진행률 비례
     *  - 90..100: 폴링 완료 후 caller 가 후처리 단계에서 사용
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

            val sortedSegments = segments.sortedBy { it.order }
            val videoParts = mutableListOf<BinaryPart>()
            val segmentImageParts = mutableListOf<BinaryPart>()
            val renderSegments = mutableListOf<RenderSegment>()

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

            val audioOverridePart = audioOverridePath?.let { path ->
                runCatching { readFileBytes(path) }.getOrNull()?.let { bytes ->
                    BinaryPart("audio_override", "audio_override.mp3", bytes, "audio/mpeg")
                }
            }

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
                audioOverrideKey = audioOverridePart?.let { "audio_override" },
                separationDirectives = renderSeparationDirectives,
                outputKind = outputKind
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
                    "COMPLETED" -> {
                        onProgress(100)
                        return Result.success(jobId)
                    }
                    "FAILED" -> throw RuntimeException(status.error ?: "서버 렌더링 실패")
                    else -> {
                        val mapped = 10 + (status.progress * 0.9f).toInt()
                        onProgress(mapped.coerceIn(10, 99))
                    }
                }
                delay(2000)
            }
            // 코루틴 취소: 호출자에게 CancellationException 으로 전달.
            throw CancellationException("Render polling cancelled")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
