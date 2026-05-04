package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.insertTimeRange
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.scaleTimeRange
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.tracksWithMediaType
import platform.AVFoundation.volume
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.CMTimeSubtract
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObjectProtocol

/**
 * Multi-segment 미리보기를 단일 AVPlayer + AVMutableComposition 으로 구현. 각 segment 의
 * trim 영역을 composition video/audio track 에 시간순 insert + speedScale 만큼 scaleTimeRange
 * 압축. 결과: composition timeline = 사용자가 보는 글로벌 timeline 과 정확히 일치.
 *
 * 이전 AVQueuePlayer multi-item 방식의 문제 (transition 추적 부정확 / 자식 segment forwardEnd
 * 누락 / volume·rate 의 carry / 삭제 후 stale state) 모두 우회. trade-off: composition 빌드는
 * playlist 변경마다 매번 재생성 (sourceUri/trim/speed 어떤 것이라도 변경) — asset lazy load
 * 영향이 있을 수 있어 AVURLAssetPreferPreciseDurationAndTimingKey=true 옵션 필수.
 *
 * 미해결: per-segment volumeScale 은 AVMutableAudioMix 필요한데 K/N AVFoundation cinterop
 * 의 일부 audio mix setter 미노출 (CLAUDE.md known iOS bug). 임시: 첫 segment 의 volumeScale
 * 만 player.volume (글로벌) 로 적용.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    items: List<VideoPlayerItem>,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (Long) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier
) {
    fun toFileUrl(s: String): NSURL = if (s.startsWith("file://"))
        NSURL.URLWithString(s) ?: NSURL.fileURLWithPath(s.removePrefix("file://"))
    else NSURL.fileURLWithPath(s)

    // 모든 segment 속성을 key 에 포함 — 변경 시 composition 재빌드. asset lazy load 영향 최소화.
    val playlistKey = items.joinToString("|") {
        "${it.sourceUri}:${it.trimStartMs}:${it.trimEndMs}:${it.speedScale}"
    }

    val player = remember(playlistKey) {
        val composition = AVMutableComposition()
        @Suppress("UNCHECKED_CAST")
        val videoTrack = composition.addMutableTrackWithMediaType(
            AVMediaTypeVideo, kCMPersistentTrackID_Invalid
        ) as? AVMutableCompositionTrack
        @Suppress("UNCHECKED_CAST")
        val audioTrack = composition.addMutableTrackWithMediaType(
            AVMediaTypeAudio, kCMPersistentTrackID_Invalid
        ) as? AVMutableCompositionTrack

        items.forEach { item ->
            val asset = AVURLAsset(
                uRL = toFileUrl(item.sourceUri),
                options = mapOf<Any?, Any>(AVURLAssetPreferPreciseDurationAndTimingKey to true),
            )
            @Suppress("UNCHECKED_CAST")
            val srcVideo = (asset.tracksWithMediaType(AVMediaTypeVideo) as? List<AVAssetTrack>)
                ?.firstOrNull()
            @Suppress("UNCHECKED_CAST")
            val srcAudio = (asset.tracksWithMediaType(AVMediaTypeAudio) as? List<AVAssetTrack>)
                ?.firstOrNull()

            val trimStartSec = item.trimStartMs / 1000.0
            val trimStart = CMTimeMakeWithSeconds(trimStartSec, preferredTimescale = 1000)
            val trimEnd = if (item.trimEndMs > 0L) {
                CMTimeMakeWithSeconds(item.trimEndMs / 1000.0, preferredTimescale = 1000)
            } else {
                asset.duration
            }
            // CMTimeRange 는 K/N 에서 CValue 라 멤버 접근 불가 — duration 변수 별도 보관.
            val sourceDur = CMTimeSubtract(trimEnd, trimStart)
            val sourceDurSec = CMTimeGetSeconds(sourceDur)
            val sourceRange = CMTimeRangeMake(start = trimStart, duration = sourceDur)
            val insertAt = composition.duration

            try {
                srcVideo?.let {
                    videoTrack?.insertTimeRange(sourceRange, ofTrack = it, atTime = insertAt, error = null)
                }
                srcAudio?.let {
                    audioTrack?.insertTimeRange(sourceRange, ofTrack = it, atTime = insertAt, error = null)
                }
            } catch (_: Throwable) {
                // insertTimeRange 실패 시 (트랙 누락 / 잘못된 range) skip — 다음 item 으로 진행.
            }

            // speedScale 적용 — 방금 삽입한 영역을 scale (1/speedScale) 로 압축/확장.
            // sourceLen / speedScale = composition 에서 차지하는 길이 = 글로벌 effectiveDur.
            if (item.speedScale != 1f && item.speedScale > 0f && !sourceDurSec.isNaN()) {
                val newDurSec = sourceDurSec / item.speedScale.toDouble()
                val newDur = CMTimeMakeWithSeconds(newDurSec, preferredTimescale = 1000)
                val insertedRange = CMTimeRangeMake(start = insertAt, duration = sourceDur)
                videoTrack?.scaleTimeRange(insertedRange, toDuration = newDur)
                audioTrack?.scaleTimeRange(insertedRange, toDuration = newDur)
            }
        }

        AVPlayer(playerItem = AVPlayerItem(asset = composition))
    }

    val controller = remember(player) {
        AVPlayerViewController().apply {
            this.player = player
            showsPlaybackControls = false
            allowsPictureInPicturePlayback = false
            updatesNowPlayingInfoCenter = false
            videoGravity = AVLayerVideoGravityResizeAspect
            overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        }
    }

    val isPlayingState = rememberUpdatedState(isPlaying)
    val onEndedState = rememberUpdatedState(onEnded)

    DisposableEffect(player) {
        val bgObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> player.pause() }
        )
        // 단일 AVPlayerItem 이라 composition 끝 도달 = 전체 timeline 끝.
        val endObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> onEndedState.value() }
        )
        // 첫 segment 의 volume 만 적용 (per-segment volume 은 audioMix 필요 — known K/N 미노출).
        items.firstOrNull()?.let { player.volume = it.volumeScale.coerceIn(0f, 1f) }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(bgObserver)
            NSNotificationCenter.defaultCenter.removeObserver(endObserver)
            player.pause()
            controller.player = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // composition 의 scaleTimeRange 가 segment 별 speed 를 이미 처리하므로 player.rate=1 고정.
            // 이전 (AVQueuePlayer 단계) 의 per-item rate 적용은 불필요.
            player.rate = 1f
            player.play()
        } else {
            player.pause()
        }
    }

    // global seekToMs = composition timeline 의 ms 그대로. transition 추적 불필요 — 단일 player.
    LaunchedEffect(seekToMs) {
        if (seekToMs == null) return@LaunchedEffect
        val curSec = CMTimeGetSeconds(player.currentTime())
        val targetSec = seekToMs / 1000.0
        // self-echo 차단: position 폴링 → ViewModel state → seekToMs 미세 변동 — 0.5초 가드.
        if (!curSec.isNaN() && kotlin.math.abs(curSec - targetSec) <= 0.5) return@LaunchedEffect
        player.seekToTime(CMTimeMakeWithSeconds(targetSec, preferredTimescale = 1000))
    }

    LaunchedEffect(player) {
        // composition 내 현재 재생 중인 segment 의 volumeScale 을 player.volume 에 동기화.
        // AVMutableComposition 은 단일 AVPlayerItem → player.volume 글로벌 단일 값. multi-segment
        // 볼륨 차이는 AVMutableAudioMix 필요한데 K/N audio mix setter 일부 미노출 (CLAUDE.md
        // known iOS bug). 차선책: 글로벌 ms → segment idx 역검색 후 그 segment 의 volume 적용 →
        // 재생 위치 따라 player.volume 자동 전환 (sample-accurate 는 아니지만 200ms 단위 OK).
        var lastVolume = Float.NaN
        while (true) {
            val sec = CMTimeGetSeconds(player.currentTime())
            if (!sec.isNaN()) {
                val globalMs = (sec * 1000.0).toLong()
                // globalMs → segment idx 역검색. 각 segment 의 글로벌 길이 = sourceLen / speedScale.
                var acc = 0L
                var curIdx = items.size - 1
                for ((idx, it) in items.withIndex()) {
                    val srcLen = if (it.trimEndMs > 0L) {
                        (it.trimEndMs - it.trimStartMs).coerceAtLeast(0L)
                    } else Long.MAX_VALUE
                    val segGlobalDur = if (srcLen == Long.MAX_VALUE) Long.MAX_VALUE
                        else if (it.speedScale > 0f) (srcLen / it.speedScale).toLong() else srcLen
                    if (segGlobalDur == Long.MAX_VALUE || globalMs < acc + segGlobalDur) {
                        curIdx = idx; break
                    }
                    acc += segGlobalDur
                }
                items.getOrNull(curIdx)?.let { item ->
                    val curVol = item.volumeScale.coerceIn(0f, 1f)
                    if (curVol != lastVolume) {
                        player.volume = curVol
                        lastVolume = curVol
                    }
                }
                onPositionChanged(globalMs)
            }
            delay(200)
        }
    }

    UIKitViewController(
        factory = { controller },
        modifier = modifier
    )
}
