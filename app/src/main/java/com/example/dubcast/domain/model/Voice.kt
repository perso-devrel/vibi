package com.example.dubcast.domain.model

data class Voice(
    val voiceId: String,
    val name: String,
    val previewUrl: String?,
    val language: String?
)
