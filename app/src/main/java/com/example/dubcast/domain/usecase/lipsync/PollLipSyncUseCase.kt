package com.example.dubcast.domain.usecase.lipsync

import com.example.dubcast.domain.repository.LipSyncRepository
import com.example.dubcast.domain.repository.LipSyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class PollLipSyncUseCase @Inject constructor(
    private val lipSyncRepository: LipSyncRepository
) {
    operator fun invoke(jobId: String, intervalMs: Long = 2000L): Flow<LipSyncStatus> = flow {
        while (true) {
            val result = lipSyncRepository.pollStatus(jobId)
            val status = result.getOrThrow()
            emit(status)
            if (status.isCompleted) break
            delay(intervalMs)
        }
    }
}
