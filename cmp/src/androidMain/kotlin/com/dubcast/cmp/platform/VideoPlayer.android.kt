package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
actual fun VideoPlayer(
    items: List<VideoPlayerItem>,
    isPlaying: Boolean,
    seekToMs: Long?,
    onPositionChanged: (Long) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    // playlist 시그니처 변경 시 player 재생성. 동일 playlist 내 trim/speed/volume 만 변경되면
    // 같은 player 인스턴스 유지하고 setMediaItems 재호출.
    val playlistKey = items.joinToString("|") { it.sourceUri + ":" + it.trimStartMs + ":" + it.trimEndMs }
    val exoPlayer = remember(playlistKey) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(items.map { it.toMediaItem() })
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        // segment 전환 시 per-item speed/volume 재적용.
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyPerItemPlayback(exoPlayer, items)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        exoPlayer.addListener(listener)
        applyPerItemPlayback(exoPlayer, items) // 초기 첫 item 적용.
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    // global seekToMs 를 (item index, item-local ms) 로 변환해서 seek.
    LaunchedEffect(seekToMs) {
        if (seekToMs == null) return@LaunchedEffect
        var acc = 0L
        for ((idx, item) in items.withIndex()) {
            val itemDur = (item.trimEndMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
                .let { it - item.trimStartMs }.coerceAtLeast(0L)
            val itemEnd = if (itemDur == Long.MAX_VALUE) Long.MAX_VALUE else acc + itemDur
            if (seekToMs < itemEnd) {
                val localMs = (seekToMs - acc).coerceAtLeast(0L)
                exoPlayer.seekTo(idx, localMs)
                return@LaunchedEffect
            }
            acc = itemEnd
        }
    }

    // 글로벌 위치 산정 — 현재 item 의 누적 시작 + currentPosition.
    LaunchedEffect(exoPlayer) {
        while (true) {
            var acc = 0L
            for (i in 0 until exoPlayer.currentMediaItemIndex) {
                val item = items.getOrNull(i) ?: break
                val dur = if (item.trimEndMs > 0L) item.trimEndMs - item.trimStartMs else 0L
                acc += dur
            }
            onPositionChanged(acc + exoPlayer.currentPosition)
            delay(200)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        }
    )
}

private fun VideoPlayerItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder().setUri(sourceUri)
    val clip = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(trimStartMs.coerceAtLeast(0L))
    if (trimEndMs > 0L) clip.setEndPositionMs(trimEndMs)
    builder.setClippingConfiguration(clip.build())
    return builder.build()
}

private fun applyPerItemPlayback(player: ExoPlayer, items: List<VideoPlayerItem>) {
    val idx = player.currentMediaItemIndex
    val item = items.getOrNull(idx) ?: return
    val safeSpeed = item.speedScale.coerceIn(0.25f, 4f)
    player.playbackParameters = PlaybackParameters(safeSpeed)
    player.volume = item.volumeScale.coerceIn(0f, 1f)
}
