package com.vibi.cmp.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun LockLandscape(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(enabled) {
        val previous = activity.requestedOrientation
        if (enabled) {
            // SENSOR_LANDSCAPE — 디바이스 가속도계 기준 좌/우 양방향 자동 선택. 단방향 LANDSCAPE 보다
            // 자연스럽다 (사용자가 어느 쪽으로 들어도 영상이 똑바로 보임).
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            activity.requestedOrientation = previous
        }
    }
}
