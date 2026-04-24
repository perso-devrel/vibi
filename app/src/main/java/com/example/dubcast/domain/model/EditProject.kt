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
    val enableAutoSubtitles: Boolean = false,
    // Phase 3 — speaker count handed to Perso for both subtitle and dub
    // pipelines. Affects diarization quality (esp. for translated SRTs).
    // Default 1 to keep behavior backwards-compatible for existing rows.
    val numberOfSpeakers: Int = 1,
    // Phase 3 — automatic localization pipeline state. Set by background
    // jobs that drive Perso STT (subtitles) and Perso translate (dubbing).
    // dubbedAudioPath stays null until autodub finishes; the renderer
    // substitutes it for the original segment audio when present.
    val dubbedAudioPath: String? = null,
    val autoSubtitleStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val autoDubStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val autoSubtitleJobId: String? = null,
    val autoDubJobId: String? = null,
    val autoSubtitleError: String? = null,
    val autoDubError: String? = null
) {
    /**
     * UI-level "auto-localization" toggle. The DB still stores the two
     * underlying flags so we can evolve them independently later, but the
     * sheet writes them in lockstep — treat this as the single source of
     * truth for "is automatic localization on?".
     */
    val isAutoLocalizationEnabled: Boolean
        get() = enableAutoSubtitles && enableAutoDubbing

    companion object {
        const val DEFAULT_BACKGROUND_COLOR_HEX = "#000000"
        const val DEFAULT_VIDEO_SCALE = 1f
        const val MIN_VIDEO_SCALE = 0.25f
        const val MAX_VIDEO_SCALE = 4f
        const val MAX_VIDEO_OFFSET_PCT = 100f
    }
}

enum class AutoJobStatus { IDLE, RUNNING, READY, FAILED }
