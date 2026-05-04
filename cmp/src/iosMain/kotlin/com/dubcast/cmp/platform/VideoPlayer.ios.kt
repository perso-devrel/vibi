package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
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
import platform.AVFoundation.setForwardPlaybackEndTime
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.tracksWithMediaType
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.CMTimeSubtract
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIView
import platform.darwin.NSObjectProtocol
import kotlin.native.ObjCName

/**
 * Two-mode 미리보기:
 *
 * 1) **Single-segment fast path** — items.size == 1 일 때. composition 안 만들고
 *    `AVPlayer(playerItem = AVPlayerItem(URL))` 직결. AVPlayer 가 asset lazy load 를
 *    내부적으로 처리하므로 검정 프레임 없음 + trim/speed/volume 변경 시 player rebuild
 *    없이 파라미터만 패치 → 편집이 즉각 반영. 영상 편집 워크플로의 hot path.
 *
 * 2) **Multi-segment composition path** — items.size > 1 (split 결과 등) 일 때.
 *    AVMutableComposition + scaleTimeRange 로 글로벌 timeline 정확히 매핑. AVQueuePlayer
 *    의 transition/forwardEnd/volume carry 결함 회피용.
 *
 * sourceUri 만 다르고 trim/speed/volume 변동만 있는 경우 player 인스턴스 유지 — 재빌드
 * 없이 setForwardPlaybackEndTime + seek + rate + volume 으로 적용.
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
    if (items.isEmpty()) return
    if (items.size == 1) {
        SingleItemVideoPlayer(items[0], isPlaying, seekToMs, onPositionChanged, onEnded, modifier)
    } else {
        MultiSegmentVideoPlayer(items, isPlaying, seekToMs, onPositionChanged, onEnded, modifier)
    }
}

private fun toFileUrl(s: String): NSURL = if (s.startsWith("file://"))
    NSURL.URLWithString(s) ?: NSURL.fileURLWithPath(s.removePrefix("file://"))
else NSURL.fileURLWithPath(s)

/**
 * UIView 서브클래스 — `layoutSubviews` override 로 sublayer (AVPlayerLayer) 의 frame 을
 * 항상 bounds 에 동기화. AVPlayerViewController 는 자체 observer 가 player 속성 변경 시
 * 내부 view hierarchy 를 재배치해 AVPlayerLayer 가 블랭크되는 결함이 있어 버리고, raw
 * AVPlayerLayer 직결로 가서 그 영향을 제거.
 */
@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCName::class)
@ObjCName("VibiAVPlayerHostView")
internal class AVPlayerHostView constructor(
    frame: CValue<CGRect>,
) : UIView(frame = frame) {
    val playerLayer: AVPlayerLayer = AVPlayerLayer().apply {
        videoGravity = AVLayerVideoGravityResizeAspect
    }

    init {
        layer.addSublayer(playerLayer)
    }

    @ObjCAction
    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }
}

