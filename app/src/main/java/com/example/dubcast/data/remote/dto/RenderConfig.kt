package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RenderConfig(
    val dubClips: List<RenderDubClip>,
    val segments: List<RenderSegment>,
    val imageClips: List<RenderImageClip> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RenderDubClip(
    val audioFileKey: String,
    val startMs: Long,
    val durationMs: Long,
    val volume: Float
)

@JsonClass(generateAdapter = true)
data class RenderImageClip(
    val imageFileKey: String,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float,
    val yPct: Float,
    val widthPct: Float,
    val heightPct: Float
)

@JsonClass(generateAdapter = true)
data class RenderSegment(
    val sourceFileKey: String,
    val type: String,
    val order: Int,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val width: Int,
    val height: Int,
    val imageXPct: Float = 50f,
    val imageYPct: Float = 50f,
    val imageWidthPct: Float = 50f,
    val imageHeightPct: Float = 50f,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f
)
