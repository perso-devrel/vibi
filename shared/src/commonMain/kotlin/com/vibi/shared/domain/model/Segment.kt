package com.vibi.shared.domain.model

enum class SegmentType { VIDEO }

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
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
    val duplicatedFromId: String? = null
) {
    val effectiveTrimEndMs: Long
        get() = if (trimEndMs <= 0L) durationMs else trimEndMs

    /**
     * Source-media 의 trim 적용 길이 (ms). 속도 변경 전 — split / removeRange 등 source 좌표계 사용처.
     */
    val sourceTrimmedDurationMs: Long
        get() = effectiveTrimEndMs - trimStartMs

    /**
     * 타임라인 위에서 실제로 차지하는 길이 (ms). speedScale 반영 — speedScale=2 면 절반 길이로 보임.
     * UI 비율 계산 / 전체 timeline 합 / global ms 매핑 모두 본 값 사용.
     */
    val effectiveDurationMs: Long
        get() {
            val trimmed = effectiveTrimEndMs - trimStartMs
            return if (speedScale > 0f) (trimmed / speedScale).toLong() else trimmed
        }
}

/**
 * Slider 가 1.0 위치에서 emit 하는 부동소수점 quantize 오차 (예: 0.9999999f) 를 흡수하기 위한
 * tolerance. 1e-4 = 0.01% — 사람 귀/눈에 무의미한 차이.
 */
private const val EDIT_TOLERANCE = 1e-4f

private fun Float.isApproxOne(): Boolean = kotlin.math.abs(this - 1f) < EDIT_TOLERANCE
private fun Float.isApproxZero(): Boolean = kotlin.math.abs(this) < EDIT_TOLERANCE

/**
 * 본 segment 에 무편집 default 가 아닌 변경이 적용됐는지.
 * default 기준: trimStartMs == 0, trimEndMs == 0 or durationMs (미트림 sentinel), volumeScale ≈ 1.0, speedScale ≈ 1.0.
 *
 * 용도: 편집 영상이 필요한지(=새로 render 해야 하는지) 판단. 무편집이면 원본 sourceUri 직접 사용 가능.
 */
/**
 * 두 인접 세그먼트가 하나로 이어붙일 수 있는지 — 동일 source · 연속 trim(앞 effectiveTrimEnd == 뒤 trimStart)
 * · 동일 speed/volume · 연속 order. (split 조각은 충족.) 병합/병합복제의 단일 판정 진실.
 */
fun Segment.isContiguousMergeableWith(next: Segment): Boolean =
    next.order == order + 1 &&
        sourceUri == next.sourceUri &&
        effectiveTrimEndMs == next.trimStartMs &&
        speedScale == next.speedScale &&
        volumeScale == next.volumeScale

/**
 * order 순으로 정렬된 세그먼트들이 하나로 병합 가능한 연속 run 인지. [allowDuplicates] true 면 복제본
 * (duplicatedFromId 있음)도 허용 — 병합복제 블록을 다시 복제할 때. 기본 false (음원분리 병합 등).
 */
fun List<Segment>.isContiguousMergeableRun(allowDuplicates: Boolean = false): Boolean {
    if (size < 2) return false
    return zipWithNext().all { (a, b) ->
        a.isContiguousMergeableWith(b) &&
            (allowDuplicates || (a.duplicatedFromId == null && b.duplicatedFromId == null))
    }
}

fun Segment.hasNonTrivialEdits(): Boolean {
    if (trimStartMs != 0L) return true
    // trimEndMs == 0L 또는 == durationMs 둘 다 "미트림" 으로 본다 (effectiveTrimEndMs 의 fallback).
    if (trimEndMs != 0L && trimEndMs != durationMs) return true
    if (!volumeScale.isApproxOne()) return true
    if (!speedScale.isApproxOne()) return true
    return false
}

/**
 * 프로젝트가 편집된 영상을 필요로 하는지 — 단일 segment + 모든 default + 추가 합성 항목 없음 → false
 * (원본 영상 그대로 사용 가능). 그 외엔 true.
 *
 * 검사 항목:
 *  - 다중 segment
 *  - segment 별 [hasNonTrivialEdits] (trim / volume / speed)
 *  - 사용자 추가 합성 항목: BGM, separation directive
 *  - project 수준 frame/canvas 설정: [EditProject.frameWidth]/[EditProject.frameHeight]/
 *    [EditProject.backgroundColorHex]/[EditProject.videoScale]/
 *    [EditProject.videoOffsetXPct]/[EditProject.videoOffsetYPct]
 *
 * text overlay 는 export 출력에 영향 없으므로 (preview 전용) 트리거에서 제외.
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
    separationDirectives: List<SeparationDirective> = emptyList(),
): Boolean {
    if (segments.size > 1) return true
    if (segments.any { it.hasNonTrivialEdits() }) return true
    if (bgmClips.isNotEmpty()) return true
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
    if (!project.videoScale.isApproxOne()) return true
    if (!project.videoOffsetXPct.isApproxZero()) return true
    if (!project.videoOffsetYPct.isApproxZero()) return true
    return false
}
