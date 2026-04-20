package com.example.dubcast.domain.usecase.image

import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.repository.ImageClipRepository
import javax.inject.Inject

class UpdateImageClipUseCase @Inject constructor(
    private val repository: ImageClipRepository
) {
    suspend operator fun invoke(clip: ImageClip) {
        require(clip.endMs > clip.startMs) { "endMs must be greater than startMs" }
        repository.updateClip(clip)
    }
}
