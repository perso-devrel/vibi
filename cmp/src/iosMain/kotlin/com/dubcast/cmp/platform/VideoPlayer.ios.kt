package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObjectProtocol

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    uri: String,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (Long) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier
) {
    fun toFileUrl(s: String): NSURL = if (s.startsWith("file://"))
        NSURL.URLWithString(s) ?: NSURL.fileURLWithPath(s.removePrefix("file://"))
    else NSURL.fileURLWithPath(s)

    // 단일 player. 미리보기 언어 swap 은 commonMain 에서 uri 자체를 BFF mux 한 mp4 로 바꿔 호출.
    val player = remember(uri) {
        AVPlayer(uRL = toFileUrl(uri))
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

    DisposableEffect(player) {
        val bgObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ -> player.pause() }
        )
        // 영상 끝 도달 → 처음으로 seek (다음 play 즉시 0초부터) + onEnded callback.
        val endObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = null,
            usingBlock = { _ ->
                player.seekToTime(CMTimeMakeWithSeconds(0.0, preferredTimescale = 1000))
                onEnded()
            }
        )
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(bgObserver)
            NSNotificationCenter.defaultCenter.removeObserver(endObserver)
            player.pause()
            controller.player = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) player.play() else player.pause()
    }

    LaunchedEffect(seekToMs) {
        if (seekToMs != null) {
            val currentSec = CMTimeGetSeconds(player.currentTime())
            val targetSec = seekToMs / 1000.0
            if (!currentSec.isNaN() && kotlin.math.abs(currentSec - targetSec) > 0.5) {
                player.seekToTime(CMTimeMakeWithSeconds(targetSec, preferredTimescale = 1000))
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val sec = CMTimeGetSeconds(player.currentTime())
            if (!sec.isNaN()) onPositionChanged((sec * 1000.0).toLong())
            delay(200)
        }
    }

    UIKitViewController(
        factory = { controller },
        modifier = modifier
    )
}
