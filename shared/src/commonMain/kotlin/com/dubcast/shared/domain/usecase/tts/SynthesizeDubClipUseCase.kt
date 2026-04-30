package com.dubcast.shared.domain.usecase.tts

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.repository.DubClipRepository
import com.dubcast.shared.domain.repository.TtsRepository

class SynthesizeDubClipUseCase constructor(
    private val ttsRepository: TtsRepository,
    private val dubClipRepository: DubClipRepository
) {
    suspend operator fun invoke(
        projectId: String,
        text: String,
        voiceId: String,
        voiceName: String,
        startMs: Long
    ): Result<DubClip> {
        return ttsRepository.synthesize(text, voiceId).map { ttsResult ->
            val clip = DubClip(
                id = generateId(),
                projectId = projectId,
                text = text,
                voiceId = voiceId,
                voiceName = voiceName,
                audioFilePath = ttsResult.localAudioPath,
                startMs = startMs,
                durationMs = ttsResult.durationMs
            )
            dubClipRepository.addClip(clip)
            clip
        }
    }
}
