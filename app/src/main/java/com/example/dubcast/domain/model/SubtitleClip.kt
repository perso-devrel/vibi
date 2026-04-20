package com.example.dubcast.domain.model

data class SubtitleClip(
    val id: String,
    val projectId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val position: SubtitlePosition,
    // Auto-subtitle fields (null = manual subtitle)
    val sourceDubClipId: String? = null,
    val xPct: Float? = null,
    val yPct: Float? = null,
    val widthPct: Float? = null,
    val heightPct: Float? = null
) {
    val isAuto: Boolean get() = sourceDubClipId != null
    val isSticker: Boolean get() = xPct != null
}
