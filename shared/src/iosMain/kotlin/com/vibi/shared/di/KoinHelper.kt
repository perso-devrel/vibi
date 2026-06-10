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
 * @param iapEnabled `RuntimeFlags.iapEnabled` (`:cmp` SSOT). false 면 `Transaction.updates` listener
 *   를 등록하지 않는다 — 샌드박스 미로그인 시 StoreKit 이 뱉는 "No active account" (ASDError 509) 로그도 사라짐.
 */
fun initKoinIos(
    bffBaseUrl: String,
    googleSignInBridge: GoogleSignInBridge,
    appleSignInBridge: AppleSignInBridge,
    iapBridge: IapBridge,
    onDeviceVideoExportBridge: OnDeviceVideoExportBridge,
    iapEnabled: Boolean,
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
    // iapEnabled=false (무료 선출시) 면 구매 진입점이 없어 마무리할 transaction 도 없으므로
    // listener 등록을 건너뛴다. start() 가 IapBridge.setTransactionListener → Transaction.updates
    // for-await 루프를 띄우는 유일한 경로라, 이걸 막으면 미로그인 시 ASDError 509 로그도 안 뜬다.
    if (iapEnabled) {
        KoinPlatform.getKoin().get<IapTransactionReconciler>().start()
    }
}
