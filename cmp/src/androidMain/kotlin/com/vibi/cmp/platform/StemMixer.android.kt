package com.vibi.cmp.platform

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import com.vibi.cmp.BuildConfig
import kotlin.math.abs

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
    /** key → 적용된 audioUrl. 같은 key 라도 url 이 변하면 (token refresh 등) player 재생성. iOS urlByKey 대응. */
    private val urlByKey = mutableMapOf<String, String>()
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
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        // handleAudioFocus=false 필수. stem 믹서는 여러 player(스템별) + 영상 player 가 동시에
        // 함께 울려야 하는 한 묶음인데, 각 player 가 handleAudioFocus=true 로 AUDIOFOCUS_GAIN(배타적)
        // 을 요청하면 마지막 요청자만 포커스를 쥐고 나머지는 onAudioFocusChange(-1)=LOSS 를 받아
        // 자동 pause 된다. directive 구간에선 영상이 포커스를 쥔 채 mute(vol 0) 라서 결과가 완전 무음.
        // 앱 전체의 audio focus 는 영상 VideoPlayer(단일 owner, handleAudioFocus=true)가 관리하고,
        // stem player 들은 포커스 경합 없이 렌더만 한다. (iOS 는 공유 AVAudioSession 이라 무관.)
        val player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            // 이어폰 분리 등 audio output 이 speaker 로 강제 전환되기 직전 자동 pause
            // (ACTION_AUDIO_BECOMING_NOISY). iOS AVAudioSession route-change 대응.
            .setHandleAudioBecomingNoisy(true)
            .build()
        return PlayerWithGain(player, gain)
    }

    /** v 분배: 0..1 → ExoPlayer.volume, 1..2 → processor.gain (sample scale). 합쳐서 0..2. */
    private fun applyVolume(pwg: PlayerWithGain, v: Float) {
        val clamped = v.coerceIn(0f, 2f)
        pwg.player.volume = clamped.coerceAtMost(1f)
        pwg.gain.gain = if (clamped > 1f) clamped else 1f
    }

    /**
     * BFF API path(`/api/...`) 또는 상대 경로면 BFF base URL 을 prepend 해 절대 URL 로 보정.
     * http(s) 이거나 로컬 파일 절대 경로(`/data/...`, `/storage/...`)는 그대로. iOS resolveAbsoluteAudioUrl 대응.
     */
    private fun resolveAbsoluteAudioUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val isApiPath = url.startsWith("/api/")
        // `/` 로 시작하면 로컬 파일 절대 경로(즉석 녹음·picker 결과 포함) — `/api/` 만 BFF 로 본다.
        // 반대로 `/` 로 시작하지 않으면 서버 상대 경로로 간주해 BFF base 를 붙인다.
        val isRelative = !url.startsWith("/")
        if (!isApiPath && !isRelative) return url
        val base = BuildConfig.BFF_BASE_URL.takeIf { it.isNotEmpty() } ?: return url
        return "${base.trimEnd('/')}/${url.trimStart('/')}"
    }

    /**
     * 보정된 URL 을 ExoPlayer 가 재생할 Uri 로 변환. remote 는 그대로 streaming, 로컬은 file://.
     * BFF-상대 경로는 [resolveAbsoluteAudioUrl] 로 먼저 절대화한 뒤 공용 [resolveMediaUri] 에 위임.
     */
    private fun resolveUri(url: String): Uri =
        resolveMediaUri(resolveAbsoluteAudioUrl(url))

    private fun buildPlayerFor(src: StemMixerSource): PlayerWithGain {
        val pwg = buildPlayer().apply {
            player.setMediaItem(MediaItem.fromUri(resolveUri(src.audioUrl)))
            player.prepare()
            player.playWhenReady = false
            if (pendingRate != 1f) {
                player.playbackParameters = PlaybackParameters(pendingRate)
            }
        }
        applyVolume(pwg, pendingVolumes[src.stemId] ?: 1f)
        pendingSeekByGroup[src.groupId]?.let { pwg.player.seekTo(it) }
        return pwg
    }

    override fun load(sources: List<StemMixerSource>) {
        // Incremental: incoming 을 key->audioUrl 로 diff. URL 이 변한 player 만 release+rebuild,
        // 동일 URL 은 기존 player 유지(playWhenReady/seek 보존). iOS load() urlByKey diff 대응.
        val incomingKeys = sources.map { key(it.groupId, it.stemId) }.toSet()

        // 더 이상 존재하지 않는 key 의 player 정리 — orphan ExoPlayer 누수 방지.
        players.keys.filter { it !in incomingKeys }.forEach { k ->
            players.remove(k)?.player?.release()
            groupOfPlayer.remove(k)
            urlByKey.remove(k)
        }

        sources.forEach { src ->
            val k = key(src.groupId, src.stemId)
            groupOfPlayer[k] = src.groupId
            val existing = players[k]
            if (existing != null && urlByKey[k] == src.audioUrl) {
                // URL 동일 — 기존 player 그대로 유지(재생성 시 끊김/재버퍼 회피).
                return@forEach
            }
            // 신규이거나 URL 변경 — 기존 player release 후 재생성.
            players.remove(k)?.player?.release()
            val pwg = buildPlayerFor(src)
            players[k] = pwg
            urlByKey[k] = src.audioUrl
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
        // 매 tick 마다 호출되어도 활성 player 의 현재 위치와 거의 일치하면 seek skip — per-tick stutter 회피.
        // iOS IosStemMixerHandle.seekTo 의 50ms drift 가드 대응.
        val activePlayer = players.entries
            .firstOrNull { (k, _) -> groupOfPlayer[k] == active }?.value?.player
        if (activePlayer != null && abs(pos - activePlayer.currentPosition) <= 50L) return
        pendingSeekByGroup[active] = pos
        players.forEach { (k, pwg) ->
            if (groupOfPlayer[k] == active) pwg.player.seekTo(pos)
        }
    }

    override fun release() {
        players.values.forEach { it.player.release() }
        players.clear()
        groupOfPlayer.clear()
        urlByKey.clear()
        pendingVolumes.clear()
        pendingSeekByGroup.clear()
        playing = false
        activeGroupId = null
    }
}
