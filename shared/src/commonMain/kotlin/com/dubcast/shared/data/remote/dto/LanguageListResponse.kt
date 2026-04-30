package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LanguageOption(
    val code: String,
    val name: String,
    val nativeName: String? = null,
    val supportsDubbing: Boolean = true,
    val supportsSubtitles: Boolean = true,
)

@Serializable
data class LanguageListResponse(
    val languages: List<LanguageOption> = emptyList(),
)
