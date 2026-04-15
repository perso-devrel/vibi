package com.example.dubcast.data.repository

import android.content.Context
import android.net.Uri
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.domain.repository.LipSyncRepository
import com.example.dubcast.domain.repository.LipSyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import javax.inject.Inject

class LipSyncRepositoryImpl @Inject constructor(
    private val apiService: BffApiService,
    @ApplicationContext private val context: Context
) : LipSyncRepository {

    override suspend fun requestLipSync(
        videoUri: String,
        audioFilePath: String,
        startMs: Long,
        durationMs: Long
    ): Result<String> {
        return runCatching {
            val audioFile = File(audioFilePath)
            val audioPart = MultipartBody.Part.createFormData(
                "audio",
                audioFile.name,
                audioFile.asRequestBody("audio/mpeg".toMediaType())
            )

            // Copy content:// URI to a temp file for upload
            val videoTempFile = File(context.cacheDir, "lipsync_video_${UUID.randomUUID()}.mp4")
            context.contentResolver.openInputStream(Uri.parse(videoUri))?.use { input ->
                videoTempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw java.io.IOException("Cannot open video URI: $videoUri")

            val videoPart = MultipartBody.Part.createFormData(
                "video",
                videoTempFile.name,
                videoTempFile.asRequestBody("video/mp4".toMediaType())
            )

            try {
                val response = apiService.requestLipSync(videoPart, audioPart, startMs, durationMs)
                response.jobId
            } finally {
                videoTempFile.delete()
            }
        }
    }

    override suspend fun pollStatus(jobId: String): Result<LipSyncStatus> {
        return runCatching {
            val response = apiService.getLipSyncStatus(jobId)
            LipSyncStatus(
                jobId = response.jobId,
                progress = response.progress,
                isCompleted = response.status == "COMPLETED",
                resultVideoPath = response.resultVideoUrl
            )
        }
    }

    override suspend fun downloadResult(jobId: String): Result<String> {
        return runCatching {
            val responseBody = apiService.downloadLipSyncResult(jobId)
            val outputDir = File(context.filesDir, "lipsync_results")
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, "${UUID.randomUUID()}.mp4")
            responseBody.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.absolutePath
        }
    }
}
