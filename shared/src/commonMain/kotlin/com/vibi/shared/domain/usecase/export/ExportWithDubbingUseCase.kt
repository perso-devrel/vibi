package com.vibi.shared.domain.usecase.export

import com.vibi.shared.platform.writeTextToFile

import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.domain.model.DubClip
import com.vibi.shared.domain.model.ImageClip
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.SubtitleClip
import com.vibi.shared.domain.model.TextOverlay

class ExportWithDubbingUseCase constructor(
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
        bgmClips: List<BgmClip> = emptyList(),
        audioOverridePath: String? = null,
        separationDirectives: List<SeparationDirective> = emptyList(),
        preUploadedInputId: String? = null,
        resolveImagePath: suspend (imageUri: String) -> String? = { null },
        resolveAudioPath: suspend (audioUri: String) -> String? = { null },
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
            writeTextToFile(assFilePath, assContent)
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

        val bgmMixInputs = bgmClips.mapNotNull { clip ->
            val localPath = resolveAudioPath(clip.sourceUri) ?: return@mapNotNull null
            BgmClipMixInput(
                audioFilePath = localPath,
                startMs = clip.startMs,
                volume = clip.volumeScale,
                speed = clip.speedScale,
                sourceTrimStartMs = clip.sourceTrimStartMs,
                sourceTrimEndMs = clip.sourceTrimEndMs,
            )
        }

        val separationInputs = separationDirectives.mapNotNull { it.toExportInput() }

        return ffmpegExecutor.renderProject(
            segments = segments,
            dubClips = mixInputs,
            imageClips = imageMixInputs,
            outputPath = outputPath,
            assFilePath = assPath,
            fontDir = fontDir,
            frame = frame,
            bgmClips = bgmMixInputs,
            audioOverridePath = audioOverridePath,
            separationDirectives = separationInputs,
            preUploadedInputId = preUploadedInputId,
            onProgress = onProgress
        )
    }
}
