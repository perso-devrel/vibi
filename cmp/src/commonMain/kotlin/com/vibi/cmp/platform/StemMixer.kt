package com.vibi.cmp.platform

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

data class StemMixerSource(
    val stemId: String,
    val audioUrl: String,
    /** multi-directive 흐름에서 group 단위로 active 전환. 같은 stemId 라도 directive 다르면 별도 player. */
    val groupId: String = "default",
)

interface StemMixerHandle {
    /** 모든 group 의 stems prepare. 진입 시 [setActiveGroup] 으로 active 전환. */
    fun load(sources: List<StemMixerSource>)

    /** active group 만 [play]/[pause]/[seekTo] 영향 받음. null 이면 전부 pause. */
    fun setActiveGroup(groupId: String?)

    /** stem 볼륨. 같은 stemId 가 여러 group 에 있으면 모두 해당.
     *  AVAudioPlayer / ExoPlayer 모두 1.0 하드 클램프 — 미리듣기에서 >1.0 부스트는 불가.
     *  render path (ffmpeg) 는 0..2 게인을 그대로 적용하므로 UI 슬라이더는 0..2f 유지. */
    fun setVolume(stemId: String, volume: Float)

    /** 재생 속도. video segment.speedScale 와 sync. 1.0 = 원본. AVAudioPlayer.enableRate /
     *  ExoPlayer.PlaybackParameters 로 pitch-corrected stretch. 같은 stemId 의 모든 group player
     *  에 적용 (group 별 다른 속도 시나리오 없음 — 현재 활성 directive 의 속도만 의미). */
    fun setRate(rate: Float)

    /** 현재 active group 의 stems 만 동시 재생. */
    fun play()

    /** active group 일시정지. */
    fun pause()

    /** active group 의 stems 재생 위치를 동일 timestamp 로 이동. directive range 내부 offset. */
    fun seekTo(positionMs: Long)

    /** 모든 player 해제. */
    fun release()
}