/**
 * 단일 video segment fast path —
 *
 * 핵심 발견: AVPlayer 인스턴스에 rate/volume 같은 속성 mutation 을 누적하면 일정 시점부터
 * AVPlayerLayer 가 frame 을 안 그리는 결함이 있음 (raw layer/AVPlayerViewController 둘 다).
 * seekToTime/replace 우회로도 회복 안 됨. 화면 나갔다 들어오면 새 player 가 만들어져 정상.
 *
 * 결론: trim/speed/volume 어떤 게 바뀌든 AVPlayer + AVPlayerItem 자체를 새로 만든다.
 *  - AVURLAsset 은 sourceUri 별 cache → 디스크 재로드 0.
 *  - hostView/AVPlayerLayer 는 영구 유지 (UIKitView 의 factory 는 1회) → view 재attach 없음.
 *  - UIKitView.update 에서 hostView.playerLayer.player = newPlayer 로 갈아끼움 → 즉시 new
 *    output. AVPlayer 자체는 매우 가벼운 객체라 rebuild 비용 무시할 수 있음.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun SingleItemVideoPlayer(
    item: VideoPlayerItem,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (Long) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier,
) {
    // Asset 은 sourceUri 별 cache. 같은 영상 편집 중엔 재로드 비용 0.
    val asset = remember(item.sourceUri) {
        AVURLAsset(uRL = toFileUrl(item.sourceUri), options = null)
    }

    // trim/speed 만 player rebuild 키. volume 은 mutation 으로 별도 적용 — volume 단독
    // 변경은 layer 블랭크 트리거가 아니었음. volume 도 rebuild 키에 넣으면 사용자가 속도+볼륨
    // 슬라이더 동시 조작 시 player rebuild 가 중첩 발생 → audio session/타이밍 충돌.
    val player = remember(
        item.sourceUri, item.trimStartMs, item.trimEndMs, item.speedScale,
    ) {
        val playerItem = AVPlayerItem(asset = asset)
        if (item.trimEndMs > 0L) {
            playerItem.setForwardPlaybackEndTime(
                CMTimeMakeWithSeconds(item.trimEndMs / 1000.0, preferredTimescale = 1000)
            )
        }
        AVPlayer(playerItem = playerItem)
    }

    // volume 은 player 인스턴스에 직접 mutation. rebuild 안 함.
    LaunchedEffect(player, item.volumeScale) {
        player.volume = item.volumeScale.coerceIn(0f, 1f)
    }

    // hostView 는 한 번만. UIKitView factory 도 1회 호출 → view 재attach 없음.
    val hostView = remember {
        AVPlayerHostView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
    }

    val onEndedState = rememberUpdatedState(onEnded)
    val onPositionChangedState = rememberUpdatedState(onPositionChanged)

    DisposableEffect(player) {
        val bgObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> player.pause() }
        )
        val endObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> onEndedState.value() }
        )
        player.seekToTime(
            CMTimeMakeWithSeconds(item.trimStartMs / 1000.0, preferredTimescale = 1000)
        )
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(bgObserver)
            NSNotificationCenter.defaultCenter.removeObserver(endObserver)
            player.pause()
        }
    }

    LaunchedEffect(player, isPlaying) {
        if (isPlaying) {
            player.rate = item.speedScale.coerceIn(0.25f, 4f)
        } else {
            player.pause()
        }
    }

    // 글로벌 ms × speed → segment-local ms (+ trimStart). 0.5초 가드는 self-echo 차단용
    // (position 폴링 → ViewModel state → seekToMs 미세 변동 루프).
    LaunchedEffect(player, seekToMs) {
        if (seekToMs == null) return@LaunchedEffect
        val speed = if (item.speedScale > 0f) item.speedScale else 1f
        val targetLocalSec = (seekToMs * speed) / 1000.0 + item.trimStartMs / 1000.0
        val curSec = CMTimeGetSeconds(player.currentTime())
        if (!curSec.isNaN() && kotlin.math.abs(curSec - targetLocalSec) <= 0.5) return@LaunchedEffect
        player.seekToTime(CMTimeMakeWithSeconds(targetLocalSec, preferredTimescale = 1000))
    }

    LaunchedEffect(player) {
        val speed = if (item.speedScale > 0f) item.speedScale else 1f
        val trimStartSec = item.trimStartMs / 1000.0
        var lastReportedMs = -1L
        while (true) {
            val sec = CMTimeGetSeconds(player.currentTime())
            if (!sec.isNaN()) {
                val globalMs = (((sec - trimStartSec) / speed) * 1000.0).toLong().coerceAtLeast(0L)
                if (globalMs != lastReportedMs) {
                    onPositionChangedState.value(globalMs)
                    lastReportedMs = globalMs
                }
            }
            delay(200)
        }
    }

    UIKitView(
        factory = { hostView },
        modifier = modifier,
        update = { v ->
            // player rebuild 시마다 layer 의 player 갈아끼움. layer/view 자체는 그대로.
            if (v.playerLayer.player !== player) {
                v.playerLayer.player = player
            }
        },
    )
}

/**
 * Multi-segment composition — split 결과 (같은 sourceUri 의 여러 세그먼트) 또는 다중 영상.
 *
 * SingleItem 와 동일 패턴:
 *  - hostView/AVPlayerLayer 영구. UIKitView factory 1회.
 *  - composition 빌드는 LaunchedEffect 에서 async (assets 의 lazy load 회피). 빌드 완료 시
 *    UIKitView.update 를 통해 hostView.playerLayer.player = newPlayer 로 swap.
 *  - playlistKey 변경마다 새 player 생성 — AVPlayer 속성 mutation 누적이 layer 블랭크 트리거.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun MultiSegmentVideoPlayer(
    items: List<VideoPlayerItem>,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (Long) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier,
) {
    val playlistKey = items.joinToString("|") {
        "${it.sourceUri}:${it.trimStartMs}:${it.trimEndMs}:${it.speedScale}"
    }

    val hostView = remember {
        AVPlayerHostView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
    }

    val onEndedState = rememberUpdatedState(onEnded)
    val onPositionChangedState = rememberUpdatedState(onPositionChanged)

    // Player 는 async-built composition 이 준비될 때까지 null. 빌드 완료 시 state 업데이트 →
    // UIKitView.update 가 layer.player 갈아끼움. tracks/duration 의 lazy load 를 await 해서
    // composition 이 0-길이로 만들어지는 결함 차단.
    var player by remember { mutableStateOf<AVPlayer?>(null) }

    LaunchedEffect(playlistKey) {
        val newPlayer = withContext(Dispatchers.Default) {
            buildCompositionPlayer(items)
        }
        // 새 player 오면 즉시 trim/volume 적용 + 이전 player 정리는 DisposableEffect 로.
        items.firstOrNull()?.let { newPlayer.volume = it.volumeScale.coerceIn(0f, 1f) }
        player = newPlayer
    }

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose {}
        val bgObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> p.pause() }
        )
        val endObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> onEndedState.value() }
        )
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(bgObserver)
            NSNotificationCenter.defaultCenter.removeObserver(endObserver)
            p.pause()
        }
    }

    LaunchedEffect(player, isPlaying) {
        val p = player ?: return@LaunchedEffect
        if (isPlaying) {
            p.rate = 1f
            p.play()
        } else {
            p.pause()
        }
    }

    LaunchedEffect(player, seekToMs) {
        val p = player ?: return@LaunchedEffect
        if (seekToMs == null) return@LaunchedEffect
        val curSec = CMTimeGetSeconds(p.currentTime())
        val targetSec = seekToMs / 1000.0
        if (!curSec.isNaN() && kotlin.math.abs(curSec - targetSec) <= 0.5) return@LaunchedEffect
        p.seekToTime(CMTimeMakeWithSeconds(targetSec, preferredTimescale = 1000))
    }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        var lastVolume = Float.NaN
        var lastReportedMs = -1L
        while (true) {
            val sec = CMTimeGetSeconds(p.currentTime())
            if (!sec.isNaN()) {
                val globalMs = (sec * 1000.0).toLong()
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
                        p.volume = curVol
                        lastVolume = curVol
                    }
                }
                if (globalMs != lastReportedMs) {
                    onPositionChangedState.value(globalMs)
                    lastReportedMs = globalMs
                }
            }
            delay(200)
        }
    }

    UIKitView(
        factory = { hostView },
        modifier = modifier,
        update = { v ->
            val p = player
            if (p != null && v.playerLayer.player !== p) {
                v.playerLayer.player = p
            }
        },
    )
}

/**
 * Async composition build — 모든 asset 의 tracks/duration 이 loaded 될 때까지 대기 후 합성.
 * suspendCancellableCoroutine 로 loadValuesAsynchronouslyForKeys 의 callback 을 await.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun buildCompositionPlayer(items: List<VideoPlayerItem>): AVPlayer {
    // Split 결과는 같은 sourceUri 를 N개 segment 가 공유 → sourceUri 별 dedupe 로 asset 1개씩만.
    val assetBySourceUri = items.map { it.sourceUri }.toSet().associateWith { uri ->
        AVURLAsset(
            uRL = toFileUrl(uri),
            options = mapOf<Any?, Any>(AVURLAssetPreferPreciseDurationAndTimingKey to true),
        )
    }
    coroutineScope {
        assetBySourceUri.values.map { asset ->
            async {
                suspendCancellableCoroutine<Unit> { cont ->
                    asset.loadValuesAsynchronouslyForKeys(listOf("tracks", "duration")) {
                        if (cont.isActive) cont.resume(Unit) {}
                    }
                }
            }
        }.awaitAll()
    }

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
        val asset = assetBySourceUri.getValue(item.sourceUri)
        @Suppress("UNCHECKED_CAST")
        val srcVideo = (asset.tracksWithMediaType(AVMediaTypeVideo) as? List<AVAssetTrack>)
            ?.firstOrNull()
        @Suppress("UNCHECKED_CAST")
        val srcAudio = (asset.tracksWithMediaType(AVMediaTypeAudio) as? List<AVAssetTrack>)
            ?.firstOrNull()

        val trimStart = CMTimeMakeWithSeconds(item.trimStartMs / 1000.0, preferredTimescale = 1000)
        val trimEnd = if (item.trimEndMs > 0L) {
            CMTimeMakeWithSeconds(item.trimEndMs / 1000.0, preferredTimescale = 1000)
        } else {
            asset.duration
        }
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
        }

        if (item.speedScale != 1f && item.speedScale > 0f && !sourceDurSec.isNaN()) {
            val newDurSec = sourceDurSec / item.speedScale.toDouble()
            val newDur = CMTimeMakeWithSeconds(newDurSec, preferredTimescale = 1000)
            val insertedRange = CMTimeRangeMake(start = insertAt, duration = sourceDur)
            videoTrack?.scaleTimeRange(insertedRange, toDuration = newDur)
            audioTrack?.scaleTimeRange(insertedRange, toDuration = newDur)
        }
    }

    return AVPlayer(playerItem = AVPlayerItem(asset = composition))
}
