package com.vibi.cmp.platform

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
    /** stemId → 적용된 마지막 volume. load() 로 새 player 생성 시 적용. */
    private val pendingVolumes = mutableMapOf<String, Float>()
    /** groupId → 마지막 seek 위치(ms). load() 직후 새 player 가 올바른 offset 에서 시작. */
    private val pendingSeekByGroup = mutableMapOf<String, Long>()
    private var activeGroupId: String? = null
    private var playing = false

    private fun key(groupId: String, stemId: String) = "$groupId/$stemId"

    override fun load(sources: List<StemMixerSource>) {
        // 기존 player release 하고 새로 생성하되, playing / activeGroupId / pendingVolumes /
        // pendingSeekByGroup 는 보존. 화면 재진입 시 LaunchedEffect 발화 순서가 보장되지 않아
        // setActiveGroup/play/setVolume/seekTo 가 load 보다 먼저 호출돼도 새 player 가 즉시
        // 올바른 상태에 맞춰 재생되도록.
        players.values.forEach { it.release() }
        players.clear()
        groupOfPlayer.clear()
        sources.forEach { src ->
            val p = playerFactory().apply {
                setMediaItem(MediaItem.fromUri(src.audioUrl))
                prepare()
                playWhenReady = false
                volume = pendingVolumes[src.stemId] ?: 1f
            }
            val k = key(src.groupId, src.stemId)
            players[k] = p
            groupOfPlayer[k] = src.groupId
            pendingSeekByGroup[src.groupId]?.let { p.seekTo(it) }
        }
        applyActiveState()
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
        pendingVolumes[stemId] = v
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
        pendingSeekByGroup[active] = pos
        players.forEach { (k, p) ->
            if (groupOfPlayer[k] == active) p.seekTo(pos)
        }
    }

    override fun release() {
        players.values.forEach { it.release() }
        players.clear()
        groupOfPlayer.clear()
        pendingVolumes.clear()
        pendingSeekByGroup.clear()
        playing = false
        activeGroupId = null
    }
}
