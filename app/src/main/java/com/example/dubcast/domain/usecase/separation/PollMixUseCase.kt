package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.repository.MixStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class PollMixUseCase @Inject constructor(
    private val repository: AudioSeparationRepository
) {
    operator fun invoke(mixJobId: String, intervalMs: Long = 3000L): Flow<MixStatus> = flow {
        while (true) {
            val status = repository.pollMixStatus(mixJobId).getOrThrow()
            emit(status)
            when (status) {
                is MixStatus.Completed, is MixStatus.Failed -> return@flow
                is MixStatus.Processing -> delay(intervalMs)
            }
        }
    }
}
