package com.example.dubcast.fake

import com.example.dubcast.domain.repository.LipSyncRepository
import com.example.dubcast.domain.repository.LipSyncStatus

class FakeLipSyncRepository : LipSyncRepository {

    var requestResult: Result<String> = Result.success("job-123")
    var statusResults: MutableList<Result<LipSyncStatus>> = mutableListOf(
        Result.success(LipSyncStatus("job-123", 50, false, null)),
        Result.success(LipSyncStatus("job-123", 100, true, "/fake/lipsync.mp4"))
    )
    private var pollIndex = 0
    var downloadResult: Result<String> = Result.success("/fake/lipsync.mp4")

    override suspend fun requestLipSync(
        videoUri: String,
        audioFilePath: String,
        startMs: Long,
        durationMs: Long
    ): Result<String> = requestResult

    override suspend fun pollStatus(jobId: String): Result<LipSyncStatus> {
        val result = statusResults[pollIndex.coerceAtMost(statusResults.lastIndex)]
        pollIndex++
        return result
    }

    override suspend fun downloadResult(jobId: String): Result<String> = downloadResult
}
