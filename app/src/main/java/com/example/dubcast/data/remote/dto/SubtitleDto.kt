package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubtitleSpec(
    val mediaType: String,
    val sourceLanguageCode: String,
    val targetLanguageCode: String? = null,
    val numberOfSpeakers: Int = 1
)

@JsonClass(generateAdapter = true)
data class SubtitleJobResponse(
    val jobId: String
)

@JsonClass(generateAdapter = true)
data class SubtitleStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val error: String? = null,
    val originalSrtUrl: String? = null,
    val translatedSrtUrl: String? = null
)
