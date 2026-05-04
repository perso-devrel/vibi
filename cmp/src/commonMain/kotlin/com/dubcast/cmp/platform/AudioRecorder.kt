package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable

/**
 * 즉시 녹음 — start/stop 으로 마이크 입력 받아 m4a (AAC) 로 저장.
 *
 * Android: MediaRecorder + RECORD_AUDIO permission + cacheDir m4a.
 * iOS: AVAudioRecorder + AVAudioSession (.record) + cacheDir m4a.
 *
 * @param onRecorded 녹음 종료 시 절대 경로(또는 file://) 콜백.
 * @param onError 권한 거부 / 디스크 I/O 등 실패 메시지.
 */
@Composable
expect fun rememberAudioRecorder(
    onRecorded: (uri: String, durationMs: Long) -> Unit,
    onError: (message: String) -> Unit = {},
): AudioRecorderController

interface AudioRecorderController {
    /** 녹음 시작 — 권한 prompt 가 떠 있으면 grant 후 자동 시작. */
    fun start()
    /** 녹음 종료 — onRecorded 콜백 트리거. */
    fun stop()
    /** 현재 녹음 중인지. UI 의 ▶/⏹ 토글 표시용. */
    val isRecording: Boolean
    /**
     * 현재 마이크 입력 레벨 — 0f (무음) ~ 1f (clipping). 호출 시점 peak power 를 dB → linear
     * 정규화. polling 으로 ~100ms 마다 읽어 시각화. 녹음 중이 아닐 땐 0f.
     */
    val currentLevel: Float
}
