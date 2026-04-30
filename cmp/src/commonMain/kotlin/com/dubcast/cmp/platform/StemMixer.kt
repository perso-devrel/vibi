package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable

/**
 * 분리된 stem 들을 동시에 재생하면서 stem 별 볼륨을 실시간 조절하는 mixer.
 *
 * Timeline 의 video preview 옆에서 stem 들이 같이 흘러가야 함 — video 재생/일시정지/seek 에
 * 맞춰 [play] / [pause] / [seekTo] 가 호출되고, 사용자가 stem 별 볼륨 슬라이더를 움직이면
 * [setVolume] 으로 즉시 반영.
 *
 * iOS 의 AVPlayer 볼륨/audioMix 는 K/N cinterop 에 setter 누락 — 단순 다중 player 는 막힘.
 * 우회는 (a) Swift bridge, (b) AVAudioPlayer 로 로컬 파일 재생. iOS 구현은 Phase 2 후속에서
 * Swift bridge 도입 시 활성화. 그 전까지 iOS impl 은 no-op 으로 빌드만 통과.
 */
@Composable
expect fun rememberStemMixer(): StemMixerHandle

data class StemMixerSource(val stemId: String, val audioUrl: String)

interface StemMixerHandle {
    /** 현재 player 다 정리하고 새 sources 로 prepare. */
    fun load(sources: List<StemMixerSource>)

    /** stem 별 볼륨을 0f~2f 로 설정. 불가 stemId 는 무시. */
    fun setVolume(stemId: String, volume: Float)

    /** 모든 stem 동시 재생 시작. 이미 재생 중이면 no-op. */
    fun play()

    /** 모든 stem 일시정지. */
    fun pause()

    /** 모든 stem 의 재생 위치를 동일 timestamp 로 이동. directive range 내부 offset 기준. */
    fun seekTo(positionMs: Long)

    /** 모든 player 해제. */
    fun release()
}
