package com.vibi.cmp.platform

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun LockLandscape(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(enabled) {
        val previous = activity.requestedOrientation
        if (enabled) {
            // USER — 강제 회전 안 함. 기본 세로 잠금만 풀어 "사용자가 기기를 직접 돌리면" 가로가
            // 되도록 허용하고, 전체화면 진입 즉시 landscape 로 auto-rotate 하지는 않는다. 기기의
            // auto-rotate(회전 잠금) 설정도 존중 — 잠겨 있으면 세로 유지. iOS(supportedInterface
            // Orientations 플래그만 flip, force-rotate 없음)와 동등한 체감.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
        onDispose {
            activity.requestedOrientation = previous
        }
    }
}
