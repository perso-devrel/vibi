package com.dubcast.shared.domain.usecase.image

import com.dubcast.shared.domain.repository.ImageClipRepository

class DeleteImageClipUseCase constructor(
    private val repository: ImageClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        repository.deleteClip(clipId)
    }
}
