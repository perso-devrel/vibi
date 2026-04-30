package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
actual fun rememberAudioPreviewer(): AudioPreviewerHandle {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return remember(player) {
        object : AudioPreviewerHandle {
            override fun play(url: String) {
                player.stop()
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }

            override fun stop() {
                player.stop()
            }
        }
    }
}
