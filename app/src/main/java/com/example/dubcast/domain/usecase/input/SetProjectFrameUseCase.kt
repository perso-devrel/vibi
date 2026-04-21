package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.repository.EditProjectRepository
import javax.inject.Inject

class SetProjectFrameUseCase @Inject constructor(
    private val editProjectRepository: EditProjectRepository
) {
    suspend operator fun invoke(
        projectId: String,
        width: Int,
        height: Int,
        backgroundColorHex: String?
    ) {
        require(width > 0) { "frame width must be positive: $width" }
        require(height > 0) { "frame height must be positive: $height" }
        if (backgroundColorHex != null) {
            require(HEX_COLOR_PATTERN.matches(backgroundColorHex)) {
                "backgroundColorHex must be #RRGGBB or #AARRGGBB: $backgroundColorHex"
            }
        }
        val project = editProjectRepository.getProject(projectId)
            ?: throw IllegalArgumentException("Project not found: $projectId")
        editProjectRepository.updateProject(
            project.copy(
                frameWidth = width,
                frameHeight = height,
                backgroundColorHex = backgroundColorHex ?: project.backgroundColorHex,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        private val HEX_COLOR_PATTERN = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")
    }
}
