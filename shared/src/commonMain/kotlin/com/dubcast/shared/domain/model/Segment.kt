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

/**
 * 본 segment 에 무편집 default 가 아닌 변경이 적용됐는지.
 * default 기준: trimStartMs == 0, trimEndMs == 0 (미트림), volumeScale == 1.0, speedScale == 1.0.
 *
 * IMAGE segment 는 trim 개념이 없으므로 volume/speed 도 항상 default — 본 함수는 false 를 자주 반환.
 * 용도: 편집 영상이 필요한지(=새로 render 해야 하는지) 판단. 무편집이면 원본 sourceUri 직접 사용 가능.
 */
fun Segment.hasNonTrivialEdits(): Boolean {
    if (type == SegmentType.VIDEO) {
        if (trimStartMs != 0L) return true
        // trimEndMs == 0L 또는 == durationMs 둘 다 "미트림" 으로 본다 (effectiveTrimEndMs 의 fallback).
        if (trimEndMs != 0L && trimEndMs != durationMs) return true
    }
    if (volumeScale != 1.0f) return true
    if (speedScale != 1.0f) return true
    return false
}

/**
 * 프로젝트가 편집된 영상을 필요로 하는지 — 단일 segment + 모든 default + 추가 합성 항목 없음 → false
 * (원본 영상 그대로 사용 가능). 그 외엔 true.
 *
 * 검사 항목:
 *  - 다중 segment
 *  - segment 별 [hasNonTrivialEdits] (trim / volume / speed)
 *  - 사용자 추가 합성 항목: BGM, image overlay, text overlay, separation directive
 *  - project 수준 frame/canvas 설정: [EditProject.frameWidth]/[EditProject.frameHeight]/
 *    [EditProject.backgroundColorHex]/[EditProject.videoScale]/
 *    [EditProject.videoOffsetXPct]/[EditProject.videoOffsetYPct]
 *
 * default 기준값 (EditProject 기본 생성자):
 *  - frameWidth/frameHeight = 0 (미설정 — 첫 segment 의 native size 사용) **또는** 첫 segment 의
 *    native size 와 일치 (CreateProjectWithInitialVideoSegmentUseCase 가 즉시 영속화하는 값).
 *    두 경우 모두 "사용자가 명시적으로 frame 을 변경하지 않았음" 을 뜻하므로 default 로 인정.
 *  - backgroundColorHex = [EditProject.DEFAULT_BACKGROUND_COLOR_HEX] = "#000000"
 *  - videoScale = [EditProject.DEFAULT_VIDEO_SCALE] = 1f
 *  - videoOffsetXPct = videoOffsetYPct = 0f
 *
 * 호출자는 이 결과 false 일 때 새 render 호출을 skip 하고 segment[0].sourceUri 를 그대로 BFF 에 보낸다.
 */
fun isProjectEdited(
    project: EditProject,
    segments: List<Segment>,
    bgmClips: List<BgmClip> = emptyList(),
    imageClips: List<ImageClip> = emptyList(),
    textOverlays: List<TextOverlay> = emptyList(),
    separationDirectives: List<SeparationDirective> = emptyList(),
): Boolean {
    if (segments.size > 1) return true
    if (segments.any { it.hasNonTrivialEdits() }) return true
    if (bgmClips.isNotEmpty()) return true
    if (imageClips.isNotEmpty()) return true
    if (textOverlays.isNotEmpty()) return true
    if (separationDirectives.isNotEmpty()) return true
    // frame size: 첫 segment 의 native size 와 매칭되거나 미설정(0) 이면 default 로 간주.
    // CreateProjectWithInitialVideoSegmentUseCase 가 첫 segment 의 width/height 를 그대로
    // EditProject.frameWidth/Height 로 즉시 영속화하므로 native 매칭도 default 로 본다.
    val firstSegment = segments.firstOrNull()
    val frameMatchesNative = firstSegment != null &&
        project.frameWidth == firstSegment.width &&
        project.frameHeight == firstSegment.height
    val frameIsUnset = project.frameWidth == 0 && project.frameHeight == 0
    if (!frameMatchesNative && !frameIsUnset) return true
    if (project.backgroundColorHex != EditProject.DEFAULT_BACKGROUND_COLOR_HEX) return true
    if (project.videoScale != EditProject.DEFAULT_VIDEO_SCALE) return true
    if (project.videoOffsetXPct != 0f) return true
    if (project.videoOffsetYPct != 0f) return true
    return false
}
