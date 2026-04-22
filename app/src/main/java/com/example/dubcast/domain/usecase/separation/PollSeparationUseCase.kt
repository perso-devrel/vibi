package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.repository.SeparationStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class PollSeparationUseCase @Inject constructor(
    private val repository: AudioSeparationRepository
) {
    operator fun invoke(jobId: String, intervalMs: Long = 5000L): Flow<SeparationStatus> = flow {
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
