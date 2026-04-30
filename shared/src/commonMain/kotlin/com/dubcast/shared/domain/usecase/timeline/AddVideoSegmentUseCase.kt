package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.model.VideoInfo
import com.dubcast.shared.domain.repository.SegmentRepository

class AddVideoSegmentUseCase constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(projectId: String, videoInfo: VideoInfo): Segment {
        require(videoInfo.durationMs > 0L) { "video durationMs must be positive" }
        val nextOrder = segmentRepository.getMaxOrder(projectId) + 1
        val segment = Segment(
            id = generateId(),
            projectId = projectId,
            type = SegmentType.VIDEO,
            order = nextOrder,
            sourceUri = videoInfo.uri,
            durationMs = videoInfo.durationMs,
            width = videoInfo.width,
            height = videoInfo.height
        )
        segmentRepository.addSegment(segment)
        return segment
    }
}
