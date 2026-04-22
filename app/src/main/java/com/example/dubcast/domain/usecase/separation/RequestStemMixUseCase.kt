package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.repository.StemSelection
import javax.inject.Inject

class RequestStemMixUseCase @Inject constructor(
    private val repository: AudioSeparationRepository
) {
    suspend operator fun invoke(
        jobId: String,
        selections: List<StemSelection>
    ): Result<String> {
        if (selections.isEmpty()) {
            return Result.failure(IllegalArgumentException("selections must not be empty"))
        }
        if (selections.any { it.volume < 0f }) {
            return Result.failure(IllegalArgumentException("volume must be >= 0"))
        }
        return repository.requestMix(jobId, selections)
    }
}
