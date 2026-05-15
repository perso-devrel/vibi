package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

@Composable
actual fun rememberAudioPreviewer(): AudioPreviewerHandle {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    // 파형 PlayBar 가 구독할 reactive state. iOS 와 동일 인터페이스.
    val progress = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return remember(player) {
        object : AudioPreviewerHandle {
            private var pendingComplete: (() -> Unit)? = null
            private val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        duration.longValue = player.duration.coerceAtLeast(0L)
                    }
                    if (state == Player.STATE_ENDED) {
                        progress.longValue = 0L
                        pendingComplete?.invoke()
                        pendingComplete = null
                    }
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    val now = newPosition.positionMs.coerceAtLeast(0L)
                    if (progress.longValue != now) progress.longValue = now
                }
            }

            init {
                player.addListener(listener)
            }

            override val progressMs: State<Long> get() = progress
            override val durationMs: State<Long> get() = duration

            override fun play(url: String, volume: Float, rate: Float, onComplete: () -> Unit) {
                player.stop()
                pendingComplete = onComplete
                player.volume = volume.coerceIn(0f, 1f)
                player.playbackParameters = PlaybackParameters(rate.coerceIn(0.5f, 2.0f))
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                progress.longValue = 0L
                player.playWhenReady = true
            }

            override fun stop() {
                pendingComplete = null
                player.stop()
                progress.longValue = 0L
                duration.longValue = 0L
            }

            override fun seekTo(ms: Long) {
                val total = duration.longValue
                val target = if (total > 0L) ms.coerceIn(0L, total) else ms.coerceAtLeast(0L)
                player.seekTo(target)
                progress.longValue = target
            }
        }
    }
}
