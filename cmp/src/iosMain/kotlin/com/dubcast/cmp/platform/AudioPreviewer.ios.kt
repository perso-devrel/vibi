package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberAudioPreviewer(): AudioPreviewerHandle {
    val player = remember { AVPlayer() }
    DisposableEffect(player) {
        onDispose { player.pause() }
    }
    return remember(player) {
        object : AudioPreviewerHandle {
            override fun play(url: String) {
                val nsUrl = NSURL.URLWithString(url) ?: NSURL.fileURLWithPath(url)
                val item = AVPlayerItem(uRL = nsUrl)
                player.replaceCurrentItemWithPlayerItem(item)
                player.play()
            }

            override fun stop() {
                player.pause()
                player.replaceCurrentItemWithPlayerItem(null)
            }
        }
    }
}
