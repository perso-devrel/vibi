package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TtsRequest(
    val text: String,
    val voiceId: String
)
