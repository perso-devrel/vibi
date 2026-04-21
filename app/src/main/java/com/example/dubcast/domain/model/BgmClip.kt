package com.example.dubcast.domain.model

data class BgmClip(
    val id: String,
    val projectId: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val startMs: Long,
    val volumeScale: Float = 1.0f
) {
    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
    }
}
