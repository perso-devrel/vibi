package com.vibi.cmp.platform

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File

/** seekTo 직후 이 시간(ms) 동안 위치 폴링 보고를 봉인 — ExoPlayer 가 seek 를 적용하기 전
 *  currentPosition 이 pre-seek 값을 잠깐 보고해 ViewModel state 를 stale 로 덮는 race 차단.
 *  iOS SEEK_GUARD_MS 동등. */
private const val SEEK_GUARD_MS = 500L

/** 목표 seekToMs 가 현재 글로벌 위치와 이 거리(ms) 이내면 실제 seekTo 를 생략 — 폴링→ViewModel
 *  state→seekToMs 미세 변동이 매 tick seekTo 를 유발해 디코더 버퍼가 flush 되며 "제자리 버벅"
 *  으로 보이는 self-echo 루프 차단. iOS 의 0.5s abs 가드 동등. */
private const val SEEK_TOLERANCE_MS = 500L

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

    // 모든 segment 의 sourceUri 파일이 실제 존재하는지 사전 검사. file:// 로컬 경로 중 하나라도
    // 누락 시 흰/검정 화면 대신 placeholder 노출 — container/디렉터리 이동으로 옛 절대경로가
    // 끊긴 케이스 (iOS VideoSourceMissingPlaceholder 와 동등). sourceUri 들이 바뀔 때만
    // 재검사해서 recomposition 마다 stat 호출 누적을 피한다.
    val sourceUris = items.map { it.sourceUri }
    val anyMissing = remember(sourceUris) {
        sourceUris.any {
            it.startsWith("file://") && !File(Uri.parse(it).path ?: it.removePrefix("file://")).exists()
        }
    }
    if (anyMissing) {
        VideoSourceMissingPlaceholder(modifier)
        return
    }

    // playlist 시그니처 변경 시 player 재생성. 동일 playlist 내 trim/speed/volume 만 변경되면
    // 같은 player 인스턴스 유지하고 setMediaItems 재호출.
    val playlistKey = remember(items) { items.joinToString("|") { it.sourceUri + ":" + it.trimStartMs + ":" + it.trimEndMs } }
    val exoPlayer = remember(playlistKey) {
        ExoPlayer.Builder(context)
            // 시스템 audio focus 핸들링 — 다른 앱 재생 시 양보하고, 전화/타이머 interruption 시
            // ExoPlayer 가 자동 pause. iOS AVAudioSession interruption 처리와 동등.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // 이어폰/블루투스 분리 (becoming noisy) 시 자동 pause — 스피커 폭발 방지.
            // iOS AVAudioSessionRouteChange(OldDeviceUnavailable) 처리와 동등.
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setMediaItems(items.map { it.toMediaItem() })
                prepare()
            }
    }

    // seekTo 직후 폴링 보고를 잠시 봉인하는 가드 타임스탬프 (elapsedRealtime 기준). iOS 동등.
    var seekGuardUntilMs by remember(exoPlayer) { mutableStateOf(0L) }

    DisposableEffect(exoPlayer) {
        // segment 전환 시 per-item speed/volume 재적용.
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyPerItemPlayback(exoPlayer, items)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // STATE_ENDED 직후 polling 이 currentPosition(=endMs) 을 재보고해서 ViewModel
                    // state 가 0 → endMs 로 덮이는 race 차단. player 자체를 0 으로 되돌려 currentPosition
                    // 이 0 이 된 뒤에 onEnded 호출.
                    exoPlayer.seekTo(0, 0L)
                    onEnded()
                }
            }
        }
        exoPlayer.addListener(listener)
        applyPerItemPlayback(exoPlayer, items) // 초기 첫 item 적용.
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 앱이 백그라운드로 전환되면 (ON_STOP) 재생 정지. iOS UIApplicationWillResignActive 시
    // pause 와 동등. 사용자 명시 액션 존중 — ON_START 에서 자동 resume 은 하지 않는다.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    // volume 변경 (예: 음원 분리 directive 진입 시 원본 mute) 을 즉시 반영.
    // playlistKey 에 volume 미포함이라 player rebuild 없이 mutation 만 적용 — 현재 재생 중인
    // item index 기준으로 volume 만 갈아끼움. media item transition 콜백 (applyPerItemPlayback)
    // 으로는 단일 segment 중간의 volume 변경을 못 잡는 결함 회피.
    val volumeKey = remember(items) { items.joinToString("|") { it.volumeScale.toString() } }
    LaunchedEffect(exoPlayer, volumeKey) {
        val idx = exoPlayer.currentMediaItemIndex
        items.getOrNull(idx)?.let { exoPlayer.volume = it.volumeScale.coerceIn(0f, 1f) }
    }

    // global seekToMs 를 (item index, item-local ms) 로 변환해서 seek.
    LaunchedEffect(seekToMs) {
        if (seekToMs == null) return@LaunchedEffect
        // self-echo 가드: 현재 글로벌 위치와 목표가 SEEK_TOLERANCE_MS 이내면 실제 시킹 skip.
        // 폴링(onPositionChanged)→ViewModel state→seekToMs 미세 변동이 매 tick seekTo 를
        // 유발해 디코더가 계속 flush → "제자리 버벅" 되는 것 차단. 현재 위치 산정은 아래
        // 폴링 루프와 동일 공식 (앞 item 누적 dur + clip-local currentPosition).
        val curGlobalMs = run {
            var acc = 0L
            for (i in 0 until exoPlayer.currentMediaItemIndex) {
                val item = items.getOrNull(i) ?: break
                acc += if (item.trimEndMs > 0L) item.trimEndMs - item.trimStartMs else 0L
            }
            acc + exoPlayer.currentPosition
        }
        if (kotlin.math.abs(seekToMs - curGlobalMs) <= SEEK_TOLERANCE_MS) return@LaunchedEffect

        var acc = 0L
        for ((idx, item) in items.withIndex()) {
            val itemDur = (item.trimEndMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
                .let { it - item.trimStartMs }.coerceAtLeast(0L)
            val itemEnd = if (itemDur == Long.MAX_VALUE) Long.MAX_VALUE else acc + itemDur
            if (seekToMs < itemEnd) {
                val localMs = (seekToMs - acc).coerceAtLeast(0L)
                // seek 적용 완료 전 폴링이 pre-seek 위치를 재보고하는 race 봉인.
                seekGuardUntilMs = SystemClock.elapsedRealtime() + SEEK_GUARD_MS
                exoPlayer.seekTo(idx, localMs)
                return@LaunchedEffect
            }
            acc = itemEnd
        }
    }

    // 글로벌 위치 산정 — 현재 item 의 누적 시작 + currentPosition.
    LaunchedEffect(exoPlayer) {
        var lastReportedMs = -1L
        while (true) {
            // seek 진행 중이면 폴링 보고 skip — pre-seek currentPosition 을 재보고해 seekToMs
            // 를 도로 되돌려 self-echo 를 재점화하는 것 차단. 짧은 sleep 으로 가드 만료 폴.
            if (SystemClock.elapsedRealtime() < seekGuardUntilMs) {
                delay(50)
                continue
            }
            var acc = 0L
            for (i in 0 until exoPlayer.currentMediaItemIndex) {
                val item = items.getOrNull(i) ?: break
                val dur = if (item.trimEndMs > 0L) item.trimEndMs - item.trimStartMs else 0L
                acc += dur
            }
            // 같은 ms 중복 emit 차단 — idle 시 recomposition 부담 제거 (iOS lastReportedMs 가드 동등).
            val globalMs = acc + exoPlayer.currentPosition
            if (globalMs != lastReportedMs) {
                onPositionChanged(globalMs)
                lastReportedMs = globalMs
            }
            // 30fps 폴링 — 200ms 면 짧은 영상에서 파형/오디오와 playhead 가 화면 폭의
            // 5% 이상 어긋나 사용자가 싱크 mismatch 로 인지.
            delay(33)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
        // factory 는 1회만 실행되므로 playlistKey 로 exoPlayer 가 rebuild 되면 PlayerView 가
        // released 된 옛 player 를 참조한 채로 남는다. update 에서 현재 인스턴스로 갱신.
        update = { view -> if (view.player !== exoPlayer) view.player = exoPlayer },
        onRelease = { view -> view.player = null },
    )
}

/**
 * Source file 부재 시 fallback. ExoPlayer 를 끊긴 file:// 로 attach 하면 빈 화면이 그대로 노출돼
 * 사용자가 "앱 깨짐" 으로 인지함. 명시적 안내 문구로 대체 (iOS VideoSourceMissingPlaceholder 동등).
 */
@Composable
private fun VideoSourceMissingPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "영상 파일을 찾을 수 없습니다",
            color = Color.White,
        )
    }
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
