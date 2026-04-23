package com.example.dubcast.domain.model

enum class SegmentType { VIDEO, IMAGE }

data class Segment(
    val id: String,
    val projectId: String,
    val type: SegmentType,
    val order: Int,
    val sourceUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val imageXPct: Float = 50f,
    val imageYPct: Float = 50f,
    val imageWidthPct: Float = 50f,
    val imageHeightPct: Float = 50f,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
    val duplicatedFromId: String? = null
) {
    val effectiveTrimEndMs: Long
        get() = if (type == SegmentType.VIDEO && trimEndMs <= 0L) durationMs else trimEndMs

    val effectiveDurationMs: Long
        get() = when (type) {
            SegmentType.VIDEO -> effectiveTrimEndMs - trimStartMs
            SegmentType.IMAGE -> durationMs
        }
}
