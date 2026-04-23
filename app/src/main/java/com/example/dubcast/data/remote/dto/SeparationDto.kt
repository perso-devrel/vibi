package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SeparationSpec(
    val mediaType: String,
    val numberOfSpeakers: Int,
    val sourceLanguageCode: String,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class SeparationJobResponse(
    val jobId: String
)

@JsonClass(generateAdapter = true)
data class StemDto(
    val stemId: String,
    val label: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class SeparationStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val stems: List<StemDto> = emptyList(),
    val mixJobId: String? = null
)

@JsonClass(generateAdapter = true)
data class MixStemRequest(
    val stemId: String,
    val volume: Float
)

@JsonClass(generateAdapter = true)
data class MixRequest(
    val stems: List<MixStemRequest>
)

@JsonClass(generateAdapter = true)
data class MixJobResponse(
    val mixJobId: String
)

@JsonClass(generateAdapter = true)
data class MixStatusResponse(
    val mixJobId: String,
    val status: String,
    val progress: Int = 0,
    val downloadUrl: String? = null
)
