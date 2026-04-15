package com.example.dubcast.domain.usecase.subtitle

import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.repository.SubtitleClipRepository
import javax.inject.Inject

class EditSubtitleClipUseCase @Inject constructor(
    private val subtitleClipRepository: SubtitleClipRepository
) {
    suspend operator fun invoke(
        clip: SubtitleClip,
        text: String? = null,
        startMs: Long? = null,
        endMs: Long? = null,
        position: SubtitlePosition? = null
    ): SubtitleClip {
        val updated = clip.copy(
            text = text ?: clip.text,
            startMs = startMs ?: clip.startMs,
            endMs = endMs ?: clip.endMs,
            position = position ?: clip.position
        )
        require(updated.endMs > updated.startMs) { "endMs must be greater than startMs" }
        subtitleClipRepository.updateClip(updated)
        return updated
    }
}
