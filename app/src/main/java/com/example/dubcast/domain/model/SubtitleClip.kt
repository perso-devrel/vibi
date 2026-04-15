package com.example.dubcast.domain.model

data class SubtitleClip(
    val id: String,
    val projectId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val position: SubtitlePosition
)
