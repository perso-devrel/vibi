package com.example.dubcast.domain.usecase.tts

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.repository.DubClipRepository
import com.example.dubcast.domain.repository.TtsRepository
import java.util.UUID
import javax.inject.Inject

class SynthesizeDubClipUseCase @Inject constructor(
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
                id = UUID.randomUUID().toString(),
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
