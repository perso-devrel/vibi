package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TtsResponse(
    val audioUrl: String,
    val durationMs: Long
)
