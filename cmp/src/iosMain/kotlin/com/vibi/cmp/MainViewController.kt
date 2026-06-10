package com.vibi.cmp

import androidx.compose.ui.window.ComposeUIViewController
import com.vibi.cmp.platform.RuntimeFlags
import com.vibi.shared.di.initKoinIos
import com.vibi.shared.platform.AppleSignInBridge
import com.vibi.shared.platform.GoogleSignInBridge
import com.vibi.shared.platform.IapBridge
import com.vibi.shared.platform.OnDeviceVideoExportBridge
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

private var koinStarted = false

@OptIn(ExperimentalForeignApi::class)
fun MainViewController(
    bffBaseUrl: String,
    googleSignInBridge: GoogleSignInBridge,
    appleSignInBridge: AppleSignInBridge,
    iapBridge: IapBridge,
    onDeviceVideoExportBridge: OnDeviceVideoExportBridge,
): UIViewController {
    if (!koinStarted) {
        initKoinIos(
            bffBaseUrl,
            googleSignInBridge,
            appleSignInBridge,
            iapBridge,
            onDeviceVideoExportBridge,
            RuntimeFlags.iapEnabled,
        )
        koinStarted = true
    }
    // KoinContext composable 은 Koin 4.x 에서 deprecated — startKoin {} 호출만으로
    // composable tree 의 DI 가 자동 활성화. wrapper 제거.
    val controller = ComposeUIViewController(configure = {
        enforceStrictPlistSanityCheck = false
    }) {
        App()
    }
    // ComposeUIViewController 의 root view 가 검정이라 SwiftUI ZStack 배경(그레이) 을 덮음.
    // systemGroupedBackground (#F2F2F7) 로 칠해서 status bar / home indicator 영역까지 자연스럽게 연결.
    controller.view.setBackgroundColor(
        UIColor.colorWithRed(red = 0.949, green = 0.949, blue = 0.969, alpha = 1.0)
    )
    return controller
}
