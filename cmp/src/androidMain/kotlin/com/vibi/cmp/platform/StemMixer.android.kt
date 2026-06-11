package com.vibi.cmp.platform

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import java.io.File

/**
 * Android: stem 별 ExoPlayer 인스턴스. 같은 시점에 동시 재생 + 인스턴스별 volume.
 *
 * ExoPlayer.setVolume 은 0..1 하드 클램프 — 1.0..2.0 부스트는 [GainAudioProcessor] 가 PCM 16-bit
 * sample 을 직접 스케일링. UI 슬라이더의 0..2 가 preview/render 동일 amplitude 로 적용.
 *
 * 동기화 정밀도는 system 의 audio output buffering 한계까지 — 영상은 별도 ExoPlayer 가
 * 재생하므로 100% sample 정렬은 불가능하지만 사용자 체감은 충분.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
actual fun rememberStemMixer(): StemMixerHandle {
    val context = LocalContext.current.applicationContext
    val handle = remember { AndroidStemMixerHandle(context) }
    DisposableEffect(handle) {
        onDispose { handle.release() }
    }
    return handle
}

@UnstableApi
private class AndroidStemMixerHandle(
    private val context: Context,
) : StemMixerHandle {

    private class PlayerWithGain(
        val player: ExoPlayer,
        val gain: GainAudioProcessor,
    )

    /** key = "groupId/stemId" — multi-directive 지원. */
    private val players = mutableMapOf<String, PlayerWithGain>()
    private val groupOfPlayer = mutableMapOf<String, String>()
    /** stemId → 적용된 마지막 volume (0..2). load() 로 새 player 생성 시 적용. */
    private val pendingVolumes = mutableMapOf<String, Float>()
    /** groupId → 마지막 seek 위치(ms). load() 직후 새 player 가 올바른 offset 에서 시작. */
    private val pendingSeekByGroup = mutableMapOf<String, Long>()
    /** 적용된 마지막 rate. 새 player 도 동일 속도로 시작. 1.0 = 원본. */
    private var pendingRate: Float = 1f
    private var activeGroupId: String? = null
    private var playing = false

    private fun key(groupId: String, stemId: String) = "$groupId/$stemId"

    private fun buildPlayer(): PlayerWithGain {
        val gain = GainAudioProcessor()
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(
                        DefaultAudioProcessorChain(gain)
                    )
                    .build()
            }
        }
        val player = ExoPlayer.Builder(context, renderersFactory).build()
        return PlayerWithGain(player, gain)
    }

    /** v 분배: 0..1 → ExoPlayer.volume, 1..2 → processor.gain (sample scale). 합쳐서 0..2. */
    private fun applyVolume(pwg: PlayerWithGain, v: Float) {
        val clamped = v.coerceIn(0f, 2f)
        pwg.player.volume = clamped.coerceAtMost(1f)
        pwg.gain.gain = if (clamped > 1f) clamped else 1f
    }

    override fun load(sources: List<StemMixerSource>) {
        // 기존 player release 하고 새로 생성하되, playing / activeGroupId / pendingVolumes /
        // pendingSeekByGroup / pendingRate 는 보존.
        players.values.forEach { it.player.release() }
        players.clear()
        groupOfPlayer.clear()
        sources.forEach { src ->
            // 영구 캐시된 로컬 파일 절대경로는 file:// URI 로 — 서버 연결이 끊겨도 재생. 원격 URL 은 그대로.
            val uri = if (src.audioUrl.startsWith("http://") || src.audioUrl.startsWith("https://")) {
                Uri.parse(src.audioUrl)
            } else {
                Uri.fromFile(File(src.audioUrl))
            }
            val pwg = buildPlayer().apply {
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = false
                if (pendingRate != 1f) {
                    player.playbackParameters = PlaybackParameters(pendingRate)
                }
            }
            applyVolume(pwg, pendingVolumes[src.stemId] ?: 1f)
            val k = key(src.groupId, src.stemId)
            players[k] = pwg
            groupOfPlayer[k] = src.groupId
            pendingSeekByGroup[src.groupId]?.let { pwg.player.seekTo(it) }
        }
        applyActiveState()
    }

    override fun setActiveGroup(groupId: String?) {
        if (activeGroupId == groupId) return
        activeGroupId = groupId
        applyActiveState()
    }

    private fun applyActiveState() {
        players.forEach { (k, pwg) ->
            val isActive = groupOfPlayer[k] == activeGroupId
            pwg.player.playWhenReady = isActive && playing
        }
    }

    override fun setVolume(stemId: String, volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        pendingVolumes[stemId] = v
        players.entries
            .filter { (k, _) -> k.endsWith("/$stemId") }
            .forEach { (_, pwg) -> applyVolume(pwg, v) }
    }

    override fun setRate(rate: Float) {
        val r = rate.coerceIn(0.5f, 2.0f)
        if (pendingRate == r) return
        pendingRate = r
        val params = PlaybackParameters(r)
        players.values.forEach { it.player.playbackParameters = params }
    }

    override fun play() {
        if (playing) return
        playing = true
        applyActiveState()
    }

    override fun pause() {
        if (!playing) return
        playing = false
        players.values.forEach { it.player.playWhenReady = false }
    }

    override fun seekTo(positionMs: Long) {
        val pos = positionMs.coerceAtLeast(0L)
        val active = activeGroupId ?: return
        pendingSeekByGroup[active] = pos
        players.forEach { (k, pwg) ->
            if (groupOfPlayer[k] == active) pwg.player.seekTo(pos)
        }
    }

    override fun release() {
        players.values.forEach { it.player.release() }
        players.clear()
        groupOfPlayer.clear()
        pendingVolumes.clear()
        pendingSeekByGroup.clear()
        playing = false
        activeGroupId = null
    }
}
