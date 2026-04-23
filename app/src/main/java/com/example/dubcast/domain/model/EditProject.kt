package com.example.dubcast.domain.model

data class EditProject(
    val projectId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f,
    val targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
    val enableAutoDubbing: Boolean = false,
    val enableAutoSubtitles: Boolean = false
) {
    companion object {
        const val DEFAULT_BACKGROUND_COLOR_HEX = "#000000"
        const val DEFAULT_VIDEO_SCALE = 1f
        const val MIN_VIDEO_SCALE = 0.25f
        const val MAX_VIDEO_SCALE = 4f
        const val MAX_VIDEO_OFFSET_PCT = 100f
    }
}
