package com.vibi.shared.domain.usecase.export

import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.SeparationDirective

data class SegmentInput(
    val sourceFilePath: String,
    val type: SegmentType,
    val order: Int,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val width: Int,
    val height: Int,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f
) {
    val effectiveTrimEndMs: Long
        get() = if (trimEndMs <= 0L) durationMs else trimEndMs

    val effectiveDurationMs: Long
        get() = effectiveTrimEndMs - trimStartMs
}

data class FrameInput(
    val width: Int,
    val height: Int,
    val backgroundColorHex: String = "#000000"
)

data class BgmClipMixInput(
    val audioFilePath: String,
    val startMs: Long,
    val volume: Float = 1.0f,
    /** 재생 속도 — BFF 가 atempo 필터로 적용. 1.0 = 정상. */
    val speed: Float = 1.0f,
    /** 음원 내부 trim 시작 ms. 0 이면 처음부터. */
    val sourceTrimStartMs: Long = 0L,
    /** 음원 내부 trim 끝 ms. 0 이면 끝까지. */
    val sourceTrimEndMs: Long = 0L,
)

/**
 * 음성분리 명세를 export 단에서 그대로 전달하기 위한 input.
 * 합성은 BFF render 가 stem URL 들을 다운로드 후 amix 처리.
 */
data class SeparationDirectiveInput(
    val id: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    val selections: List<SeparationStemInput>,
    /** Stem audio 파일 안 시작 offset. split directive 의 뒤쪽 piece 가 stem 중간부터 재생. */
    val sourceOffsetMs: Long = 0L,
    /** 앵커 세그먼트의 speedScale — BFF 가 stem 에 atempo 로 걸어 영상 속도와 tempo 동기. 1.0 = 원본. */
    val appliedSpeedScale: Float = 1.0f,
)

data class SeparationStemInput(
    val stemId: String,
    val audioUrl: String,
    val volume: Float = 1.0f
)

/**
 * `selected=true` 이고 audioUrl 이 있는 stem 만 모아 export input 으로 변환.
 * 사용 가능한 stem 이 없으면 null — render 단계에서 directive 자체 skip.
 *
 * [appliedSpeedScale] 은 directive 가 앵커된 세그먼트의 speedScale — 호출자가 세그먼트에서 resolve 해
 * 주입한다(directive 는 speed 를 저장하지 않음, 세그먼트가 단일 진실원천). BFF 가 이 값으로 stem 에
 * atempo 를 걸어 속도 조절된 영상과 tempo 를 맞춘다.
 */
fun SeparationDirective.toExportInput(appliedSpeedScale: Float = 1.0f): SeparationDirectiveInput? {
    val stems = selections.mapNotNull { sel ->
        if (!sel.selected) return@mapNotNull null
        val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SeparationStemInput(stemId = sel.stemId, audioUrl = url, volume = sel.volume)
    }
    if (stems.isEmpty()) return null
    return SeparationDirectiveInput(
        id = id,
        rangeStartMs = rangeStartMs,
        rangeEndMs = rangeEndMs,
        numberOfSpeakers = numberOfSpeakers,
        muteOriginalSegmentAudio = muteOriginalSegmentAudio,
        selections = stems,
        sourceOffsetMs = sourceOffsetMs,
        appliedSpeedScale = if (appliedSpeedScale > 0f) appliedSpeedScale else 1.0f,
    )
}

interface FfmpegExecutor {
    suspend fun renderProject(
        segments: List<SegmentInput>,
        outputPath: String,
        frame: FrameInput? = null,
        bgmClips: List<BgmClipMixInput> = emptyList(),
        separationDirectives: List<SeparationDirectiveInput> = emptyList(),
        onProgress: (percent: Int) -> Unit
    ): Result<String>
}
