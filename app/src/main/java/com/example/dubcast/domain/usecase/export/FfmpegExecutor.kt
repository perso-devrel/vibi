package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.SegmentType

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

interface FfmpegExecutor {
    suspend fun renderProject(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput> = emptyList(),
        outputPath: String,
        assFilePath: String? = null,
        fontDir: String? = null,
        onProgress: (percent: Int) -> Unit
    ): Result<String>
}
