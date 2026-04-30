package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS no-op fallback. K/N AVFoundation cinterop 의 audio setter (AVPlayer.volume,
 * AVPlayerItem.audioMix 등) 미노출 한계 — Swift bridge 도입 시까지 timeline 에 stem 동시
 * 재생 미적용. 빌드 통과만 보장.
 */
@Composable
actual fun rememberStemMixer(): StemMixerHandle = remember {
    object : StemMixerHandle {
        override fun load(sources: List<StemMixerSource>) {}
        override fun setVolume(stemId: String, volume: Float) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun release() {}
    }
}
