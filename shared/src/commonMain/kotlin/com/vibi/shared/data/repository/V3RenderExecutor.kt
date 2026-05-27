package com.vibi.shared.data.repository

import com.vibi.shared.data.remote.AssetUploadManager
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.dto.RenderBgmClipV3
import com.vibi.shared.data.remote.dto.RenderConfigV3
import com.vibi.shared.data.remote.dto.RenderSegmentV3
import com.vibi.shared.data.remote.dto.RenderSeparationDirective
import com.vibi.shared.data.remote.dto.RenderSeparationStem
import com.vibi.shared.domain.usecase.export.BgmClipMixInput
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.domain.usecase.export.FrameInput
import com.vibi.shared.domain.usecase.export.SegmentInput
import com.vibi.shared.domain.usecase.export.SeparationDirectiveInput
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.platform.saveBytesToCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * v3 asset-by-reference 렌더 — segment 영상/BGM 을 R2 에 사전 PUT 한 뒤 키만 BFF 로 전송.
 *
 * 흐름:
 *   1) segments 의 distinct sourceFilePath 마다 [AssetUploadManager.ensureUploaded] →
 *      asset key. 같은 source 영상 반복 사용 시 1회만 sha256/upload.
 *   2) bgmClips 의 distinct audioFilePath 마다 동일 — 같은 BGM 두 directive 사용 시
 *      R2 PUT 1회만 (v2 multipart 의 BGM dedup 누락 해결).
 *   3) [RenderConfigV3] 구성 → [BffApi.submitRenderJobV3] 호출 (JSON, multipart 없음).
 *   4) pollUntilDone + downloadRenderResult — BFF 가 완료 즉시 R2 push 했으므로 302 즉시.
 *
 * iOS-only. Android 는 [RemoteRenderExecutor] (v2 multipart) 가 계속 사용.
 */
class V3RenderExecutor(
    private val api: BffApi,
    private val assetUploader: AssetUploadManager,
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

            val sortedSegments = segments.sortedBy { it.order }
            val distinctVideoPaths = sortedSegments.map { it.sourceFilePath }.distinct()
            val distinctBgmPaths = bgmClips.map { it.audioFilePath }.distinct()
            val totalUploads = (distinctVideoPaths.size + distinctBgmPaths.size).coerceAtLeast(1)
            var done = 0
            fun bumpUploadProgress() {
                done++
                onProgress((done * 30 / totalUploads).coerceIn(0, 30))
            }

            val assetKeyByVideoPath = mutableMapOf<String, String>()
            for (path in distinctVideoPaths) {
                assetKeyByVideoPath[path] = assetUploader.ensureUploaded(
                    localPath = path,
                    ext = "mp4",
                    contentType = "video/mp4",
                )
                bumpUploadProgress()
            }

            val assetKeyByBgmPath = mutableMapOf<String, String>()
            for (path in distinctBgmPaths) {
                val ext = inferAudioExt(path)
                assetKeyByBgmPath[path] = assetUploader.ensureUploaded(
                    localPath = path,
                    ext = ext,
                    contentType = inferAudioContentType(ext),
                )
                bumpUploadProgress()
            }

            val config = RenderConfigV3(
                segments = sortedSegments.map { s ->
                    RenderSegmentV3(
                        sourceAssetKey = requireNotNull(assetKeyByVideoPath[s.sourceFilePath]) {
                            "missing assetKey for segment path ${s.sourceFilePath}"
                        },
                        order = s.order,
                        durationMs = s.durationMs,
                        trimStartMs = s.trimStartMs,
                        trimEndMs = s.effectiveTrimEndMs,
                        volumeScale = s.volumeScale,
                        speedScale = s.speedScale,
                    )
                },
                bgmClips = bgmClips.map { c ->
                    RenderBgmClipV3(
                        audioAssetKey = requireNotNull(assetKeyByBgmPath[c.audioFilePath]) {
                            "missing assetKey for bgm path ${c.audioFilePath}"
                        },
                        startMs = c.startMs,
                        volume = c.volume,
                        sourceTrimStartMs = c.sourceTrimStartMs,
                        sourceTrimEndMs = c.sourceTrimEndMs,
                    )
                },
                separationDirectives = separationDirectives.map { d ->
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
                                volume = s.volume,
                            )
                        },
                        sourceOffsetMs = d.sourceOffsetMs,
                    )
                },
                outputKind = "video",
            )

            onProgress(35)
            val jobId = api.submitRenderJobV3(config).jobId
            onProgress(40)

            pollUntilDone(
                jobId,
                pollStartProgress = 40,
                pollEndProgress = 89,
                completedProgress = 90,
                onProgress = onProgress,
            )

            val bytes = api.downloadRenderResult(jobId)
            val outPath = saveBytesToCache(outputPath.substringAfterLast('/'), bytes)
            onProgress(100)
            Result.success(outPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun pollUntilDone(
        jobId: String,
        pollStartProgress: Int,
        pollEndProgress: Int,
        completedProgress: Int,
        onProgress: (percent: Int) -> Unit,
    ) {
        val maxPollMs = 15 * 60 * 1000L
        val startTime = currentTimeMillis()
        val span = (pollEndProgress - pollStartProgress).coerceAtLeast(1)
        val ratio = span / 100f
        while (currentCoroutineContext().isActive) {
            if (currentTimeMillis() - startTime > maxPollMs) {
                throw RuntimeException("Render timed out (15 min)")
            }
            val status = api.getRenderStatus(jobId)
            when (status.status) {
                "COMPLETED" -> { onProgress(completedProgress); return }
                "FAILED" -> throw RuntimeException(status.error ?: "Server render failed")
                else -> {
                    val mapped = pollStartProgress + (status.progress * ratio).toInt()
                    onProgress(mapped.coerceIn(pollStartProgress, pollEndProgress))
                }
            }
            delay(2000)
        }
        throw CancellationException("Render polling cancelled")
    }

    private fun inferAudioExt(path: String): String {
        val raw = path.substringAfterLast('.', "").lowercase()
        return if (raw in ALLOWED_AUDIO_EXTS) raw else "m4a"
    }

    private fun inferAudioContentType(ext: String): String = when (ext) {
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        else -> "audio/mp4"
    }

    companion object {
        private val ALLOWED_AUDIO_EXTS = setOf("m4a", "mp3", "wav", "aac")
    }
}
