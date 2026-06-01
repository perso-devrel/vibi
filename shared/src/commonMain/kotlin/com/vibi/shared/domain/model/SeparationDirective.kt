package com.vibi.shared.domain.model

import com.vibi.shared.domain.repository.StemSelection

/**
 * 음성분리 명세 — 언어 독립.
 *
 * 사용자가 어느 언어 더빙 위에서 음성분리 sheet 를 띄웠든, 명세는 같은 형태로 저장되며
 * Export 시점에 모든 결과 영상(원본 + N개 더빙)에 동일하게 적용된다.
 *
 * BFF `/api/v2/separate` 가 stem 을 만들면 사용자가 stem 별 볼륨을 결정 → 그 결과
 * (stemId + volume + audioUrl) 묶음을 selections 에 그대로 보존. Export 가 BFF render
 * 에 selections 를 넘기면 ffmpeg 가 stem 들을 다운로드 후 amix 로 합성한다. 별도 mix
 * mp3 산출 단계는 없음 — 명세만 보존하고 실제 합성은 export 시점에 일괄 처리.
 */
data class SeparationDirective(
    val id: String,
    val projectId: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    val selections: List<StemSelection>,
    val createdAt: Long,
    /**
     * 이 directive 를 만든 BFF 분리 잡 id (`sep-...`). 같은 잡이 두 번 commit 돼도 (동시 폴링 race,
     * actualDuration 스냅으로 range 가 흔들리는 경우 등) 같은 directive 로 upsert 되게 하는 **안정적
     * dedup 키** — `(rangeStartMs, rangeEndMs)` 정확 일치 기반 dedup 은 스냅 경계에서 미스가 나
     * "내용이 같은 구간"이 중복 생성되던 회귀가 있었다. legacy row / 수동 split piece 는 null.
     */
    val jobId: String? = null,
    /**
     * Stem audio 파일 안에서 본 directive piece 가 시작하는 offset (ms).
     *
     * 기본 0 = 신규 분리 결과 (stem audio 전체가 이 directive 의 [rangeStartMs..rangeEndMs] 와 1:1 매핑).
     * 영상 range delete 로 directive 가 분할되면, 뒤쪽 piece 는 stem audio 의 중간부터 재생해야 하므로
     * `sourceOffsetMs = (deletedRangeEnd - originalRangeStart) + parent.sourceOffsetMs` 로 누적.
     *
     * Stem mixer 의 seek offset: `(playback - rangeStartMs) + sourceOffsetMs`.
     */
    val sourceOffsetMs: Long = 0L,
) {
    val durationMs: Long get() = (rangeEndMs - rangeStartMs).coerceAtLeast(0L)
}
