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

    /** key = "groupId/stemId" — multi-directive 지원. */
    private val players = mutableMapOf<String, ExoPlayer>()
    private val groupOfPlayer = mutableMapOf<String, String>()
    private var activeGroupId: String? = null
    private var playing = false

    private fun key(groupId: String, stemId: String) = "$groupId/$stemId"

    override fun load(sources: List<StemMixerSource>) {
        players.values.forEach { it.release() }
        players.clear()
        groupOfPlayer.clear()
        playing = false
        activeGroupId = null
        sources.forEach { src ->
            val p = playerFactory().apply {
                setMediaItem(MediaItem.fromUri(src.audioUrl))
                prepare()
                playWhenReady = false
                volume = 1f
            }
            val k = key(src.groupId, src.stemId)
            players[k] = p
            groupOfPlayer[k] = src.groupId
        }
    }

    override fun setActiveGroup(groupId: String?) {
        if (activeGroupId == groupId) return
        activeGroupId = groupId
        applyActiveState()
    }

    private fun applyActiveState() {
        players.forEach { (k, p) ->
            val isActive = groupOfPlayer[k] == activeGroupId
            p.playWhenReady = isActive && playing
        }
    }

    override fun setVolume(stemId: String, volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        players.entries
            .filter { (k, _) -> k.endsWith("/$stemId") }
            .forEach { (_, p) -> p.volume = v }
    }

    override fun play() {
        if (playing) return
        playing = true
        applyActiveState()
    }

    override fun pause() {
        if (!playing) return
        playing = false
        players.values.forEach { it.playWhenReady = false }
    }

    override fun seekTo(positionMs: Long) {
        val pos = positionMs.coerceAtLeast(0L)
        val active = activeGroupId ?: return
        players.forEach { (k, p) ->
            if (groupOfPlayer[k] == active) p.seekTo(pos)
        }
    }

    override fun release() {
        players.values.forEach { it.release() }
        players.clear()
        groupOfPlayer.clear()
        playing = false
        activeGroupId = null
    }
}
