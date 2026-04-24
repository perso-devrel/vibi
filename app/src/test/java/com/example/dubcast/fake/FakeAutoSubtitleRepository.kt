package com.example.dubcast.fake

import com.example.dubcast.domain.repository.AutoSubtitleRepository
import com.example.dubcast.domain.repository.AutoSubtitleStatus

class FakeAutoSubtitleRepository : AutoSubtitleRepository {

    var submitResult: Result<String> = Result.success("sub-fake-1")
    private val statusSequence = FakePollSequence<Result<AutoSubtitleStatus>>()
    val statusResults: MutableList<Result<AutoSubtitleStatus>> get() = statusSequence.results
    var srtResult: Result<String> = Result.success("")

    override suspend fun submit(
        sourceUri: String,
        mediaType: String,
        sourceLanguageCode: String,
        targetLanguageCode: String?,
        numberOfSpeakers: Int
    ): Result<String> = submitResult

    override suspend fun pollStatus(jobId: String): Result<AutoSubtitleStatus> =
        statusSequence.next { Result.failure(IllegalStateException("no status")) }

    override suspend fun fetchSrt(srtUrl: String): Result<String> = srtResult
}
