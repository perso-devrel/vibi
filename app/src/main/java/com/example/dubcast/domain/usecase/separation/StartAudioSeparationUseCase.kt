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
        sourceLanguageCode: String = "auto"
    ): Result<String> {
        if (numberOfSpeakers !in 1..10) {
            return Result.failure(IllegalArgumentException("numberOfSpeakers must be in 1..10"))
        }
        return repository.startSeparation(sourceUri, mediaType, numberOfSpeakers, sourceLanguageCode)
    }
}
