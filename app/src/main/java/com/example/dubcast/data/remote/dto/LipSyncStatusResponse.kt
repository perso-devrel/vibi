package com.example.dubcast.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LipSyncStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int,
    val resultVideoUrl: String? = null
)
