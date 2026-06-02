package com.vibi.shared.di

import com.vibi.shared.platform.AppleSignInBridge
import com.vibi.shared.platform.GoogleSignInBridge
import com.vibi.shared.platform.IapBridge
import com.vibi.shared.platform.IapTransactionReconciler
import com.vibi.shared.platform.OnDeviceVideoExportBridge
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/**
 * Convenience entry point for Swift/iOS callers who cannot pass Kotlin default
 * arguments or construct Koin modules directly. Call once from `iOSApp` during
 * app launch.
 *
 * @param googleSignInBridge Swift `GoogleSignInBridgeImpl` 인스턴스.
 * @param appleSignInBridge Swift `AppleSignInBridgeImpl` 인스턴스.
 * @param iapBridge Swift `IapBridgeImpl` (StoreKit2) 인스턴스.
 * @param onDeviceVideoExportBridge Swift `OnDeviceVideoExportBridgeImpl` (AVFoundation 온디바이스 인코딩) 인스턴스.
 */
fun initKoinIos(
    bffBaseUrl: String,
    googleSignInBridge: GoogleSignInBridge,
    appleSignInBridge: AppleSignInBridge,
    iapBridge: IapBridge,
    onDeviceVideoExportBridge: OnDeviceVideoExportBridge,
) {
    initKoin(
        bffBaseUrl = bffBaseUrl,
        platformModules = listOf(
            iosPlatformModule,
            module {
                single<GoogleSignInBridge> { googleSignInBridge }
                single<AppleSignInBridge> { appleSignInBridge }
                single<IapBridge> { iapBridge }
                single<OnDeviceVideoExportBridge> { onDeviceVideoExportBridge }
            },
        ),
    )
    KoinPlatform.getKoin().get<IapTransactionReconciler>().start()
}
