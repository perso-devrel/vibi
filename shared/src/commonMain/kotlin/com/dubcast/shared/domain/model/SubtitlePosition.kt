package com.dubcast.shared.domain.model

enum class Anchor {
    TOP, MIDDLE, BOTTOM;

    companion object {
        fun fromString(value: String): Anchor =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BOTTOM
    }
}

data class SubtitlePosition(
    val anchor: Anchor = Anchor.BOTTOM,
    val yOffsetPct: Float = 90f
)
