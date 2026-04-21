package com.example.dubcast.data.repository

import android.content.Context
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.RenderConfig
import com.example.dubcast.data.remote.dto.RenderDubClip
import com.example.dubcast.data.remote.dto.RenderFrame
import com.example.dubcast.data.remote.dto.RenderImageClip
import com.example.dubcast.data.remote.dto.RenderSegment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.usecase.export.DubClipMixInput
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
import com.example.dubcast.domain.usecase.export.FrameInput
import com.example.dubcast.domain.usecase.export.ImageClipMixInput
import com.example.dubcast.domain.usecase.export.SegmentInput
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

class RemoteRenderExecutor @Inject constructor(
    private val apiService: BffApiService,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) : FfmpegExecutor {

    override suspend fun renderProject(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput>,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        frame: FrameInput?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(segments.isNotEmpty()) { "segments must not be empty" }
            onProgress(0)

            val sortedSegments = segments.sortedBy { it.order }

            // Video / image-segment parts (keyed by segment order)
            val videoParts = mutableListOf<MultipartBody.Part>()
            val segmentImageParts = mutableListOf<MultipartBody.Part>()
            val renderSegments = mutableListOf<RenderSegment>()

            for (seg in sortedSegments) {
                val file = File(seg.sourceFilePath)
                when (seg.type) {
                    SegmentType.VIDEO -> {
                        val key = "video_${seg.order}"
                        videoParts.add(
                            MultipartBody.Part.createFormData(
                                key, file.name,
                                file.asRequestBody("video/mp4".toMediaType())
                            )
                        )
                        renderSegments.add(
                            RenderSegment(
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
                        )
                    }
                    SegmentType.IMAGE -> {
                        val key = "segment_image_${seg.order}"
                        segmentImageParts.add(
                            MultipartBody.Part.createFormData(
                                key, file.name,
                                file.asRequestBody("image/*".toMediaType())
                            )
                        )
                        renderSegments.add(
                            RenderSegment(
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
                        )
                    }
                }
            }

            // Audio (dub) parts
            val audioParts = mutableListOf<MultipartBody.Part>()
            val renderClips = mutableListOf<RenderDubClip>()
            for ((index, clip) in dubClips.withIndex()) {
                val key = "audio_$index"
                val audioFile = File(clip.audioFilePath)
                audioParts.add(
                    MultipartBody.Part.createFormData(
                        key, audioFile.name,
                        audioFile.asRequestBody("audio/mpeg".toMediaType())
                    )
                )
                renderClips.add(
                    RenderDubClip(
                        audioFileKey = key,
                        startMs = clip.startMs,
                        durationMs = 0,
                        volume = clip.volume
                    )
                )
            }

            // Sticker image parts (kept from Task 2)
            val imageParts = mutableListOf<MultipartBody.Part>()
            val renderImageClips = mutableListOf<RenderImageClip>()
            for ((index, clip) in imageClips.withIndex()) {
                val key = "image_$index"
                val imageFile = File(clip.imageFilePath)
                imageParts.add(
                    MultipartBody.Part.createFormData(
                        key, imageFile.name,
                        imageFile.asRequestBody("image/*".toMediaType())
                    )
                )
                renderImageClips.add(
                    RenderImageClip(
                        imageFileKey = key,
                        startMs = clip.startMs,
                        endMs = clip.endMs,
                        xPct = clip.xPct,
                        yPct = clip.yPct,
                        widthPct = clip.widthPct,
                        heightPct = clip.heightPct
                    )
                )
            }

            val subtitlePart = if (assFilePath != null) {
                val assFile = File(assFilePath)
                if (assFile.exists()) {
                    MultipartBody.Part.createFormData(
                        "subtitles", assFile.name,
                        assFile.asRequestBody("text/plain".toMediaType())
                    )
                } else null
            } else null

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
                }
            )
            val configJson = moshi.adapter(RenderConfig::class.java).toJson(config)
            val configBody = configJson.toRequestBody("application/json".toMediaType())

            onProgress(5)
            val response = apiService.submitRenderJob(
                videoFiles = videoParts,
                audioFiles = audioParts,
                subtitles = subtitlePart,
                imageFiles = imageParts,
                segmentImageFiles = segmentImageParts,
                config = configBody
            )
            val jobId = response.jobId

            onProgress(10)

            val maxPollMs = 15 * 60 * 1000L
            val startTime = System.currentTimeMillis()

            while (currentCoroutineContext().isActive) {
                if (System.currentTimeMillis() - startTime > maxPollMs) {
                    throw RuntimeException("렌더링 시간 초과 (15분)")
                }

                val status = apiService.getRenderStatus(jobId)

                when (status.status) {
                    "COMPLETED" -> {
                        onProgress(90)
                        break
                    }
                    "FAILED" -> {
                        throw RuntimeException(status.error ?: "서버 렌더링 실패")
                    }
                    else -> {
                        val mappedProgress = 10 + (status.progress * 0.8f).toInt()
                        onProgress(mappedProgress.coerceIn(10, 89))
                    }
                }

                delay(2000)
            }

            val responseBody = apiService.downloadRenderResult(jobId)
            val outputFile = File(outputPath)
            responseBody.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            onProgress(100)
            Result.success(outputPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
