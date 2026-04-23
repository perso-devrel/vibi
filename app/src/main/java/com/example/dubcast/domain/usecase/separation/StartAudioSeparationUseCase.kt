package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.model.SeparationMediaType
import com.example.dubcast.domain.repository.AudioSeparationRepository
import javax.inject.Inject

class StartAudioSeparationUseCase @Inject constructor(
    private val repository: AudioSeparationRepository
) {
    suspend operator fun invoke(
        sourceUri: String,
        mediaType: SeparationMediaType,
        numberOfSpeakers: Int,
        sourceLanguageCode: String = "auto",
        trimStartMs: Long? = null,
        trimEndMs: Long? = null
    ): Result<String> {
        if (numberOfSpeakers !in 1..10) {
            return Result.failure(IllegalArgumentException("numberOfSpeakers must be in 1..10"))
        }
        if (trimStartMs != null || trimEndMs != null) {
            if (trimStartMs == null || trimEndMs == null) {
                return Result.failure(IllegalArgumentException("trimStartMs and trimEndMs must be set together"))
            }
            if (trimStartMs < 0L || trimEndMs <= trimStartMs) {
                return Result.failure(IllegalArgumentException("invalid trim range"))
            }
        }
        return repository.startSeparation(
            sourceUri = sourceUri,
            mediaType = mediaType,
            numberOfSpeakers = numberOfSpeakers,
            sourceLanguageCode = sourceLanguageCode,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs
        )
    }
}
