package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Android: stem 별 ExoPlayer 인스턴스. 같은 시점에 동시 재생 + 인스턴스별 volume.
 *
 * 동기화 정밀도는 system 의 audio output buffering 한계까지 — 영상은 별도 ExoPlayer 가
 * 재생하므로 100% sample 정렬은 불가능하지만 사용자 체감은 충분.
 */
@Composable
actual fun rememberStemMixer(): StemMixerHandle {
    val context = LocalContext.current
    val handle = remember {
        AndroidStemMixerHandle { ExoPlayer.Builder(context).build() }
    }
    DisposableEffect(handle) {
        onDispose { handle.release() }
    }
    return handle
}

private class AndroidStemMixerHandle(
    private val playerFactory: () -> ExoPlayer
) : StemMixerHandle {

    private val players = mutableMapOf<String, ExoPlayer>()
    private var playing = false

    override fun load(sources: List<StemMixerSource>) {
        // 기존 instance 정리.
        players.values.forEach { it.release() }
        players.clear()
        playing = false
        sources.forEach { src ->
            val p = playerFactory().apply {
                setMediaItem(MediaItem.fromUri(src.audioUrl))
                prepare()
                playWhenReady = false
                volume = 1f
            }
            players[src.stemId] = p
        }
    }

    override fun setVolume(stemId: String, volume: Float) {
        players[stemId]?.volume = volume.coerceIn(0f, 2f)
    }

    override fun play() {
        if (playing) return
        playing = true
        players.values.forEach { it.playWhenReady = true }
    }

    override fun pause() {
        if (!playing) return
        playing = false
        players.values.forEach { it.playWhenReady = false }
    }

    override fun seekTo(positionMs: Long) {
        players.values.forEach { it.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    override fun release() {
        players.values.forEach { it.release() }
        players.clear()
        playing = false
    }
}
