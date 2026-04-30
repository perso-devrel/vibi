package com.dubcast.shared.domain.model

import com.dubcast.shared.domain.repository.StemSelection

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
    val createdAt: Long
) {
    val durationMs: Long get() = (rangeEndMs - rangeStartMs).coerceAtLeast(0L)
}
