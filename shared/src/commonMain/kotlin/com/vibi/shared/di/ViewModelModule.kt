package com.vibi.shared.di

import com.vibi.shared.ui.auth.LoginViewModel
import com.vibi.shared.ui.chat.ChatViewModel
import com.vibi.shared.ui.input.InputViewModel
import com.vibi.shared.ui.timeline.TimelineViewModel
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
            authRepository = get(),
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
            moveDubClip = get(),
            deleteDubClip = get(),
            addSubtitleClip = get(),
            addImageClip = get(),
            updateImageClip = get(),
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
            duplicateTextOverlay = get(),
            addBgmClip = get(),
            updateBgmClip = get(),
            videoMetadataExtractor = get(),
            imageMetadataExtractor = get(),
            audioMetadataExtractor = get(),
            startAudioSeparation = get(),
            pollSeparation = get(),
            audioSeparationRepository = get(),
            generateAutoSubtitles = get(),
            regenerateSubtitles = get(),
            generateOriginalScript = get(),
            bffBaseUrl = getProperty<String>("bffBaseUrl"),
            bffApi = get(),
            generateAutoDub = get(),
            separationDirectiveRepository = get(),
            saveAllVariants = get(),
            listExportVariants = get(),
            shareSheetLauncher = get(),
            ensureLatestRender = get(),
            userPrefs = get(),
        )
    }
    factory { ChatViewModel(chatRepository = get()) }
    factory { LoginViewModel(authRepository = get()) }
}
