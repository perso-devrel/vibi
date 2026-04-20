package com.example.dubcast.fake

import com.example.dubcast.domain.usecase.export.DubClipMixInput
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
import com.example.dubcast.domain.usecase.export.ImageClipMixInput
import com.example.dubcast.domain.usecase.export.SegmentInput

class FakeFfmpegExecutor : FfmpegExecutor {
    var mixResult: Result<String> = Result.success("/output/mixed.mp4")
    var progressSteps: List<Int> = listOf(25, 50, 75, 100)
    var lastMixInputs: List<DubClipMixInput>? = null
    var lastImageInputs: List<ImageClipMixInput>? = null
    var lastSegments: List<SegmentInput>? = null

    override suspend fun renderProject(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput>,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        lastSegments = segments
        lastMixInputs = dubClips
        lastImageInputs = imageClips
        progressSteps.forEach { onProgress(it) }
        return mixResult
    }
}
