package com.dubcast.shared.domain.usecase.lipsync

import com.dubcast.shared.domain.repository.LipSyncRepository
import com.dubcast.shared.domain.repository.LipSyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PollLipSyncUseCase constructor(
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
