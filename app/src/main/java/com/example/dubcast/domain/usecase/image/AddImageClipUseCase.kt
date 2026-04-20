package com.example.dubcast.domain.usecase.image

import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.repository.ImageClipRepository
import java.util.UUID
import javax.inject.Inject

class AddImageClipUseCase @Inject constructor(
    private val repository: ImageClipRepository
) {
    suspend operator fun invoke(
        projectId: String,
        imageUri: String,
        startMs: Long,
        endMs: Long,
        xPct: Float = 50f,
        yPct: Float = 50f,
        widthPct: Float = 30f,
        heightPct: Float = 30f
    ): ImageClip {
        require(endMs > startMs) { "endMs must be greater than startMs" }
        require(startMs >= 0L) { "startMs must be non-negative" }
        val clip = ImageClip(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            imageUri = imageUri,
            startMs = startMs,
            endMs = endMs,
            xPct = xPct,
            yPct = yPct,
            widthPct = widthPct,
            heightPct = heightPct
        )
        repository.addClip(clip)
        return clip
    }
}
