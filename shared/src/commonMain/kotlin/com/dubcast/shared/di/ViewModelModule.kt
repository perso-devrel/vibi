package com.dubcast.shared.di

import com.dubcast.shared.ui.chat.ChatViewModel
import com.dubcast.shared.ui.input.InputViewModel
import com.dubcast.shared.ui.timeline.TimelineViewModel
import org.koin.dsl.module

val viewModelModule = module {
    factory {
        InputViewModel(
            extractor = get(),
            validateVideo = get(),
            createProjectWithInitialVideoSegment = get(),
            languageRepository = get(),
            editProjectRepository = get(),
            segmentRepository = get(),
            thumbnailExtractor = get(),
            expireOldDrafts = get(),
        )
    }
    factory { (projectId: String) ->
        TimelineViewModel(
            projectId = projectId,
            segmentRepository = get(),
            dubClipRepository = get(),
            subtitleClipRepository = get(),
            imageClipRepository = get(),
            editProjectRepository = get(),
            textOverlayRepository = get(),
            bgmClipRepository = get(),
            ttsRepository = get(),
            synthesizeDubClip = get(),
            getVoiceList = get(),
            moveDubClip = get(),
            deleteDubClip = get(),
            addSubtitleClip = get(),
            deleteSubtitleClip = get(),
            addImageClip = get(),
            updateImageClip = get(),
            deleteImageClip = get(),
            updateSegmentTrim = get(),
            addVideoSegment = get(),
            addImageSegment = get(),
            removeSegment = get(),
            updateImageSegmentDuration = get(),
            updateImageSegmentPosition = get(),
            splitSegment = get(),
            duplicateSegmentRange = get(),
            removeSegmentRange = get(),
            updateSegmentVolume = get(),
            updateSegmentSpeed = get(),
            setProjectFrame = get(),
            addTextOverlay = get(),
            updateTextOverlay = get(),
            deleteTextOverlay = get(),
            duplicateTextOverlay = get(),
            addBgmClip = get(),
            updateBgmClip = get(),
            deleteBgmClip = get(),
            videoMetadataExtractor = get(),
            imageMetadataExtractor = get(),
            audioMetadataExtractor = get(),
            startAudioSeparation = get(),
            pollSeparation = get(),
            generateAutoSubtitles = get(),
            regenerateSubtitles = get(),
            generateOriginalScript = get(),
            bffBaseUrl = getProperty<String>("bffBaseUrl"),
            bffApi = get(),
            generateAutoDub = get(),
            separationDirectiveRepository = get(),
            saveAllVariants = get(),
            shareSheetLauncher = get(),
        )
    }
    factory { ChatViewModel(chatRepository = get()) }
}
