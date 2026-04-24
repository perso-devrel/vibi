package com.example.dubcast.domain.model

data class SubtitleClip(
    val id: String,
    val projectId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val position: SubtitlePosition,
    // Auto-subtitle fields (null = not derived from a dub clip)
    val sourceDubClipId: String? = null,
    val xPct: Float? = null,
    val yPct: Float? = null,
    val widthPct: Float? = null,
    val heightPct: Float? = null,
    // Phase 3 — origin of the cue. AUTO cues come from the BFF subtitle
    // pipeline (Perso STT + Gemini translation) and can be regenerated /
    // bulk-deleted independently of user-authored MANUAL cues.
    val source: SubtitleSource = SubtitleSource.MANUAL
) {
    /**
     * Legacy helper retained because callers still distinguish "auto-derived
     * from a dub clip" from "manually inserted". Phase 3's [SubtitleSource]
     * is broader (it also covers cues from the BFF subtitle pipeline) so
     * use [source] for new code.
     */
    val isAuto: Boolean get() = sourceDubClipId != null
    val isSticker: Boolean get() = xPct != null
}

enum class SubtitleSource { MANUAL, AUTO }
