package com.dubcast.shared.domain.usecase.input

import com.dubcast.shared.platform.currentTimeMillis

import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.util.isValidHexColor

class SetProjectFrameUseCase constructor(
    private val editProjectRepository: EditProjectRepository
) {
    suspend operator fun invoke(
        projectId: String,
        width: Int,
        height: Int,
        backgroundColorHex: String?,
        videoScale: Float? = null,
        videoOffsetXPct: Float? = null,
        videoOffsetYPct: Float? = null
    ) {
        require(width > 0) { "frame width must be positive: $width" }
        require(height > 0) { "frame height must be positive: $height" }
        if (backgroundColorHex != null) {
            require(isValidHexColor(backgroundColorHex)) {
                "backgroundColorHex must be #RRGGBB or #AARRGGBB: $backgroundColorHex"
            }
        }
        if (videoScale != null) {
            require(videoScale in EditProject.MIN_VIDEO_SCALE..EditProject.MAX_VIDEO_SCALE) {
                "videoScale must be in ${EditProject.MIN_VIDEO_SCALE}..${EditProject.MAX_VIDEO_SCALE}: $videoScale"
            }
        }
        if (videoOffsetXPct != null) {
            require(videoOffsetXPct in -EditProject.MAX_VIDEO_OFFSET_PCT..EditProject.MAX_VIDEO_OFFSET_PCT) {
                "videoOffsetXPct out of range: $videoOffsetXPct"
            }
        }
        if (videoOffsetYPct != null) {
            require(videoOffsetYPct in -EditProject.MAX_VIDEO_OFFSET_PCT..EditProject.MAX_VIDEO_OFFSET_PCT) {
                "videoOffsetYPct out of range: $videoOffsetYPct"
            }
        }
        val project = editProjectRepository.getProject(projectId)
            ?: throw IllegalArgumentException("Project not found: $projectId")
        editProjectRepository.updateProject(
            project.copy(
                frameWidth = width,
                frameHeight = height,
                backgroundColorHex = backgroundColorHex ?: project.backgroundColorHex,
                videoScale = videoScale ?: project.videoScale,
                videoOffsetXPct = videoOffsetXPct ?: project.videoOffsetXPct,
                videoOffsetYPct = videoOffsetYPct ?: project.videoOffsetYPct,
                updatedAt = currentTimeMillis()
            )
        )
    }
}
