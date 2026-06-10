package com.vibi.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RenderConfig(
    val segments: List<RenderSegment>,
    val frame: RenderFrame? = null,
    val bgmClips: List<RenderBgmClip> = emptyList(),
    /** 음성분리 명세 — 모든 결과 영상에 동일 적용. */
    val separationDirectives: List<RenderSeparationDirective> = emptyList(),
    /**
     * BFF render 출력 모드. "video" (기본) = 풀 mp4 mux. "audio" = audio-only m4a (AAC 192k) —
     * 5–10x 빠름. 음성분리/STT 단계가 audio 만 필요할 때 사용.
     */
    val outputKind: String = "video"
)

@Serializable
data class RenderSeparationDirective(
    val id: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    /** stem 별 (URL + 볼륨) 들 — BFF render 가 다운로드 후 amix 로 합성. */
    val selections: List<RenderSeparationStem> = emptyList(),
    /**
     * Stem audio 파일 안의 시작 offset (ms). 영상 range delete 로 split 된 directive 의
     * 뒤쪽 piece 가 stem audio 의 중간부터 재생해야 할 때 사용. 기본 0 = 신규 분리 결과.
     *
     * **BFF 측 미구현 (TODO)**: 현재 BFF `/api/v2/render` 핸들러는 본 필드를 무시하고
     * 항상 stem audio 의 0 위치부터 재생 — split piece 의 audio 가 잘못 출력됨. BFF DTO/ffmpeg
     * 파이프라인을 본 필드 인지하도록 업데이트 필요 (mobile preview 는 이미 반영됨).
     */
    val sourceOffsetMs: Long = 0L,
    /**
     * directive 가 앵커된 영상 세그먼트의 speedScale. stem audio 에 atempo 로 적용해 속도 조절된
     * 영상과 tempo 를 맞춘다. 1.0 = 원본 속도. atempo 권장 0.5..2.0 → chain 으로 0.25..4 표현
     * ([RenderBgmClip.speed] 와 동일 정책).
     *
     * **BFF 측 미구현 (TODO)**: 현재 핸들러는 본 필드를 무시하고 stem 을 원본 tempo 로 mix —
     * 속도 조절된 구간에서 stem 이 영상과 어긋난다. BFF ffmpeg 파이프라인이 stem 입력마다
     * `atempo=appliedSpeedScale` 를 걸도록 업데이트 필요 (mobile preview 는 이미 setRate 로 반영됨).
     */
    val appliedSpeedScale: Float = 1.0f,
)

@Serializable
data class RenderSeparationStem(
    val stemId: String,
    val audioUrl: String,
    val volume: Float = 1.0f
)

@Serializable
data class RenderBgmClip(
    val audioFileKey: String,
    val startMs: Long,
    val volume: Float = 1.0f,
    /** 1.0 = 정상 속도. BFF 의 atempo 필터로 적용 (atempo 권장 범위 0.5..2.0 → chain 으로 0.25..4 표현). */
    val speed: Float = 1.0f,
    /** 음원 내부 trim 시작 ms. 0 이면 처음부터. */
    val sourceTrimStartMs: Long = 0L,
    /** 음원 내부 trim 끝 ms. 0 이면 끝까지. */
    val sourceTrimEndMs: Long = 0L,
)

@Serializable
data class RenderFrame(
    val width: Int,
    val height: Int,
    val backgroundColorHex: String = "#000000"
)

@Serializable
data class RenderSegment(
    val sourceFileKey: String,
    /** 항상 "VIDEO" — wire 호환을 위해 필드 유지 (BFF 가 무시해도 무방). */
    val type: String,
    val order: Int,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val width: Int,
    val height: Int,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f
)
