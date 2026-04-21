package com.example.dubcast.domain.model

data class TextOverlay(
    val id: String,
    val projectId: String,
    val text: String,
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val colorHex: String = DEFAULT_COLOR_HEX,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    val lane: Int = 0
) {
    companion object {
        const val DEFAULT_FONT_FAMILY = "noto_sans_kr"
        const val DEFAULT_FONT_SIZE_SP = 24f
        const val DEFAULT_COLOR_HEX = "#FFFFFFFF"
        const val MIN_FONT_SIZE_SP = 8f
        const val MAX_FONT_SIZE_SP = 120f
        val SUPPORTED_FONT_FAMILIES = listOf("noto_sans_kr", "noto_serif_kr")
    }
}
