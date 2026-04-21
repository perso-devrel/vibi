package com.example.dubcast.domain.usecase.image

import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.repository.ImageClipRepository
import com.example.dubcast.domain.util.pickLowestFreeLane
import kotlinx.coroutines.flow.first
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
        heightPct: Float = 30f,
        lane: Int? = null
    ): ImageClip {
        require(endMs > startMs) { "endMs must be greater than startMs" }
        require(startMs >= 0L) { "startMs must be non-negative" }
        // If the caller (ViewModel) already computed a cross-type lane
        // considering both images and text overlays, honour it. Otherwise
        // fall back to image-only lane allocation.
        val effectiveLane = lane ?: pickLowestFreeLane(
            existing = repository.observeClips(projectId).first(),
            startMs = startMs,
            endMs = endMs,
            laneOf = { it.lane },
            startOf = { it.startMs },
            endOf = { it.endMs }
        )
        val clip = ImageClip(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            imageUri = imageUri,
            startMs = startMs,
            endMs = endMs,
            xPct = xPct,
            yPct = yPct,
            widthPct = widthPct,
            heightPct = heightPct,
            lane = effectiveLane
        )
        repository.addClip(clip)
        return clip
    }
}
