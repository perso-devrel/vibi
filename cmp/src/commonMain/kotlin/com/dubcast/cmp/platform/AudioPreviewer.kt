package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable

/**
 * stem 미리듣기용 가벼운 오디오 플레이어 — my_plan: "분리된 음원 미리듣기 가능".
 *
 * Composable 컨텍스트에서 호출. 반환된 [AudioPreviewerHandle] 의 [play] / [stop] 으로 제어.
 * 화면이 떠나면 자동으로 정리됨 (DisposableEffect).
 */
@Composable
expect fun rememberAudioPreviewer(): AudioPreviewerHandle

interface AudioPreviewerHandle {
    /** url 은 BFF 서명 토큰을 포함한 절대 URL 또는 file:// 경로. */
    fun play(url: String)
    fun stop()
}
