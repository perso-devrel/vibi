package com.dubcast.shared.domain.usecase.input

import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.model.TargetLanguage
import com.dubcast.shared.domain.model.VideoInfo
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.platform.currentTimeMillis
import com.dubcast.shared.platform.generateId

class CreateProjectWithInitialVideoSegmentUseCase constructor(
    private val editProjectRepository: EditProjectRepository
) {
    suspend operator fun invoke(
        videoInfo: VideoInfo,
        targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
        targetLanguageCodes: List<String> = emptyList(),
        enableAutoDubbing: Boolean = false,
        enableAutoSubtitles: Boolean = false,
        numberOfSpeakers: Int = 1
    ): String {
        val projectId = generateId()
        val now = currentTimeMillis()
        val project = EditProject(
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
            frameWidth = videoInfo.width,
            frameHeight = videoInfo.height,
            targetLanguageCode = targetLanguageCode,
            targetLanguageCodes = targetLanguageCodes,
            enableAutoDubbing = enableAutoDubbing,
            enableAutoSubtitles = enableAutoSubtitles,
            numberOfSpeakers = numberOfSpeakers.coerceIn(1, 10)
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
