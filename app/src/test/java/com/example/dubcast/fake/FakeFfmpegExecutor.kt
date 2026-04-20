package com.example.dubcast.fake

import com.example.dubcast.domain.usecase.export.DubClipMixInput
import com.example.dubcast.domain.usecase.export.FfmpegExecutor

class FakeFfmpegExecutor : FfmpegExecutor {
    var result: Result<String> = Result.success("/output/video.mp4")
    var mixResult: Result<String> = Result.success("/output/mixed.mp4")
    var progressSteps: List<Int> = listOf(25, 50, 75, 100)
    var lastMixInputs: List<DubClipMixInput>? = null

    override suspend fun burnSubtitles(
        inputVideoPath: String,
        assFilePath: String,
        outputPath: String,
        fontDir: String,
        durationMs: Long,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        progressSteps.forEach { onProgress(it) }
        return result
    }

    override suspend fun mixAudioWithVideo(
        inputVideoPath: String,
        dubClips: List<DubClipMixInput>,
        outputPath: String,
        videoDurationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        assFilePath: String?,
        fontDir: String?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        lastMixInputs = dubClips
        progressSteps.forEach { onProgress(it) }
        return mixResult
    }
}
