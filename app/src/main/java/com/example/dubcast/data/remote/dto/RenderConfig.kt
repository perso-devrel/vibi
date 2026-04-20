package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RenderConfig(
    val dubClips: List<RenderDubClip>,
    val videoDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
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
