package com.example.dubcast.domain.model

data class EditProject(
    val projectId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR_HEX
) {
    companion object {
        const val DEFAULT_BACKGROUND_COLOR_HEX = "#000000"
    }
}
