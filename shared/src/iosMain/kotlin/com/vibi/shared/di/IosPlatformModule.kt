@file:OptIn(com.russhwolf.settings.ExperimentalSettingsImplementation::class)

package com.vibi.shared.di

import com.vibi.shared.data.repository.IosAudioMetadataExtractor
import com.vibi.shared.data.repository.IosGallerySaver
import com.vibi.shared.data.repository.IosShareSheetLauncher
import com.vibi.shared.data.repository.IosVideoMetadataExtractor
import com.vibi.shared.data.repository.V3RenderExecutor
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.domain.usecase.export.HybridRenderExecutor
import com.vibi.shared.domain.usecase.export.OnDeviceVideoEncoder
import com.vibi.shared.platform.IosOnDeviceVideoEncoder
import com.vibi.shared.platform.AppleSignInClient
import com.vibi.shared.platform.GoogleSignInClient
import com.vibi.shared.platform.IosAppleSignInClient
import com.vibi.shared.platform.IapTransactionReconciler
import com.vibi.shared.platform.IosGoogleSignInClient
import com.vibi.shared.platform.IosIapClient
import com.vibi.shared.platform.IosRewardedAdController
import com.vibi.shared.platform.RewardedAdController
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.IosAudioExtractor
import com.vibi.shared.platform.IosSeparationNotifier
import com.vibi.shared.platform.SeparationNotifier
import com.vibi.shared.platform.IosVideoThumbnailExtractor
import com.vibi.shared.platform.VideoThumbnailExtractor
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.qualifier.named
import platform.Foundation.NSUserDefaults
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.domain.usecase.share.GallerySaver
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import com.vibi.shared.ui.export.ExportPlatformAdapter
import com.vibi.shared.ui.export.IosExportPlatformAdapter
import org.koin.dsl.module

/**
 * 온디바이스 인코딩 기능 게이트. **기본 OFF** — 실기기에서 서버(BFF ffmpeg) 대비 출력 파리티
 * (코덱/회전/fps) 검증을 마치기 전까지 프로덕션은 검증된 서버 경로([V3RenderExecutor])를 쓴다.
 * 검증 후 true 로 바꾸면 영상전용 편집 저장이 [HybridRenderExecutor] 로 온디바이스 fast-path 를 탄다.
 */
private const val ON_DEVICE_EXPORT_ENABLED = true

/**
 * iOS 측 platform module.
 */
val iosPlatformModule = module {
    // 온디바이스 fast-path(영상전용 편집) + 서버 v3 fallback. OnDeviceVideoExportBridge 는 Swift 가
    // KoinHelper.initKoinIos 호출 시 별도 module 로 주입. 영상 외(BGM·분리·실제 reframe·다중소스)는 fallback.
    // 플래그 OFF 면 onDevice=null → Hybrid 가 항상 fallback (현재 프로덕션 상태).
    single<OnDeviceVideoEncoder> { IosOnDeviceVideoEncoder(bridge = get()) }
    single<FfmpegExecutor> {
        HybridRenderExecutor(
            onDevice = if (ON_DEVICE_EXPORT_ENABLED) get<OnDeviceVideoEncoder>() else null,
            fallback = get<V3RenderExecutor>(),
        )
    }
    single<ExportPlatformAdapter> { IosExportPlatformAdapter(executor = get()) }
    single<GallerySaver> { IosGallerySaver() }
    single<ShareSheetLauncher> { IosShareSheetLauncher() }
    single<VideoMetadataExtractor> { IosVideoMetadataExtractor() }
    single<VideoThumbnailExtractor> { IosVideoThumbnailExtractor() }
    single<AudioMetadataExtractor> { IosAudioMetadataExtractor() }
    single<AudioExtractor> { IosAudioExtractor() }
    single<SeparationNotifier> { IosSeparationNotifier() }

    // 인증 — GoogleSignInBridge / AppleSignInBridge 는 Swift 가 KoinHelper.initKoinIos
    // 호출 시 별도 module 로 주입.
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    // 토큰 전용 보안 저장소 — Keychain(암호화). accessibility 는 지정하지 않는다(기본 WhenUnlocked).
    // ⚠️ kSecAttrAccessible 을 vararg 로 넘기면 multiplatform-settings 가 그 값을 read/update/delete
    // 쿼리에까지 매칭 제약(pdmn)으로 섞어, 기존(다른 accessibility) 토큰의 판독·재기록이 깨진다 —
    // 로그인 직후 getValidToken()=null → Authorization 헤더 누락 → BFF 401 missing_token 회귀.
    // 잠금 중 백그라운드 폴링용 토큰 접근은 AuthTokenStore 의 인메모리 캐시가 담당한다(프로세스 생존 중).
    single<Settings>(named(SECURE_SETTINGS)) { KeychainSettings(service = "vibi.auth") }
    single<GoogleSignInClient> { IosGoogleSignInClient(bridge = get()) }
    single<AppleSignInClient> { IosAppleSignInClient(bridge = get()) }
    single { IosIapClient(bridge = get()) }
    single {
        IapTransactionReconciler(
            bridge = get(),
            creditPurchaseService = get(),
        )
    }
    // 보상형 광고 — RewardedAdBridge(Swift) 는 KoinHelper.initKoinIos 가 별도 module 로 주입.
    single<RewardedAdController> { IosRewardedAdController(bridge = get()) }
}
