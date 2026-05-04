package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.dubcast.shared.domain.model.BgmClip
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL

/**
 * 클립별 AVAudioPlayer 를 hold 한 후 (id, sourceUri) 기준으로 dedupe / 재생성. video 재생 상태와
 * currentMs 에 sync. drift 0.3s 이상이면 currentTime 재정렬, 그 이하면 자연 재생에 맡김 (glitch 회피).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun BgmPlaybackSync(
    clips: List<BgmClip>,
    isPlaying: Boolean,
    currentMs: Long,
) {
    // clip.id → AVAudioPlayer. clip 추가/제거 시 새로 만들거나 정리.
    val players = remember { mutableMapOf<String, AVAudioPlayer>() }

    DisposableEffect(Unit) {
        onDispose {
            players.values.forEach { it.stop() }
            players.clear()
        }
    }

    // clips 변경 → 매핑 sync. 새 clip 은 만들고, 사라진 clip 은 stop+release.
    LaunchedEffect(clips.map { it.id to it.sourceUri }.toSet()) {
        val active = clips.associateBy { it.id }
        // 사라진 clip 정리.
        players.keys.filter { it !in active }.forEach { id ->
            players.remove(id)?.stop()
        }
        // 새 clip 또는 sourceUri 변경 시 player 생성.
        active.values.forEach { clip ->
            val existing = players[clip.id]
            val sameUri = existing != null
            if (!sameUri) {
                val nsUrl = if (clip.sourceUri.startsWith("file://")) {
                    NSURL.URLWithString(clip.sourceUri)
                        ?: NSURL.fileURLWithPath(clip.sourceUri.removePrefix("file://"))
                } else {
                    NSURL.fileURLWithPath(clip.sourceUri)
                }
                val p = runCatching {
                    AVAudioPlayer(contentsOfURL = nsUrl, error = null)
                }.getOrNull() ?: return@forEach
                p.enableRate = true
                p.prepareToPlay()
                players[clip.id] = p
            }
        }
    }

    // 재생 중 + 매 currentMs 갱신 시 sync. clip 의 글로벌 [start, start+globalDur) 범위 안이면
    // 재생 + 위치 정렬 (drift > 0.3s 시), 범위 밖이면 정지+0 으로 seek.
    LaunchedEffect(isPlaying, currentMs, clips) {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        }
        clips.forEach { clip ->
            val player = players[clip.id] ?: return@forEach
            val speed = clip.speedScale.coerceIn(0.5f, 2.0f)
            val volume = clip.volumeScale.coerceIn(0f, 1f)
            val globalDurMs = (clip.sourceDurationMs / clip.speedScale.coerceAtLeast(0.01f))
                .toLong().coerceAtLeast(1L)
            val inRange = currentMs in clip.startMs until (clip.startMs + globalDurMs)
            if (!inRange || !isPlaying) {
                if (player.playing) player.pause()
                if (!inRange) {
                    // 범위 밖 = 다음 진입 시 처음부터. 0 으로 reset.
                    player.currentTime = 0.0
                }
                return@forEach
            }
            // In range + playing — 위치/볼륨/속도 sync.
            player.volume = volume
            player.rate = speed
            val expectedSec = ((currentMs - clip.startMs) * speed).toDouble() / 1000.0
            if (kotlin.math.abs(player.currentTime - expectedSec) > 0.3) {
                player.currentTime = expectedSec.coerceAtLeast(0.0)
            }
            if (!player.playing) player.play()
        }
    }
}
