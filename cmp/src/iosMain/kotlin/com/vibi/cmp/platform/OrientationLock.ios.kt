package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * AppDelegate.application(_:supportedInterfaceOrientationsFor:) 가 이 flag 를 읽어
 * 현재 허용 mask 를 결정한다. force-rotate 는 하지 않으므로 사용자가 디바이스를 직접 돌려야
 * landscape 가 적용됨 (수동 회전 정책).
 */
object IosOrientationState {
    var landscapeAllowed: Boolean = false
}

@Composable
actual fun LockLandscape(enabled: Boolean) {
    DisposableEffect(enabled) {
        IosOrientationState.landscapeAllowed = enabled
        onDispose {
            IosOrientationState.landscapeAllowed = false
        }
    }
}
