package com.dubcast.shared.domain.usecase.separation

import com.dubcast.shared.domain.repository.AudioSeparationRepository
import com.dubcast.shared.domain.repository.SeparationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PollSeparationUseCase constructor(
    private val repository: AudioSeparationRepository
) {
    operator fun invoke(jobId: String, intervalMs: Long = 10000L): Flow<SeparationStatus> = flow {
        while (true) {
            val status = repository.pollStatus(jobId).getOrThrow()
            emit(status)
            when (status) {
                is SeparationStatus.Ready,
                is SeparationStatus.Consumed,
                is SeparationStatus.Failed -> return@flow
                is SeparationStatus.Processing -> delay(intervalMs)
            }
        }
    }
}
