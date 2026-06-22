package com.vibi.shared.data.repository

import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.api.BinaryPart
import com.vibi.shared.data.remote.dto.RenderBgmClip
import com.vibi.shared.data.remote.dto.RenderConfig
import com.vibi.shared.data.remote.dto.RenderFrame
import com.vibi.shared.data.remote.dto.RenderSegment
import com.vibi.shared.data.remote.dto.RenderSeparationDirective
import com.vibi.shared.data.remote.dto.RenderSeparationStem
import com.vibi.shared.domain.usecase.export.BgmClipMixInput
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.domain.usecase.export.FrameInput
import com.vibi.shared.domain.usecase.export.SegmentInput
import com.vibi.shared.domain.usecase.export.SeparationDirectiveInput
import com.vibi.shared.platform.cacheDirPath
import com.vibi.shared.platform.readFileBytes
import com.vibi.shared.platform.writeChannelToFile
import kotlinx.coroutines.CancellationException

/**
 * BFF render 잡 제출 + COMPLETED 폴링 + 결과 mp4 다운로드 + 캐시 저장 후 파일 경로 반환.
 */
class RemoteRenderExecutor(
    private val api: BffApi
) : FfmpegExecutor {

    override suspend fun renderProject(
        segments: List<SegmentInput>,
        outputPath: String,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        separationDirectives: List<SeparationDirectiveInput>,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        return try {
            require(segments.isNotEmpty()) { "segments must not be empty" }
            onProgress(0)

            val req = buildRenderRequest(
                segments = segments,
                frame = frame,
                bgmClips = bgmClips,
                separationDirectives = separationDirectives,
            )

            onProgress(5)
            val jobId = api.submitRenderJob(
                videoFiles = req.videoParts,
                bgmFiles = req.bgmParts,
                config = req.config,
            ).jobId
            onProgress(10)

            val pollStartProgress = 10
            val pollEndProgress = 89
            val ratio = (pollEndProgress - pollStartProgress).coerceAtLeast(1) / 100f
            pollRenderJobUntilDone(
                api = api,
                jobId = jobId,
                onPoll = { sp ->
                    val mapped = pollStartProgress + (sp * ratio).toInt()
                    onProgress(mapped.coerceIn(pollStartProgress, pollEndProgress))
                },
                onCompleted = { onProgress(90) },
            )

            val outPath = "${cacheDirPath()}/${outputPath.substringAfterLast('/')}"
            api.downloadRenderResult(jobId) { writeChannelToFile(it, outPath) }
            onProgress(100)
            Result.success(outPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private data class RenderRequest(
        val videoParts: List<BinaryPart>,
        val bgmParts: List<BinaryPart>,
        val config: RenderConfig,
    )

    private suspend fun buildRenderRequest(
        segments: List<SegmentInput>,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        separationDirectives: List<SeparationDirectiveInput>,
    ): RenderRequest {
        val sortedSegments = segments.sortedBy { it.order }
        val videoParts = mutableListOf<BinaryPart>()
        val renderSegments = mutableListOf<RenderSegment>()

        // 같은 source video 를 가리키는 N 개 segment 가 동일 파일을 N번 read+upload 하지
        // 않도록 sourceFilePath → key 1대1 매핑.
        val videoKeyByPath = mutableMapOf<String, String>()

        for (seg in sortedSegments) {
            val key = videoKeyByPath.getOrPut(seg.sourceFilePath) {
                val k = "video_${seg.order}"
                val bytes = readFileBytes(seg.sourceFilePath)
                videoParts += BinaryPart(k, "$k.mp4", bytes, "video/mp4")
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

        // selected=false 필터는 호출자 단계에서 이미 적용 — 여기로 넘어온 SeparationDirectiveInput 의
        // selections 는 mix 에 들어갈 stem 만 남아있음.
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
                appliedSpeedScale = d.appliedSpeedScale,
            )
        }

        val config = RenderConfig(
            segments = renderSegments,
            frame = frame?.let {
                RenderFrame(
                    width = it.width,
                    height = it.height,
                    backgroundColorHex = it.backgroundColorHex
                )
            },
            bgmClips = renderBgmClips,
            separationDirectives = renderSeparationDirectives,
            outputKind = "video",
        )

        return RenderRequest(
            videoParts = videoParts,
            bgmParts = bgmParts,
            config = config,
        )
    }
}
