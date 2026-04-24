package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AutoDubSpec(
    val mediaType: String,
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val numberOfSpeakers: Int = 1,
    val ttsModel: String? = null
)

@JsonClass(generateAdapter = true)
data class AutoDubJobResponse(
    val jobId: String
)

@JsonClass(generateAdapter = true)
data class AutoDubStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val error: String? = null,
    val dubbedAudioUrl: String? = null
)
