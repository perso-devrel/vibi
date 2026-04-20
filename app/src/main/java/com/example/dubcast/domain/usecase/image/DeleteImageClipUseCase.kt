package com.example.dubcast.domain.usecase.image

import com.example.dubcast.domain.repository.ImageClipRepository
import javax.inject.Inject

class DeleteImageClipUseCase @Inject constructor(
    private val repository: ImageClipRepository
) {
    suspend operator fun invoke(clipId: String) {
        repository.deleteClip(clipId)
    }
}
