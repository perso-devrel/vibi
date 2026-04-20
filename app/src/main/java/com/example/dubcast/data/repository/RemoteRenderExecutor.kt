package com.example.dubcast.data.repository

import android.content.Context
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.RenderConfig
import com.example.dubcast.data.remote.dto.RenderDubClip
import com.example.dubcast.data.remote.dto.RenderImageClip
import com.example.dubcast.domain.usecase.export.DubClipMixInput
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
import com.example.dubcast.domain.usecase.export.ImageClipMixInput
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

    override suspend fun burnSubtitles(
        inputVideoPath: String,
        assFilePath: String,
        outputPath: String,
        fontDir: String,
        durationMs: Long,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        return mixAudioWithVideo(
            inputVideoPath = inputVideoPath,
            dubClips = emptyList(),
            outputPath = outputPath,
            videoDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = 0L,
            assFilePath = assFilePath,
            fontDir = fontDir,
            imageClips = emptyList(),
            onProgress = onProgress
        )
    }

    override suspend fun mixAudioWithVideo(
        inputVideoPath: String,
        dubClips: List<DubClipMixInput>,
        outputPath: String,
        videoDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        assFilePath: String?,
        fontDir: String?,
        imageClips: List<ImageClipMixInput>,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            onProgress(0)

            // 1. Build multipart parts
            val videoFile = File(inputVideoPath)
            val videoPart = MultipartBody.Part.createFormData(
                "video", videoFile.name,
                videoFile.asRequestBody("video/mp4".toMediaType())
            )

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

            // Subtitle part (separate from audio parts)
            val subtitlePart = if (assFilePath != null) {
                val assFile = File(assFilePath)
                if (assFile.exists()) {
                    MultipartBody.Part.createFormData(
                        "subtitles", assFile.name,
                        assFile.asRequestBody("text/plain".toMediaType())
                    )
                } else null
            } else null

            // Image sticker parts
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

            val config = RenderConfig(
                dubClips = renderClips,
                videoDurationMs = videoDurationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                imageClips = renderImageClips
            )
            val configJson = moshi.adapter(RenderConfig::class.java).toJson(config)
            val configBody = configJson.toRequestBody("application/json".toMediaType())

            // 2. Submit render job
            onProgress(5)
            val response = apiService.submitRenderJob(
                videoPart,
                audioParts,
                subtitlePart,
                imageParts,
                configBody
            )
            val jobId = response.jobId

            onProgress(10)

            // 3. Poll for status
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

            // 4. Download result
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
