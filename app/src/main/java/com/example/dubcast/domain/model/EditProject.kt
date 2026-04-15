package com.example.dubcast.domain.model

data class EditProject(
    val projectId: String,
    val videoUri: String,
    val videoDurationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val createdAt: Long,
    val updatedAt: Long
)
