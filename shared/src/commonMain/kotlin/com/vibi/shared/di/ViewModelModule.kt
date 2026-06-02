package com.vibi.shared.di

import com.vibi.shared.ui.account.UserMenuViewModel
import com.vibi.shared.ui.auth.LoginViewModel
import com.vibi.shared.ui.input.InputViewModel
import com.vibi.shared.ui.timeline.TimelineViewModel
import org.koin.dsl.module

val viewModelModule = module {
    factory {
        InputViewModel(
            extractor = get(),
            validateVideo = get(),
            createProjectWithInitialVideoSegment = get(),
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
            editProjectRepository = get(),
            bgmClipRepository = get(),
            updateSegmentTrim = get(),
            addVideoSegment = get(),
            removeSegment = get(),
            splitSegment = get(),
            duplicateSegmentRange = get(),
            moveSegment = get(),
            mergeSegments = get(),
            removeSegmentRange = get(),
            updateSegmentVolume = get(),
            updateSegmentSpeed = get(),
            addBgmClip = get(),
            updateBgmClip = get(),
            videoMetadataExtractor = get(),
            audioMetadataExtractor = get(),
            startAudioSeparation = get(),
            pollSeparation = get(),
            audioExtractor = get(),
            audioSeparationRepository = get(),
            separationDirectiveRepository = get(),
            bffBaseUrl = getProperty<String>("bffBaseUrl"),
            saveAllVariants = get(),
            shareSheetLauncher = get(),
            prewarmAssetUpload = get(),
            settings = get(),
        )
    }
    factory { LoginViewModel(authRepository = get()) }
    factory {
        UserMenuViewModel(
            authRepository = get(),
            tokenStore = get(),
            creditStore = get(),
            userSession = get(),
            bffApi = get(),
            creditPurchaseService = get(),
        )
    }
}
