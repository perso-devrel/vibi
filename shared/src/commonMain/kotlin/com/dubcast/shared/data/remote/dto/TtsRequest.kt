package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TtsRequest(
    val text: String,
    val voiceId: String
)
