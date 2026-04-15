package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TtsResponse(
    val audioUrl: String,
    val durationMs: Long
)
