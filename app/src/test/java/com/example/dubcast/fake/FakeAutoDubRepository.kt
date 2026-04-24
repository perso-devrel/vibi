package com.example.dubcast.fake

import com.example.dubcast.domain.repository.AutoDubJobStatus
import com.example.dubcast.domain.repository.AutoDubRepository

class FakeAutoDubRepository : AutoDubRepository {

    var submitResult: Result<String> = Result.success("dub-fake-1")
    private val statusSequence = FakePollSequence<Result<AutoDubJobStatus>>()
    val statusResults: MutableList<Result<AutoDubJobStatus>> get() = statusSequence.results
    var downloadResult: Result<String> = Result.success("/fake/autodub.mp3")

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String,
        numberOfSpeakers: Int,
        ttsModel: String?
    ): Result<String> = submitResult

    override suspend fun pollStatus(jobId: String): Result<AutoDubJobStatus> =
        statusSequence.next { Result.failure(IllegalStateException("no status")) }

    override suspend fun downloadDubbedAudio(
        audioUrl: String,
        outputFileName: String
    ): Result<String> = downloadResult
}
