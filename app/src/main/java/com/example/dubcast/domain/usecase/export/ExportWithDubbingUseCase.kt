package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.ImageClip
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
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        imageClips: List<ImageClip> = emptyList(),
        resolveImagePath: suspend (imageUri: String) -> String? = { null },
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

        val imageMixInputs = imageClips.mapNotNull { clip ->
            val localPath = resolveImagePath(clip.imageUri) ?: return@mapNotNull null
            ImageClipMixInput(
                imageFilePath = localPath,
                startMs = clip.startMs,
                endMs = clip.endMs,
                xPct = clip.xPct,
                yPct = clip.yPct,
                widthPct = clip.widthPct,
                heightPct = clip.heightPct
            )
        }

        return ffmpegExecutor.mixAudioWithVideo(
            inputVideoPath = inputVideoPath,
            dubClips = mixInputs,
            outputPath = outputPath,
            videoDurationMs = videoDurationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            assFilePath = assPath,
            fontDir = fontDir,
            imageClips = imageMixInputs,
            onProgress = onProgress
        )
    }
}
