package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

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

    /**
     * 절대 위치(seconds * 1000)로 즉시 seek. 재생 중일 때만 의미 있음.
     * 범위 밖이면 clamp.
     */
    fun seekTo(ms: Long)

    /** 현재 재생 위치 (ms). stop / 미시작 시 0. polling 기반이라 ~50ms 해상도. */
    val progressMs: State<Long>

    /** 로드된 트랙의 전체 길이 (ms). 미로드/실패 시 0. */
    val durationMs: State<Long>
}
