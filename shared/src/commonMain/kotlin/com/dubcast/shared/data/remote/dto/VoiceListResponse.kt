package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class VoiceListResponse(
    val voices: List<VoiceDto>
)

@Serializable
data class VoiceDto(
    val voiceId: String,
    val name: String,
    val previewUrl: String? = null,
    val language: String? = null
)
