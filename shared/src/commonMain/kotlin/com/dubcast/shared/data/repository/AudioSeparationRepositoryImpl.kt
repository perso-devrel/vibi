package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.api.BinaryPart
import com.dubcast.shared.data.remote.dto.MixRequest
import com.dubcast.shared.data.remote.dto.MixStemRequest
import com.dubcast.shared.data.remote.dto.SeparationSpec
import com.dubcast.shared.domain.model.SeparationMediaType
import com.dubcast.shared.domain.model.Stem
import com.dubcast.shared.domain.repository.AudioSeparationRepository
import com.dubcast.shared.domain.repository.MixStatus
import com.dubcast.shared.domain.repository.SeparationStatus
import com.dubcast.shared.domain.repository.StemSelection
import com.dubcast.shared.platform.readFileBytes
import com.dubcast.shared.platform.saveBytesToCache
import io.ktor.client.plugins.ClientRequestException

class AudioSeparationRepositoryImpl(
    private val api: BffApi
) : AudioSeparationRepository {

    override suspend fun startSeparation(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String,
        trimStartMs: Long?,
        trimEndMs: Long?
    ): Result<String> = runCatching {
        val (ext, contentType) = when (mediaType) {
            SeparationMediaType.VIDEO -> "mp4" to "video/mp4"
            SeparationMediaType.AUDIO -> "mp3" to "audio/mpeg"
        }
        val bytes = readFileBytes(sourceUri)
        val part = BinaryPart(
            fieldName = "file",
            filename = "separation.$ext",
            bytes = bytes,
            contentType = contentType
        )
        val spec = SeparationSpec(
            mediaType = mediaType.wireName,
            numberOfSpeakers = numberOfSpeakers,
            sourceLanguageCode = sourceLanguageCode,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
        api.startSeparation(file = part, spec = spec).jobId
    }

    override suspend fun pollStatus(jobId: String): Result<SeparationStatus> = runCatching {
        val response = api.getSeparationStatus(jobId)
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
            val bytes = api.downloadStem(stemUrl)
            saveBytesToCache("stems_$outputFileName", bytes)
        }

    override suspend fun requestMix(
        jobId: String,
        selections: List<StemSelection>
    ): Result<String> = runCatching {
        val body = MixRequest(
            stems = selections.map { MixStemRequest(it.stemId, it.volume) }
        )
        api.requestStemMix(jobId, body).mixJobId
    }

    override suspend fun pollMixStatus(mixJobId: String): Result<MixStatus> = runCatching {
        val response = api.getMixStatus(mixJobId)
        when (response.status) {
            MIX_STATUS_COMPLETED -> MixStatus.Completed(
                mixJobId = response.mixJobId,
                downloadUrl = response.downloadUrl
                    ?: error("COMPLETED without downloadUrl")
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
        val bytes = try {
            api.downloadMix(downloadUrl)
        } catch (e: ClientRequestException) {
            if (e.response.status.value != HTTP_FORBIDDEN) throw e
            val refreshed = api.getMixStatus(mixJobId)
            val freshUrl = refreshed.downloadUrl
                ?: error("mix $mixJobId has no downloadUrl to refresh")
            api.downloadMix(freshUrl)
        }
        saveBytesToCache("mix_$outputFileName", bytes)
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
        const val MIX_STATUS_COMPLETED = "COMPLETED"
        const val MIX_STATUS_FAILED = "FAILED"
        const val HTTP_FORBIDDEN = 403
    }
}
