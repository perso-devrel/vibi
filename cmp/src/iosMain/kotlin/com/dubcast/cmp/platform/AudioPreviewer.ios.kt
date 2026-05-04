package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.rate
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.volume
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * 미리듣기 player.
 *
 * Local file → AVAudioPlayer (synchronous prepareToPlay, deterministic).
 * Remote URL → AVPlayer (streaming).
 *
 * 자연 종료 감지: AVAudioPlayer 의 delegate (audioPlayerDidFinishPlaying) 가 onComplete 호출.
 * stop() 은 delegate 를 미리 떼서 finish 콜백 발화 막음.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberAudioPreviewer(): AudioPreviewerHandle {
    val streamingPlayer = remember { AVPlayer() }
    // delegate / 현재 player 는 클로저 캡처용 holder. K/N 에서 NSObject 는 weak ref 만으로 GC.
    var delegateRef: NSObject? = null
    var localPlayer: AVAudioPlayer? = null
    DisposableEffect(streamingPlayer) {
        onDispose {
            streamingPlayer.pause()
            localPlayer?.delegate = null
            localPlayer?.stop()
        }
    }
    return remember(streamingPlayer) {
        object : AudioPreviewerHandle {
            override fun play(url: String, volume: Float, rate: Float, onComplete: () -> Unit) {
                runCatching {
                    val session = AVAudioSession.sharedInstance()
                    session.setCategory(AVAudioSessionCategoryPlayback, null)
                    session.setActive(true, null)
                }
                val clampedVol = volume.coerceIn(0f, 1f)
                val clampedRate = rate.coerceIn(0.5f, 2.0f)

                val isRemote = url.startsWith("http://") || url.startsWith("https://")
                if (isRemote) {
                    val nsUrl = NSURL.URLWithString(url) ?: return
                    val item = AVPlayerItem(uRL = nsUrl)
                    streamingPlayer.replaceCurrentItemWithPlayerItem(item)
                    streamingPlayer.volume = clampedVol
                    streamingPlayer.rate = clampedRate
                    streamingPlayer.play()
                    return
                }

                // shared/CLAUDE.md:78 NSURL 절대 경로 패턴.
                val nsUrl = if (url.startsWith("file://")) {
                    NSURL.URLWithString(url) ?: NSURL.fileURLWithPath(url.removePrefix("file://"))
                } else {
                    NSURL.fileURLWithPath(url)
                }
                localPlayer?.let {
                    it.delegate = null
                    it.stop()
                }
                val player = runCatching {
                    AVAudioPlayer(contentsOfURL = nsUrl, error = null)
                }.getOrNull() ?: return
                player.enableRate = true
                player.volume = clampedVol
                player.rate = clampedRate
                val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
                    override fun audioPlayerDidFinishPlaying(
                        player: AVAudioPlayer,
                        successfully: Boolean,
                    ) {
                        onComplete()
                    }
                }
                delegateRef = delegate
                player.delegate = delegate
                player.prepareToPlay()
                player.play()
                localPlayer = player
            }

            override fun stop() {
                streamingPlayer.pause()
                streamingPlayer.replaceCurrentItemWithPlayerItem(null)
                localPlayer?.let {
                    // finish 콜백이 manual stop 에서 발화 안 하게 delegate 먼저 떼기.
                    it.delegate = null
                    it.stop()
                }
                localPlayer = null
                delegateRef = null
            }
        }
    }
}
