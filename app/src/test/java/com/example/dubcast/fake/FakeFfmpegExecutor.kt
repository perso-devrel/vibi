package com.example.dubcast.fake

import com.example.dubcast.domain.usecase.export.BgmClipMixInput
import com.example.dubcast.domain.usecase.export.DubClipMixInput
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
import com.example.dubcast.domain.usecase.export.FrameInput
import com.example.dubcast.domain.usecase.export.ImageClipMixInput
import com.example.dubcast.domain.usecase.export.SegmentInput

class FakeFfmpegExecutor : FfmpegExecutor {
    var mixResult: Result<String> = Result.success("/output/mixed.mp4")
    var progressSteps: List<Int> = listOf(25, 50, 75, 100)
    var lastMixInputs: List<DubClipMixInput>? = null
    var lastImageInputs: List<ImageClipMixInput>? = null
    var lastSegments: List<SegmentInput>? = null
    var lastFrame: FrameInput? = null
    var lastBgmInputs: List<BgmClipMixInput>? = null

    var lastAudioOverridePath: String? = null

    override suspend fun renderProject(
        segments: List<SegmentInput>,
        dubClips: List<DubClipMixInput>,
        imageClips: List<ImageClipMixInput>,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        audioOverridePath: String?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        lastSegments = segments
        lastMixInputs = dubClips
        lastImageInputs = imageClips
        lastFrame = frame
        lastBgmInputs = bgmClips
        lastAudioOverridePath = audioOverridePath
        progressSteps.forEach { onProgress(it) }
        return mixResult
    }
}
