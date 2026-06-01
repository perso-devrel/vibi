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
    /**
     * 클립이 처음 추가된 시각 (epoch ms). UI 가 BGM 색·녹음 번호를 부여할 때 이 값을 기준으로
     * 정렬한다 — startMs (timeline 위치) 가 아니라. 따라서 사용자가 클립을 drag 해 위치를
     * 바꿔도 색·번호가 흔들리지 않는다 (CapCut/Premiere 식 안정성). 기본값 0 은 legacy row
     * fallback (insertion 순서로 정렬됨).
     */
    val createdAt: Long = 0L,
    /**
     * 배경음 제거 (음원분리 → voice_all stem) 적용 후 원본 URI 보존용. null 이면 원본 상태
     * ([sourceUri] 자체가 원본). 사용자가 "원래대로" 누르면 [sourceUri] 를 이 값으로 되돌리되,
     * 본 필드와 [voiceOnlyUri] 는 그대로 둬서 다시 "배경음 제거" 시 캐시(voiceOnlyUri) 로 즉시 swap.
     */
    val originalSourceUri: String? = null,
    /**
     * 음원분리 결과 (voice_all stem) 의 BFF signed URL 캐시. null 이면 아직 한 번도 분리 안 함.
     * 한 번 분리한 뒤엔 보존돼 "배경음 제거 ↔ 원래대로" 토글이 즉시 (재분리 없이) 가능.
     * **caveat**: signed URL 만료 시 재생 실패 가능 — 현 BGM 흐름이 stem URL 을 그대로 sourceUri
     * 로 쓰는 옛 가정과 동일 한계.
     */
    val voiceOnlyUri: String? = null,
    /**
     * 사용자가 지정한 표시 이름. null/blank 이면 UI 가 [sourceUri] 파일명(또는 녹음 → "Recording N")
     * 으로 자동 라벨링한다 (SoundCard `bgmDisplayLabel`). 음원·녹음 카드의 "이름 옆 연필" 로 편집.
     */
    val customName: String? = null,
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

    /** "지금 voice-only(배경음 제거) 상태로 재생 중인가". UI 버튼 라벨이 이 값으로 분기. */
    val isBackgroundRemoved: Boolean
        get() = voiceOnlyUri != null && sourceUri == voiceOnlyUri

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}
