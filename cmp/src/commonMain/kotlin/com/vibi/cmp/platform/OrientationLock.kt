package com.vibi.cmp.platform

import androidx.compose.runtime.Composable

/**
 * 화면을 가로(landscape)로 잠그는 composable lifecycle 헬퍼. [enabled]=true 일 때 적용,
 * composable 이 빠지거나 false 로 바뀌면 portrait 로 복귀.
 *
 * - Android: Activity.requestedOrientation 으로 force-rotate (sensor 기반 좌/우 자동).
 * - iOS: AppDelegate 의 supportedInterfaceOrientations 플래그만 flip — 사용자가 디바이스를
 *   직접 돌려야 회전됨 (force-rotate 없음). 의도적으로 수동 회전만 허용.
 */
@Composable
expect fun LockLandscape(enabled: Boolean)
