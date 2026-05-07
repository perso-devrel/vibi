package com.dubcast.shared.di

import com.dubcast.shared.platform.GoogleSignInBridge
import org.koin.dsl.module

/**
 * Convenience entry point for Swift/iOS callers who cannot pass Kotlin default
 * arguments or construct Koin modules directly. Call once from `iOSApp` during
 * app launch.
 *
 * @param googleSignInBridge Swift `GoogleSignInBridgeImpl` 인스턴스. Kotlin DI 에 등록되어
 *   `IosGoogleSignInClient` 가 주입받는다.
 */
fun initKoinIos(bffBaseUrl: String, googleSignInBridge: GoogleSignInBridge) {
    initKoin(
        bffBaseUrl = bffBaseUrl,
        platformModules = listOf(
            iosPlatformModule,
            module { single<GoogleSignInBridge> { googleSignInBridge } },
        ),
    )
}
