package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeparationSpec(
    val mediaType: String,
    val numberOfSpeakers: Int,
    val sourceLanguageCode: String,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null
)

@Serializable
data class SeparationJobResponse(
    val jobId: String
)

@Serializable
data class StemDto(
    val stemId: String,
    val label: String,
    val url: String
)

@Serializable
data class SeparationStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int = 0,
    val progressReason: String? = null,
    val stems: List<StemDto> = emptyList(),
    val mixJobId: String? = null
)

@Serializable
data class MixStemRequest(
    val stemId: String,
    val volume: Float
)

@Serializable
data class MixRequest(
    val stems: List<MixStemRequest>
)

@Serializable
data class MixJobResponse(
    val mixJobId: String
)

@Serializable
data class MixStatusResponse(
    val mixJobId: String,
    val status: String,
    val progress: Int = 0,
    val downloadUrl: String? = null
)
