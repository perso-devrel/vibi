package com.dubcast.shared.di

/**
 * Convenience entry point for Swift/iOS callers who cannot pass Kotlin default
 * arguments or construct Koin modules directly. Call once from `iOSApp` during
 * app launch.
 */
fun initKoinIos(bffBaseUrl: String) {
    initKoin(bffBaseUrl = bffBaseUrl, platformModules = listOf(iosPlatformModule))
}
