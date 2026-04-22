package com.example.dubcast.data.repository

import android.content.Context
import android.net.Uri
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.MixRequest
import com.example.dubcast.data.remote.dto.MixStemRequest
import com.example.dubcast.data.remote.dto.SeparationSpec
import com.example.dubcast.domain.model.SeparationMediaType
import com.example.dubcast.domain.model.Stem
import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.repository.MixStatus
import com.example.dubcast.domain.repository.SeparationStatus
import com.example.dubcast.domain.repository.StemSelection
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.util.UUID
import javax.inject.Inject

class AudioSeparationRepositoryImpl @Inject constructor(
    private val apiService: BffApiService,
    moshi: Moshi,
    @ApplicationContext private val context: Context
) : AudioSeparationRepository {

    private val specAdapter = moshi.adapter(SeparationSpec::class.java)

    override suspend fun startSeparation(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String
    ): Result<String> = runCatching {
        val (ext, contentType) = when (mediaType) {
            SeparationMediaType.VIDEO -> "mp4" to "video/mp4"
            SeparationMediaType.AUDIO -> "mp3" to "audio/mpeg"
        }

        val tempFile = File(context.cacheDir, "separation_${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw java.io.IOException("Cannot open source URI: $sourceUri")

        try {
            val filePart = MultipartBody.Part.createFormData(
                "file",
                tempFile.name,
                tempFile.asRequestBody(contentType.toMediaType())
            )
            val specJson = specAdapter.toJson(
                SeparationSpec(mediaType.wireName, numberOfSpeakers, sourceLanguageCode)
            )
            val specBody = specJson.toRequestBody("application/json".toMediaType())

            apiService.startSeparation(filePart, specBody).jobId
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun pollStatus(jobId: String): Result<SeparationStatus> = runCatching {
        val response = apiService.getSeparationStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                SeparationStatus.Failed(response.jobId, response.progressReason)

            response.mixJobId != null ->
                SeparationStatus.Consumed(response.jobId, response.mixJobId)

            response.status == STATUS_READY && response.stems.isNotEmpty() ->
                SeparationStatus.Ready(
                    jobId = response.jobId,
                    stems = response.stems.map {
                        Stem(
                            stemId = it.stemId,
                            label = it.label,
                            url = it.url,
                            kind = Stem.kindFromId(it.stemId),
                            speakerIndex = Stem.speakerIndexFromId(it.stemId)
                        )
                    }
                )

            else -> SeparationStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    override suspend fun downloadStem(stemUrl: String, outputFileName: String): Result<String> =
        runCatching {
            val responseBody = apiService.downloadStem(stemUrl)
            writeToFile(responseBody, "separation_stems", outputFileName)
        }

    override suspend fun requestMix(
        jobId: String,
        selections: List<StemSelection>
    ): Result<String> = runCatching {
        val body = MixRequest(
            stems = selections.map { MixStemRequest(it.stemId, it.volume) }
        )
        apiService.requestStemMix(jobId, body).mixJobId
    }

    override suspend fun pollMixStatus(mixJobId: String): Result<MixStatus> = runCatching {
        val response = apiService.getMixStatus(mixJobId)
        when (response.status) {
            MIX_STATUS_COMPLETED -> MixStatus.Completed(
                mixJobId = response.mixJobId,
                downloadUrl = response.downloadUrl
                    ?: throw IllegalStateException("COMPLETED without downloadUrl")
            )
            MIX_STATUS_FAILED -> MixStatus.Failed(response.mixJobId)
            else -> MixStatus.Processing(response.mixJobId, response.progress)
        }
    }

    override suspend fun downloadMix(
        mixJobId: String,
        downloadUrl: String,
        outputFileName: String
    ): Result<String> = runCatching {
        val body = try {
            apiService.downloadMix(downloadUrl)
        } catch (e: HttpException) {
            if (e.code() != HTTP_FORBIDDEN) throw e
            val refreshed = apiService.getMixStatus(mixJobId)
            val freshUrl = refreshed.downloadUrl
                ?: throw IllegalStateException("mix $mixJobId has no downloadUrl to refresh")
            apiService.downloadMix(freshUrl)
        }
        writeToFile(body, "separation_mixes", outputFileName)
    }

    private fun writeToFile(
        body: okhttp3.ResponseBody,
        subDir: String,
        name: String
    ): String {
        val outputDir = File(context.filesDir, subDir).apply { if (!exists()) mkdirs() }
        val outputFile = File(outputDir, name)
        body.byteStream().use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outputFile.absolutePath
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
        const val MIX_STATUS_COMPLETED = "COMPLETED"
        const val MIX_STATUS_FAILED = "FAILED"
        const val HTTP_FORBIDDEN = 403
    }
}
