package com.dubcast.cmp

import androidx.compose.ui.window.ComposeUIViewController
import com.dubcast.shared.di.initKoinIos
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.compose.KoinContext
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

private var koinStarted = false

@OptIn(ExperimentalForeignApi::class)
fun MainViewController(bffBaseUrl: String): UIViewController {
    if (!koinStarted) {
        initKoinIos(bffBaseUrl)
        koinStarted = true
    }
    val controller = ComposeUIViewController(configure = {
        enforceStrictPlistSanityCheck = false
    }) {
        KoinContext { App() }
    }
    // ComposeUIViewController 의 root view 가 검정이라 SwiftUI ZStack 배경(그레이) 을 덮음.
    // systemGroupedBackground (#F2F2F7) 로 칠해서 status bar / home indicator 영역까지 자연스럽게 연결.
    controller.view.setBackgroundColor(
        UIColor.colorWithRed(red = 0.949, green = 0.949, blue = 0.969, alpha = 1.0)
    )
    return controller
}
