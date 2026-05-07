package com.dubcast.shared.di

import com.dubcast.shared.data.repository.IosAudioMetadataExtractor
import com.dubcast.shared.data.repository.IosAutoDubRepositoryImpl
import com.dubcast.shared.data.repository.IosAutoSubtitleRepositoryImpl
import com.dubcast.shared.data.repository.IosGallerySaver
import com.dubcast.shared.data.repository.IosImageMetadataExtractor
import com.dubcast.shared.data.repository.IosMediaJobUploader
import com.dubcast.shared.data.repository.IosShareSheetLauncher
import com.dubcast.shared.data.repository.IosVideoMetadataExtractor
import com.dubcast.shared.platform.GoogleSignInClient
import com.dubcast.shared.platform.IosGoogleSignInClient
import com.dubcast.shared.platform.IosVideoThumbnailExtractor
import com.dubcast.shared.platform.VideoThumbnailExtractor
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults
import com.dubcast.shared.domain.repository.AutoDubRepository
import com.dubcast.shared.domain.repository.AutoSubtitleRepository
import com.dubcast.shared.domain.usecase.input.AudioMetadataExtractor
import com.dubcast.shared.domain.usecase.input.ImageMetadataExtractor
import com.dubcast.shared.domain.usecase.input.VideoMetadataExtractor
import com.dubcast.shared.domain.usecase.share.GallerySaver
import com.dubcast.shared.domain.usecase.share.ShareSheetLauncher
import com.dubcast.shared.ui.export.ExportPlatformAdapter
import com.dubcast.shared.ui.export.IosExportPlatformAdapter
import org.koin.dsl.module

/**
 * iOS 측 platform module.
 */
val iosPlatformModule = module {
    single<ExportPlatformAdapter> { IosExportPlatformAdapter(exportWithDubbing = get()) }
    single<GallerySaver> { IosGallerySaver() }
    single<ShareSheetLauncher> { IosShareSheetLauncher() }
    single<VideoMetadataExtractor> { IosVideoMetadataExtractor() }
    single<VideoThumbnailExtractor> { IosVideoThumbnailExtractor() }
    single<AudioMetadataExtractor> { IosAudioMetadataExtractor() }
    single<ImageMetadataExtractor> { IosImageMetadataExtractor() }
    single { IosMediaJobUploader() }
    single<AutoDubRepository> { IosAutoDubRepositoryImpl(api = get(), uploader = get()) }
    single<AutoSubtitleRepository> { IosAutoSubtitleRepositoryImpl(api = get(), uploader = get()) }

    // 인증 — GoogleSignInBridge 는 Swift 가 KoinHelper.initKoinIos 호출 시 별도 module 로 주입.
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<GoogleSignInClient> { IosGoogleSignInClient(bridge = get()) }
}
