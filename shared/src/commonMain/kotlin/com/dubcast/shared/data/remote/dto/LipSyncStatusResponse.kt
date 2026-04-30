package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LipSyncStatusResponse(
    val jobId: String,
    val status: String,
    val progress: Int,
    val resultVideoUrl: String? = null
)
