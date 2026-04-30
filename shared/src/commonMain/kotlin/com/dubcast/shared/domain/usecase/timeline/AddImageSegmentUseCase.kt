package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.ImageInfo
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.repository.SegmentRepository

class AddImageSegmentUseCase constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(
        projectId: String,
        imageInfo: ImageInfo,
        durationMs: Long = DEFAULT_DURATION_MS
    ): Segment {
        require(durationMs >= MIN_DURATION_MS) { "image segment duration must be >= ${MIN_DURATION_MS}ms" }
        val nextOrder = segmentRepository.getMaxOrder(projectId) + 1
        val segment = Segment(
            id = generateId(),
            projectId = projectId,
            type = SegmentType.IMAGE,
            order = nextOrder,
            sourceUri = imageInfo.uri,
            durationMs = durationMs,
            width = imageInfo.width,
            height = imageInfo.height,
            imageXPct = 50f,
            imageYPct = 50f,
            imageWidthPct = 50f,
            imageHeightPct = 50f
        )
        segmentRepository.addSegment(segment)
        return segment
    }

    companion object {
        const val DEFAULT_DURATION_MS = 3_000L
        const val MIN_DURATION_MS = 500L
    }
}
