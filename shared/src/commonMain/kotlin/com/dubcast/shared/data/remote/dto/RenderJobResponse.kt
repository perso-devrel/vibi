package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RenderJobResponse(
    val jobId: String
)

@Serializable
data class RenderStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int,
    val error: String? = null
)
