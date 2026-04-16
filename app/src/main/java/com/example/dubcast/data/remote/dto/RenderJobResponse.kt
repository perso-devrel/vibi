package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RenderJobResponse(
    val jobId: String
)

@JsonClass(generateAdapter = true)
data class RenderStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int,
    val error: String? = null
)
