package com.example.dubcast.domain.model

data class VideoInfo(
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)
