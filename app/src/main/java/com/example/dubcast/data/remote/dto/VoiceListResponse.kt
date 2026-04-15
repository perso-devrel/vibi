package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VoiceListResponse(
    val voices: List<VoiceDto>
)

@JsonClass(generateAdapter = true)
data class VoiceDto(
    val voiceId: String,
    val name: String,
    val previewUrl: String? = null,
    val language: String? = null
)
