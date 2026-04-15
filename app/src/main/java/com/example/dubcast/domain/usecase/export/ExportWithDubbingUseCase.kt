package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.SubtitleClip
import javax.inject.Inject

class ExportWithDubbingUseCase @Inject constructor(
    private val assGenerator: AssGenerator,
    private val ffmpegExecutor: FfmpegExecutor
) {

    suspend fun execute(
        inputVideoPath: String,
        dubClips: List<DubClip>,
        subtitleClips: List<SubtitleClip>,
        videoWidth: Int,
        videoHeight: Int,
        videoDurationMs: Long,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        var assPath: String? = null

        if (subtitleClips.isNotEmpty() && assFilePath != null) {
            val assContent = assGenerator.generateFromClips(subtitleClips, videoWidth, videoHeight)
            java.io.File(assFilePath).writeText(assContent)
            assPath = assFilePath
        }

        val mixInputs = dubClips.map { clip ->
            DubClipMixInput(
                audioFilePath = clip.audioFilePath,
                startMs = clip.startMs,
                volume = clip.volume
            )
        }

        return ffmpegExecutor.mixAudioWithVideo(
            inputVideoPath = inputVideoPath,
            dubClips = mixInputs,
            outputPath = outputPath,
            videoDurationMs = videoDurationMs,
            assFilePath = assPath,
            fontDir = fontDir,
            onProgress = onProgress
        )
    }
}
