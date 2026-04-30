package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.domain.repository.SubtitleClipRepository

class DeleteSubtitleClipUseCase constructor(
    private val subtitleClipRepository: SubtitleClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        subtitleClipRepository.deleteClip(clipId)
    }
}
