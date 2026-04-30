package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RenderConfig(
    val dubClips: List<RenderDubClip>,
    val segments: List<RenderSegment>,
    val imageClips: List<RenderImageClip> = emptyList(),
    val frame: RenderFrame? = null,
    val bgmClips: List<RenderBgmClip> = emptyList(),
    /** my_plan: 음성분리 명세 — 모든 결과 영상에 동일 적용. */
    val separationDirectives: List<RenderSeparationDirective> = emptyList(),
    /** 결과 영상 언어 (출력 메타). */
    val outputLanguageCode: String = "original"
)

@Serializable
data class RenderSeparationDirective(
    val id: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    /** stem 별 (URL + 볼륨) 들 — BFF render 가 다운로드 후 amix 로 합성. */
    val selections: List<RenderSeparationStem> = emptyList()
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
    val volume: Float = 1.0f
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
