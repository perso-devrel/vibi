package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.platform.resolveStoredUriToFileUrl
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

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
    val players = remember { mutableMapOf<String, AVAudioPlayer>() }
    // id → 현재 player 가 로드한 sourceUri. 같은 id 로 sourceUri 가 바뀌면 (재선택/trim 소스 교체)
    // 옛 player 를 stop·교체하기 위한 추적. 존재 여부만 보면 stale 오디오가 계속 재생된다.
    val playerUrls = remember { mutableMapOf<String, String>() }

    DisposableEffect(Unit) {
        ensurePlaybackSession()
        onDispose {
            players.values.forEach { it.stop() }
            players.clear()
            playerUrls.clear()
        }
    }

    // clips 변경 마다 category 재적용 — AudioRecorder 가 .playAndRecord 로 바꿔놓으면 이후 playback
    // 이 earpiece 로 라우팅돼 녹음본이 작게 들리는 사고.
    val clipsKey = remember(clips) { clips.map { it.id to it.sourceUri }.toSet() }
    LaunchedEffect(clipsKey) {
        ensurePlaybackSession()
        val active = clips.associateBy { it.id }
        // 사라진 clip 정리.
        players.keys.filter { it !in active }.forEach { id ->
            players.remove(id)?.stop()
            playerUrls.remove(id)
        }
        // 새 clip 또는 sourceUri 변경 시 player 생성.
        active.values.forEach { clip ->
            val existing = players[clip.id]
            val sameUri = existing != null && playerUrls[clip.id] == clip.sourceUri
            if (!sameUri) {
                // sourceUri 가 바뀐 경우 옛 player 를 먼저 정지·제거 (stale 오디오 + 미해제 방지).
                players.remove(clip.id)?.stop()
                playerUrls.remove(clip.id)
                // resolver 가 상대경로 / 절대 / file:// / 옛 UUID 모두 처리.
                val nsUrl = resolveStoredUriToFileUrl(clip.sourceUri) ?: return@forEach
                val p = runCatching {
                    AVAudioPlayer(contentsOfURL = nsUrl, error = null)
                }.getOrNull() ?: return@forEach
                p.enableRate = true
                p.prepareToPlay()
                players[clip.id] = p
                playerUrls[clip.id] = clip.sourceUri
            }
        }
    }

    // 재생 중 + 매 currentMs 갱신 시 sync. clip 의 글로벌 [start, start+globalDur) 범위 안이면
    // 재생 + 위치 정렬 (drift > 0.3s 시), 범위 밖이면 정지+0 으로 seek.
    LaunchedEffect(isPlaying, currentMs, clips) {
        clips.forEach { clip ->
            val player = players[clip.id] ?: return@forEach
            val speed = clip.speedScale.coerceIn(0.5f, 2.0f)
            val volume = clip.volumeScale.coerceIn(0f, 1f)
            // trim 적용된 source 길이 (sourceTrimStartMs > 0 또는 sourceTrimEndMs > 0 이면 sub-range).
            // globalDur 는 그 값에 speed 반영. 정상 path BgmClip.MIN_SPEED=0.5 와 일관된 clamp.
            val globalDurMs = (clip.effectiveSourceDurationMs / speed)
                .toLong().coerceAtLeast(1L)
            val inRange = currentMs in clip.startMs until (clip.startMs + globalDurMs)
            if (!inRange || !isPlaying) {
                if (player.playing) player.pause()
                if (!inRange) {
                    // 범위 밖 = 다음 진입 시 trim 시작점부터. seek 는 in-range 진입 시 보정.
                    player.currentTime = clip.sourceTrimStartMs / 1000.0
                }
                return@forEach
            }
            // In range + playing — 위치/볼륨/속도 sync. expectedSec 에 sourceTrimStartMs 오프셋 더함.
            player.volume = volume
            player.rate = speed
            val expectedSec = ((currentMs - clip.startMs) * speed + clip.sourceTrimStartMs).toDouble() / 1000.0
            if (kotlin.math.abs(player.currentTime - expectedSec) > 0.3) {
                player.currentTime = expectedSec.coerceAtLeast(0.0)
            }
            if (!player.playing) player.play()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ensurePlaybackSession() {
    runCatching {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }
}
