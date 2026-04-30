package com.dubcast.shared.domain.usecase.tts

import com.dubcast.shared.domain.model.Voice
import com.dubcast.shared.domain.repository.TtsRepository

class GetVoiceListUseCase constructor(
    private val ttsRepository: TtsRepository
) {
    suspend operator fun invoke(): Result<List<Voice>> {
        return ttsRepository.getVoices()
    }
}
