package com.dubcast.shared.domain.model

data class BgmClip(
    val id: String,
    val projectId: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val startMs: Long,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
) {
    /** 속도 적용된 timeline 상 길이 — 음원분리 stems 도 동일 속도로 재생됨. */
    val effectiveDurationMs: Long
        get() = if (speedScale > 0f) (sourceDurationMs / speedScale).toLong() else sourceDurationMs

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}
