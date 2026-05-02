package com.dubcast.shared.domain.model

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

    /**
     * Source-media 의 trim 적용 길이 (ms). 속도 변경 전 — split / removeRange 등 source 좌표계 사용처.
     */
    val sourceTrimmedDurationMs: Long
        get() = when (type) {
            SegmentType.VIDEO -> effectiveTrimEndMs - trimStartMs
            SegmentType.IMAGE -> durationMs
        }

    /**
     * 타임라인 위에서 실제로 차지하는 길이 (ms). speedScale 반영 — speedScale=2 면 절반 길이로 보임.
     * UI 비율 계산 / 전체 timeline 합 / global ms 매핑 모두 본 값 사용.
     */
    val effectiveDurationMs: Long
        get() = when (type) {
            SegmentType.VIDEO -> {
                val trimmed = effectiveTrimEndMs - trimStartMs
                if (speedScale > 0f) (trimmed / speedScale).toLong() else trimmed
            }
            SegmentType.IMAGE -> durationMs
        }
}
