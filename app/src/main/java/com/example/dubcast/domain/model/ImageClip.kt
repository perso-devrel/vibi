package com.example.dubcast.domain.model

data class ImageClip(
    val id: String,
    val projectId: String,
    val imageUri: String,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    val widthPct: Float = 30f,
    val heightPct: Float = 30f
)
