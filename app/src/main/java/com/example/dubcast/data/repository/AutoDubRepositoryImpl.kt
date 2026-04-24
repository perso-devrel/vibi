package com.example.dubcast.data.repository

import android.content.Context
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.AutoDubSpec
import com.example.dubcast.domain.repository.AutoDubJobStatus
import com.example.dubcast.domain.repository.AutoDubRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject

class AutoDubRepositoryImpl @Inject constructor(
    private val apiService: BffApiService,
    moshi: Moshi,
    private val uploader: MediaJobUploader,
    @ApplicationContext private val context: Context
) : AutoDubRepository {

    private val specAdapter = moshi.adapter(AutoDubSpec::class.java)

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        numberOfSpeakers: Int,
        ttsModel: String?
    ): Result<String> = runCatching {
        val specJson = specAdapter.toJson(
            AutoDubSpec(
                mediaType = mediaType,
                sourceLanguageCode = sourceLanguageCode,
                targetLanguageCode = targetLanguageCode,
                numberOfSpeakers = numberOfSpeakers,
                ttsModel = ttsModel
            )
        )
        uploader.upload(
            sourceUri = sourceUri,
            mediaType = mediaType,
            prefix = "autodub",
            specJson = specJson
        ) { filePart, specBody ->
            apiService.submitAutoDubJob(filePart, specBody).jobId
        }
    }

    override suspend fun pollStatus(jobId: String): Result<AutoDubJobStatus> = runCatching {
        val response = apiService.getAutoDubStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                AutoDubJobStatus.Failed(response.jobId, response.error ?: response.progressReason)

            response.status == STATUS_READY && response.dubbedAudioUrl != null ->
                AutoDubJobStatus.Ready(response.jobId, response.dubbedAudioUrl)

            else -> AutoDubJobStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    override suspend fun downloadDubbedAudio(
        audioUrl: String,
        outputFileName: String
    ): Result<String> = runCatching {
        val body = try {
            apiService.downloadDubbedAudio(audioUrl)
        } catch (e: HttpException) {
            throw e
        }
        val outputDir = File(context.filesDir, "auto_dubs").apply { if (!exists()) mkdirs() }
        val outputFile = File(outputDir, outputFileName)
        body.byteStream().use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
        outputFile.absolutePath
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
    }
}
