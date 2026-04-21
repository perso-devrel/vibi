package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.TextOverlay
import javax.inject.Inject

class ExportWithDubbingUseCase @Inject constructor(
    private val assGenerator: AssGenerator,
    private val ffmpegExecutor: FfmpegExecutor
) {

    suspend fun execute(
        segments: List<SegmentInput>,
        dubClips: List<DubClip>,
        subtitleClips: List<SubtitleClip>,
        outputPath: String,
        assFilePath: String?,
        fontDir: String?,
        frame: FrameInput? = null,
        imageClips: List<ImageClip> = emptyList(),
        textOverlays: List<TextOverlay> = emptyList(),
        resolveImagePath: suspend (imageUri: String) -> String? = { null },
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        require(segments.isNotEmpty()) { "segments must not be empty" }

        val firstSegment = segments.minByOrNull { it.order } ?: segments.first()
        val outputWidth = frame?.width ?: firstSegment.width
        val outputHeight = frame?.height ?: firstSegment.height

        var assPath: String? = null
        val needsAss = subtitleClips.isNotEmpty() || textOverlays.isNotEmpty()
        if (needsAss && assFilePath != null) {
            val assContent = assGenerator.generateFromClips(
                clips = subtitleClips,
                videoWidth = outputWidth,
                videoHeight = outputHeight,
                textOverlays = textOverlays
            )
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

        return ffmpegExecutor.renderProject(
            segments = segments,
            dubClips = mixInputs,
            imageClips = imageMixInputs,
            outputPath = outputPath,
            assFilePath = assPath,
            fontDir = fontDir,
            frame = frame,
            onProgress = onProgress
        )
    }
}
