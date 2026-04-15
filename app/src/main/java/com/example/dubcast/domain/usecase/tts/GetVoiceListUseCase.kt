package com.example.dubcast.domain.usecase.tts

import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.TtsRepository
import javax.inject.Inject

class GetVoiceListUseCase @Inject constructor(
    private val ttsRepository: TtsRepository
) {
    suspend operator fun invoke(): Result<List<Voice>> {
        return ttsRepository.getVoices()
    }
}
