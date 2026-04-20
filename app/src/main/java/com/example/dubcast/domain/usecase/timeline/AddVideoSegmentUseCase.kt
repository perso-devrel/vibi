package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.repository.SegmentRepository
import java.util.UUID
import javax.inject.Inject

class AddVideoSegmentUseCase @Inject constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(projectId: String, videoInfo: VideoInfo): Segment {
        require(videoInfo.durationMs > 0L) { "video durationMs must be positive" }
        val nextOrder = segmentRepository.getMaxOrder(projectId) + 1
        val segment = Segment(
            id = UUID.randomUUID().toString(),
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
