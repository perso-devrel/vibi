package com.example.dubcast.domain.usecase.subtitle

import com.example.dubcast.domain.repository.SubtitleClipRepository
import javax.inject.Inject

class DeleteSubtitleClipUseCase @Inject constructor(
    private val subtitleClipRepository: SubtitleClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        subtitleClipRepository.deleteClip(clipId)
    }
}
