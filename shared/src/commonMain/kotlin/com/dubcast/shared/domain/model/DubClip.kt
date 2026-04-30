package com.dubcast.shared.domain.model

data class DubClip(
    val id: String,
    val projectId: String,
    val text: String,
    val voiceId: String,
    val voiceName: String,
    val audioFilePath: String,
    val startMs: Long,
    val durationMs: Long,
    val volume: Float = 1.0f
)
