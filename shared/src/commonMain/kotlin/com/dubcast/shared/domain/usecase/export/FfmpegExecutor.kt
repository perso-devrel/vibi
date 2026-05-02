package com.dubcast.shared.domain.usecase.export

import com.dubcast.shared.domain.model.SegmentType

data class DubClipMixInput(
    val audioFilePath: String,
    val startMs: Long,
    val volume: Float = 1.0f
)

data class ImageClipMixInput(
    val imageFilePath: String,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float,
    val yPct: Float,
    val widthPct: Float,
    val heightPct: Float
)

data class SegmentInput(
    val sourceFilePath: String,
    val type: SegmentType,
    val order: Int,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val width: Int,
    val height: Int,
    val imageXPct: Float = 50f,
    val imageYPct: Float = 50f,
    val imageWidthPct: Float = 50f,
    val imageHeightPct: Float = 50f,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f
) {
    val effectiveTrimEndMs: Long
        get() = if (type == SegmentType.VIDEO && trimEndMs <= 0L) durationMs else trimEndMs

    val effectiveDurationMs: Long
        get() = when (type) {
            SegmentType.VIDEO -> effectiveTrimEndMs - trimStartMs
            SegmentType.IMAGE -> durationMs
        }
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
    val selections: List<SeparationStemInput>
)

data class SeparationStemInput(
    val stemId: String,
    val audioUrl: String,
    val volume: Float = 1.0f
)

interface FfmpegExecutor {
    /**
     * @param preUploadedInputId non-null 이면 BFF 가 캐시된 video/audios 를 재사용 — segment 의
     *   video bytes 와 dub audio bytes 를 multipart 로 다시 보내지 않는다. multi-variant export 에서
     *   ExportViewModel 이 1회 [BffApi.uploadRenderInputs] 호출 후 받은 inputId 를 모든 variant 로
     *   주입하는 fan-out 최적화. variant 단일이면 null (선업로드 round-trip 비용 회피).
     */
    suspend fun renderProject(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput> = emptyList(),
        outputPath: String,
        assFilePath: String? = null,
        fontDir: String? = null,
        frame: FrameInput? = null,
        bgmClips: List<BgmClipMixInput> = emptyList(),
        audioOverridePath: String? = null,
        separationDirectives: List<SeparationDirectiveInput> = emptyList(),
        preUploadedInputId: String? = null,
        onProgress: (percent: Int) -> Unit
    ): Result<String>
}
