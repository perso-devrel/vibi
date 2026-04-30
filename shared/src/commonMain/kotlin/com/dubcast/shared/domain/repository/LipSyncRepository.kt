package com.dubcast.shared.domain.repository

data class LipSyncStatus(
    val jobId: String,
    val progress: Int,
    val isCompleted: Boolean,
    val resultVideoPath: String?
)

interface LipSyncRepository {
    suspend fun requestLipSync(
        videoUri: String,
        audioFilePath: String,
        startMs: Long,
        durationMs: Long
    ): Result<String>

    suspend fun pollStatus(jobId: String): Result<LipSyncStatus>

    suspend fun downloadResult(jobId: String): Result<String>
}
