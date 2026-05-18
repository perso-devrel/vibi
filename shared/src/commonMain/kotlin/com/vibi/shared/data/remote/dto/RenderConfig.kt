package com.vibi.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RenderConfig(
    val dubClips: List<RenderDubClip>,
    val segments: List<RenderSegment>,
    val imageClips: List<RenderImageClip> = emptyList(),
    val frame: RenderFrame? = null,
    val bgmClips: List<RenderBgmClip> = emptyList(),
    /**
     * 자동더빙 결과를 원본 영상의 audio 트랙에 그대로 덮어쓸 때 사용. 멀티파트의
     * `audio_override` 파일 키. dubClips/bgmClips 는 그 위에 mix 됨.
     */
    val audioOverrideKey: String? = null,
    /** my_plan: 음성분리 명세 — 모든 결과 영상에 동일 적용. */
    val separationDirectives: List<RenderSeparationDirective> = emptyList(),
    /** 결과 영상 언어 (출력 메타). */
    val outputLanguageCode: String = "original",
    /**
     * BFF render 출력 모드. "video" (기본) = 풀 mp4 mux. "audio" = audio-only m4a (AAC 192k) —
     * 5–10x 빠름. 자막/음성분리/STT 단계가 audio 만 필요할 때 사용. 본 jobId 가 자막/분리 스펙의
     * `editedRenderJobId` 로 송신되면 BFF 의 `-vn` 추출 분기가 mediaType=AUDIO 를 기준으로 동작하므로
     * 호출자는 spec.mediaType 도 "AUDIO" 로 강제해야 한다.
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
data class RenderDubClip(
    val audioFileKey: String,
    val startMs: Long,
    val durationMs: Long,
    val volume: Float
)

@Serializable
data class RenderImageClip(
    val imageFileKey: String,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float,
    val yPct: Float,
    val widthPct: Float,
    val heightPct: Float
)

@Serializable
data class RenderSegment(
    val sourceFileKey: String,
    val type: String,
    val order: Int,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val width: Int,
    val height: Int,
    val imageXPct: Float = 50f,
    val imageYPct: Float = 50f,
    val imageWidthPct: Float = 50f,
    val imageHeightPct: Float = 50f,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f
)
