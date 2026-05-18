package com.vibi.shared.domain.model

data class BgmClip(
    val id: String,
    val projectId: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val startMs: Long,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
    /**
     * 음원 내부 trim 시작 ms. 0 이면 음원 처음부터.
     * 영상보다 긴 음원 삽입 시 BgmTrimSheet 에서 사용자가 잘라낸 [start, end) 구간을 보존.
     */
    val sourceTrimStartMs: Long = 0L,
    /**
     * 음원 내부 trim 끝 ms. 0 이면 음원 끝까지 (backward-compat — 기존 BGM 의 의미).
     */
    val sourceTrimEndMs: Long = 0L,
    /**
     * Visual lane (row) index in the BGM timeline lane group. 0 = top lane.
     *
     * 시간상 겹치는 BGM 클립을 위·아래 별도 행으로 시각 분리하기 위함. 사용자가 클립을
     * vertical drag 하면 ViewModel 의 in-memory override map 으로 갱신되며, 이 값이
     * 그 결과를 반영한다. **현재는 DB 영속화 없음** — repository → entity 매핑은 lane 을
     * 무시하고, ViewModel 이 매번 observe 시 override 를 다시 적용한다. 후속 마이그레이션
     * 단계에서 `bgm_clips.lane` 컬럼 + Room migration v33 으로 영속화 예정.
     */
    val lane: Int = 0,
) {
    /** trim 적용된 source 구간 길이 (ms). speed 영향 미반영. */
    val effectiveSourceDurationMs: Long
        get() {
            val end = if (sourceTrimEndMs > 0L) sourceTrimEndMs else sourceDurationMs
            return (end - sourceTrimStartMs).coerceAtLeast(0L)
        }

    /** trim + 속도 적용된 timeline 상 길이. */
    val effectiveDurationMs: Long
        get() = if (speedScale > 0f) (effectiveSourceDurationMs / speedScale).toLong()
            else effectiveSourceDurationMs

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}
