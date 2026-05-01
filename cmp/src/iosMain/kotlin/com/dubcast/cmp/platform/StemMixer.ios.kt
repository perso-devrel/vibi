package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.numberWithFloat
import platform.Foundation.setValue

/**
 * iOS: stem 별 AVPlayer 동시 재생. K/N AVFoundation cinterop 의 `AVPlayer.volume` setter 가
 * 미노출이라 직접 dot-set 은 unresolved — NSObject KVC (`setValue:forKey:`) 로 우회.
 *
 * `AVPlayer` 는 `volume` property 를 가진 NSObject 라 KVC 키 `"volume"` 으로 NSNumber 주입 가능.
 * cinterop 빌드 변경 / Swift bridge 도입 없이 timeline preview 에서 stem 들이 들리도록 해주는
 * 가장 작은 변화. 정밀 동기화는 system audio buffering 한계까지 — 사용자 체감 충분.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberStemMixer(): StemMixerHandle {
    val handle = remember { IosStemMixerHandle() }
    DisposableEffect(handle) {
        onDispose { handle.release() }
    }
    return handle
}

/**
 * NSURL.URLWithString 은 ASCII-only URI 만 받음 — 한글 stem 파일명 등 non-ASCII 가 path 에
 * 섞이면 nil 반환. UTF-8 percent-encoding 한 fallback 으로 재시도.
 *
 * `:`, `/`, `?` 같은 URL syntax 문자는 인코딩하면 안 되므로 ASCII 영역(`< 0x80`) 은 그대로 두고
 * non-ASCII 바이트만 `%HH` 로 바꿈. 이미 인코딩된 입력이 들어와도 ASCII 부분은 보존되어 멱등.
 */
private fun stemNSURL(raw: String): NSURL? {
    NSURL.URLWithString(raw)?.let { return it }
    val sb = StringBuilder()
    for (c in raw) {
        if (c.code < 0x80) {
            sb.append(c)
        } else {
            for (b in c.toString().encodeToByteArray()) {
                val v = b.toInt() and 0xFF
                val hi = (v ushr 4) and 0xF
                val lo = v and 0xF
                sb.append('%')
                sb.append(if (hi < 10) ('0' + hi) else ('A' + (hi - 10)))
                sb.append(if (lo < 10) ('0' + lo) else ('A' + (lo - 10)))
            }
        }
    }
    return NSURL.URLWithString(sb.toString())
}

@OptIn(ExperimentalForeignApi::class)
private class IosStemMixerHandle : StemMixerHandle {

    private val players = mutableMapOf<String, AVPlayer>()
    private var playing = false

    override fun load(sources: List<StemMixerSource>) {
        players.values.forEach { it.pause() }
        players.clear()
        playing = false
        sources.forEach { src ->
            val url = stemNSURL(src.audioUrl) ?: return@forEach
            val player = AVPlayer(uRL = url)
            // 기본 볼륨 1.0 — setVolume 으로 즉시 덮어쓸 예정.
            player.setValue(NSNumber.numberWithFloat(1.0f), forKey = "volume")
            players[src.stemId] = player
        }
    }

    override fun setVolume(stemId: String, volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        players[stemId]?.setValue(NSNumber.numberWithFloat(v), forKey = "volume")
    }

    override fun play() {
        if (playing) return
        playing = true
        players.values.forEach { it.play() }
    }

    override fun pause() {
        if (!playing) return
        playing = false
        players.values.forEach { it.pause() }
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        val time = CMTimeMakeWithSeconds(seconds, preferredTimescale = 600)
        players.values.forEach { it.seekToTime(time) }
    }

    override fun release() {
        players.values.forEach { it.pause() }
        players.clear()
        playing = false
    }
}
