package com.dubcast.shared.domain.usecase.export

import com.dubcast.shared.platform.writeTextToFile

import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.model.SeparationDirective
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.TextOverlay

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
                volume = clip.volumeScale
            )
        }

        // audioUrl 이 비어 있거나 사용자가 선택 해제(selected=false)한 stem 은 mix 제외. 모든
        // stem 이 빠진 directive 자체는 skip.
        val separationInputs = separationDirectives.mapNotNull { d ->
            val stems = d.selections.mapNotNull { sel ->
                if (!sel.selected) return@mapNotNull null
                val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SeparationStemInput(
                    stemId = sel.stemId,
                    audioUrl = url,
                    volume = sel.volume
                )
            }
            if (stems.isEmpty()) null else SeparationDirectiveInput(
                id = d.id,
                rangeStartMs = d.rangeStartMs,
                rangeEndMs = d.rangeEndMs,
                numberOfSpeakers = d.numberOfSpeakers,
                muteOriginalSegmentAudio = d.muteOriginalSegmentAudio,
                selections = stems
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
            bgmClips = bgmMixInputs,
            audioOverridePath = audioOverridePath,
            separationDirectives = separationInputs,
            preUploadedInputId = preUploadedInputId,
            onProgress = onProgress
        )
    }
}
