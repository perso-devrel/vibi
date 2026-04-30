package com.dubcast.shared.domain.usecase.image

import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.repository.ImageClipRepository

class UpdateImageClipUseCase constructor(
    private val repository: ImageClipRepository
) {
    suspend operator fun invoke(clip: ImageClip) {
        require(clip.endMs > clip.startMs) { "endMs must be greater than startMs" }
        repository.updateClip(clip)
    }
}
