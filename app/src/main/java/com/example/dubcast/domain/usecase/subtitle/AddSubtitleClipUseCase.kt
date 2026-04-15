package com.example.dubcast.domain.usecase.subtitle

import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.repository.SubtitleClipRepository
import java.util.UUID
import javax.inject.Inject

class AddSubtitleClipUseCase @Inject constructor(
    private val subtitleClipRepository: SubtitleClipRepository
) {
    suspend operator fun invoke(
        projectId: String,
        text: String,
        startMs: Long,
        endMs: Long,
        position: SubtitlePosition
    ): SubtitleClip {
        require(endMs > startMs) { "endMs must be greater than startMs" }
        val clip = SubtitleClip(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            text = text,
            startMs = startMs,
            endMs = endMs,
            position = position
        )
        subtitleClipRepository.addClip(clip)
        return clip
    }
}
