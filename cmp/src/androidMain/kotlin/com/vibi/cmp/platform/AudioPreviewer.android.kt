package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
actual fun rememberAudioPreviewer(): AudioPreviewerHandle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    // 파형 PlayBar 가 구독할 reactive state. iOS 와 동일 인터페이스.
    val progress = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return remember(player, scope) {
        object : AudioPreviewerHandle {
            private var pendingComplete: (() -> Unit)? = null
            // 재생 중 위치 폴링 job — play 마다 새로 시작, stop/finish 시 cancel.
            // onPositionDiscontinuity 는 seek/transition 에만 발화하므로 정상 재생 중에는
            // 이것이 없으면 progress 가 0 에 멈춰 WaveformPlayBar playhead 가 안 움직인다.
            private var pollJob: Job? = null
            private val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        duration.longValue = player.duration.coerceAtLeast(0L)
                    }
                    if (state == Player.STATE_ENDED) {
                        cancelPoll()
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

            private fun cancelPoll() {
                pollJob?.cancel()
                pollJob = null
            }

            override fun play(url: String, volume: Float, rate: Float, onComplete: () -> Unit) {
                player.stop()
                pendingComplete = onComplete
                player.volume = volume.coerceIn(0f, 1f)
                player.playbackParameters = PlaybackParameters(rate.coerceIn(0.5f, 2.0f))
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                progress.longValue = 0L
                player.playWhenReady = true
                // 50ms 폴링 — iOS AudioPreviewer 와 동일 해상도. STATE_ENDED / stop 에서 cancel.
                cancelPoll()
                pollJob = scope.launch {
                    while (isActive) {
                        val now = player.currentPosition.coerceAtLeast(0L)
                        if (progress.longValue != now) progress.longValue = now
                        delay(50)
                    }
                }
            }

            override fun stop() {
                cancelPoll()
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
