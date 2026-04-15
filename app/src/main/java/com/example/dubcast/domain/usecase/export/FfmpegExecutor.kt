package com.example.dubcast.domain.usecase.export

data class DubClipMixInput(
    val audioFilePath: String,
    val startMs: Long,
    val volume: Float = 1.0f
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
        assFilePath: String? = null,
        fontDir: String? = null,
        onProgress: (percent: Int) -> Unit
    ): Result<String>
}
