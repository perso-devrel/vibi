package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.dto.AutoDubSpec
import com.dubcast.shared.domain.repository.AutoDubJobStatus
import com.dubcast.shared.domain.repository.AutoDubRepository
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile

class IosAutoDubRepositoryImpl(
    private val api: BffApi,
    private val uploader: IosMediaJobUploader
) : AutoDubRepository {

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        numberOfSpeakers: Int,
        ttsModel: String?
    ): Result<String> = runCatching {
        val part = uploader.loadAsBinaryPart(sourceUri, mediaType, "autodub")
        val spec = AutoDubSpec(
            mediaType = mediaType,
            sourceLanguageCode = sourceLanguageCode,
            targetLanguageCode = targetLanguageCode,
            numberOfSpeakers = numberOfSpeakers,
            ttsModel = ttsModel
        )
        api.submitAutoDubJob(part, spec).jobId
    }

    override suspend fun pollStatus(jobId: String): Result<AutoDubJobStatus> = runCatching {
        val response = api.getAutoDubStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                AutoDubJobStatus.Failed(response.jobId, response.error ?: response.progressReason)

            response.status == STATUS_READY && response.dubbedAudioUrl != null ->
                AutoDubJobStatus.Ready(
                    jobId = response.jobId,
                    dubbedAudioUrl = response.dubbedAudioUrl,
                    dubbedVideoUrl = response.dubbedVideoUrl
                )

            else -> AutoDubJobStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun downloadDubbedAudio(
        audioUrl: String,
        outputFileName: String
    ): Result<String> = downloadToDocs(audioUrl, outputFileName, useApi = { api.downloadDubbedAudio(it) })

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun downloadDubbedVideo(
        videoUrl: String,
        outputFileName: String
    ): Result<String> = downloadToDocs(videoUrl, outputFileName, useApi = { api.downloadDubbedVideo(it) })

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun downloadToDocs(
        url: String,
        outputFileName: String,
        useApi: suspend (String) -> ByteArray
    ): Result<String> = runCatching {
        withContext(Dispatchers.Default) {
            val bytes = useApi(url)
            val docs = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).first() as String
            val outputDir = "$docs/auto_dubs"
            NSFileManager.defaultManager.createDirectoryAtPath(
                outputDir, withIntermediateDirectories = true, attributes = null, error = null
            )
            val outputPath = "$outputDir/$outputFileName"

            val nsData: NSData = bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            }
            nsData.writeToFile(outputPath, atomically = true)
            outputPath
        }
    }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
    }
}
