package com.example.dubcast.fake

import com.example.dubcast.domain.model.SeparationMediaType
import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.repository.MixStatus
import com.example.dubcast.domain.repository.SeparationStatus
import com.example.dubcast.domain.repository.StemSelection

class FakeAudioSeparationRepository : AudioSeparationRepository {

    var startResult: Result<String> = Result.success("sep-fake-1")
    var statusResults: MutableList<Result<SeparationStatus>> = mutableListOf()
    var mixRequestResult: Result<String> = Result.success("mix-fake-1")
    var mixStatusResults: MutableList<Result<MixStatus>> = mutableListOf()
    var stemDownloadResult: Result<String> = Result.success("/fake/stem.mp3")
    var mixDownloadResult: Result<String> = Result.success("/fake/mix.mp3")

    val mixRequests = mutableListOf<Pair<String, List<StemSelection>>>()
    var lastStartArgs: StartArgs? = null

    private var pollIndex = 0
    private var mixPollIndex = 0

    data class StartArgs(
        val sourceUri: String,
        val mediaType: SeparationMediaType,
        val numberOfSpeakers: Int,
        val sourceLanguageCode: String
    )

    override suspend fun startSeparation(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String
    ): Result<String> {
        lastStartArgs = StartArgs(sourceUri, mediaType, numberOfSpeakers, sourceLanguageCode)
        return startResult
    }

    override suspend fun pollStatus(jobId: String): Result<SeparationStatus> {
        if (statusResults.isEmpty()) return Result.failure(IllegalStateException("no status"))
        val result = statusResults[pollIndex.coerceAtMost(statusResults.lastIndex)]
        pollIndex++
        return result
    }

    override suspend fun downloadStem(stemUrl: String, outputFileName: String): Result<String> =
        stemDownloadResult

    override suspend fun requestMix(
        jobId: String,
        selections: List<StemSelection>
    ): Result<String> {
        mixRequests += jobId to selections
        return mixRequestResult
    }

    override suspend fun pollMixStatus(mixJobId: String): Result<MixStatus> {
        if (mixStatusResults.isEmpty()) return Result.failure(IllegalStateException("no status"))
        val result = mixStatusResults[mixPollIndex.coerceAtMost(mixStatusResults.lastIndex)]
        mixPollIndex++
        return result
    }

    override suspend fun downloadMix(
        mixJobId: String,
        downloadUrl: String,
        outputFileName: String
    ): Result<String> = mixDownloadResult
}
