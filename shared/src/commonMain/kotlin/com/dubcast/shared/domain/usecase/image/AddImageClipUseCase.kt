package com.dubcast.shared.domain.usecase.image

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.repository.ImageClipRepository
import com.dubcast.shared.domain.util.pickLowestFreeLane
import kotlinx.coroutines.flow.first

class AddImageClipUseCase constructor(
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
            id = generateId(),
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
