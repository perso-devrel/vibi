package com.dubcast.cmp

import androidx.compose.runtime.Composable
import com.dubcast.cmp.theme.DubCastTheme
import com.dubcast.cmp.ui.navigation.DubCastNavHost

@Composable
fun App() {
    // 시스템 다크/라이트 모드 자동 감지.
    DubCastTheme {
        DubCastNavHost()
    }
}
