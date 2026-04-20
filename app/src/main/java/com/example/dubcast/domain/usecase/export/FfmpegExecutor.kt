package com.example.dubcast.domain.usecase.export

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

interface FfmpegExecutor {
    suspend fun burnSubtitles(
        inputVideoPath: String,
        assFilePath: String,
        outputPath: String,
        fontDir: String,
        durationMs: Long = 0L,
        onProgress: (percent: Int) -> Unit
    ): Result<String>

    suspend fun mixAudioWithVideo(
        inputVideoPath: String,
        dubClips: List<DubClipMixInput>,
        outputPath: String,
        videoDurationMs: Long,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        assFilePath: String? = null,
        fontDir: String? = null,
        imageClips: List<ImageClipMixInput> = emptyList(),
        onProgress: (percent: Int) -> Unit
    ): Result<String>
}
