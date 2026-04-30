package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LipSyncResponse(
    val jobId: String
)
