package com.example.dubcast.domain.model

data class EditProject(
    val projectId: String,
    val videoUri: String,
    val videoDurationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** Effective trim end — 0 means "use full duration" */
    val effectiveTrimEndMs: Long get() = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
    val trimmedDurationMs: Long get() = effectiveTrimEndMs - trimStartMs
}

