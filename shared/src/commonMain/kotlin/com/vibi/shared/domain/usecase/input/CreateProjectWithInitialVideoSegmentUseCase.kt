package com.vibi.shared.domain.usecase.input

import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.VideoInfo
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.platform.generateId

class CreateProjectWithInitialVideoSegmentUseCase constructor(
    private val editProjectRepository: EditProjectRepository
) {
    suspend operator fun invoke(videoInfo: VideoInfo): String {
        val projectId = generateId()
        val now = currentTimeMillis()
        val project = EditProject(
            projectId = projectId,
            createdAt = now,
            updatedAt = now,
            frameWidth = videoInfo.width,
            frameHeight = videoInfo.height,
            // 선택한 영상의 원본 파일명(확장자 제거)을 초기 제목으로 — 사용자는 헤더에서 그대로 rename 가능.
            // 파일명이 비면 null 로 둬 UI 가 "Untitled" 로 fallback.
            title = defaultTitleFrom(videoInfo.fileName),
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

    /** 파일명에서 확장자를 떼고 trim/길이제한. 결과가 비면 null (UI 가 "Untitled" 표시). */
    private fun defaultTitleFrom(fileName: String): String? =
        fileName.substringBeforeLast('.', fileName)
            .trim()
            .take(MAX_TITLE_LEN)
            .takeIf { it.isNotBlank() }

    private companion object {
        // TimelineViewModel.MAX_DISPLAY_NAME_LEN 과 동일 — rename 시 적용되는 제한과 맞춤.
        const val MAX_TITLE_LEN = 80
    }
}
