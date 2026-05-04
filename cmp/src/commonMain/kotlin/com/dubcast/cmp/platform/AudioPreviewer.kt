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
    /**
     * url 은 BFF 서명 토큰을 포함한 절대 URL 또는 file:// 경로.
     * @param volume 0..1 (clamp).
     * @param rate 0.5..2.0 (AVAudioPlayer 한계, clamp).
     * @param onComplete 자연 종료 시 한 번 호출. stop() 으로 끊으면 호출 안 됨.
     */
    fun play(url: String, volume: Float = 1f, rate: Float = 1f, onComplete: () -> Unit = {})
    fun stop()
}
