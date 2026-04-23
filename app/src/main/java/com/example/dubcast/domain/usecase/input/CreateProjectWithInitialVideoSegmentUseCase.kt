package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.TargetLanguage
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.repository.EditProjectRepository
import java.util.UUID
import javax.inject.Inject

class CreateProjectWithInitialVideoSegmentUseCase @Inject constructor(
    private val editProjectRepository: EditProjectRepository
) {
    suspend operator fun invoke(
        videoInfo: VideoInfo,
        targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
        enableAutoDubbing: Boolean = false,
        enableAutoSubtitles: Boolean = false
    ): String {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = EditProject(
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
            frameWidth = videoInfo.width,
            frameHeight = videoInfo.height,
            targetLanguageCode = targetLanguageCode,
            enableAutoDubbing = enableAutoDubbing,
            enableAutoSubtitles = enableAutoSubtitles
        )
        val segment = Segment(
            id = "${projectId}_seg0",
            projectId = projectId,
            type = SegmentType.VIDEO,
            order = 0,
            sourceUri = videoInfo.uri,
            durationMs = videoInfo.durationMs,
            width = videoInfo.width,
            height = videoInfo.height
        )
        editProjectRepository.createProjectWithSegment(project, segment)
        return projectId
    }
}
