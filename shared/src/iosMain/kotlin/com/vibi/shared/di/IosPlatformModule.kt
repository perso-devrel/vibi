package com.vibi.shared.di

import com.vibi.shared.data.repository.IosAudioMetadataExtractor
import com.vibi.shared.data.repository.IosGallerySaver
import com.vibi.shared.data.repository.IosShareSheetLauncher
import com.vibi.shared.data.repository.IosVideoMetadataExtractor
import com.vibi.shared.data.repository.V3RenderExecutor
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.platform.AppleSignInClient
import com.vibi.shared.platform.GoogleSignInClient
import com.vibi.shared.platform.IosAppleSignInClient
import com.vibi.shared.platform.IapTransactionReconciler
import com.vibi.shared.platform.IosGoogleSignInClient
import com.vibi.shared.platform.IosIapClient
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.IosAudioExtractor
import com.vibi.shared.platform.IosVideoThumbnailExtractor
import com.vibi.shared.platform.VideoThumbnailExtractor
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.domain.usecase.share.GallerySaver
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import com.vibi.shared.ui.export.ExportPlatformAdapter
import com.vibi.shared.ui.export.IosExportPlatformAdapter
import org.koin.dsl.module

/**
 * iOS 측 platform module.
 */
val iosPlatformModule = module {
    // v3 asset-by-reference render path. Android 는 v2 multipart 유지 ([RemoteRenderExecutor]).
    single<FfmpegExecutor> { get<V3RenderExecutor>() }
    single<ExportPlatformAdapter> { IosExportPlatformAdapter(executor = get()) }
    single<GallerySaver> { IosGallerySaver() }
    single<ShareSheetLauncher> { IosShareSheetLauncher() }
    single<VideoMetadataExtractor> { IosVideoMetadataExtractor() }
    single<VideoThumbnailExtractor> { IosVideoThumbnailExtractor() }
    single<AudioMetadataExtractor> { IosAudioMetadataExtractor() }
    single<AudioExtractor> { IosAudioExtractor() }

    // 인증 — GoogleSignInBridge / AppleSignInBridge 는 Swift 가 KoinHelper.initKoinIos
    // 호출 시 별도 module 로 주입.
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<GoogleSignInClient> { IosGoogleSignInClient(bridge = get()) }
    single<AppleSignInClient> { IosAppleSignInClient(bridge = get()) }
    single { IosIapClient(bridge = get()) }
    single {
        IapTransactionReconciler(
            bridge = get(),
            creditPurchaseService = get(),
        )
    }
}
