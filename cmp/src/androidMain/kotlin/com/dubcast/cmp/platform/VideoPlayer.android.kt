package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
actual fun VideoPlayer(
    uri: String,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (Long) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // 외부 isPlaying 동기화
    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    // 외부 시킹
    LaunchedEffect(seekToMs) {
        if (seekToMs != null) {
            // 5ms 이상 차이날 때만 시킹 (자체 진행 중이면 진동 방지)
            val current = exoPlayer.currentPosition
            if (kotlin.math.abs(current - seekToMs) > 5) {
                exoPlayer.seekTo(seekToMs)
            }
        }
    }

    // 약 200ms 간격으로 위치 콜백
    LaunchedEffect(exoPlayer) {
        while (true) {
            onPositionChanged(exoPlayer.currentPosition)
            delay(200)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        }
    )
}
