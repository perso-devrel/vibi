package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose Multiplatform 비디오 플레이어 — Timeline 화면의 프리뷰 + 플레이헤드 양방향 동기화.
 *
 * @param uri 비디오 file URL 또는 content URI
 * @param isPlaying 외부에서 재생/정지 제어 (ViewModel.isPlaying)
 * @param seekToMs 외부에서 특정 시각으로 시킹 (변경될 때마다 적용; null 이면 시킹 명령 없음)
 * @param onPositionChanged 약 200ms 간격으로 현재 재생 위치(ms) 콜백
 */
/**
 * 한 segment 의 재생 명세 — uri, trim, 속도/볼륨 per-item 적용. Compose 가 recompose 시 [items]
 * 리스트가 바뀌면 player playlist 도 재구성된다.
 */
data class VideoPlayerItem(
    val sourceUri: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,    // 0 = 끝까지
    val speedScale: Float = 1f,
    val volumeScale: Float = 1f,
)

/**
 * Compose Multiplatform 비디오 플레이어 — 단일/다중 segment playlist 지원.
 *
 * Android: ExoPlayer + setMediaItems + ClippingConfiguration. onMediaItemTransition 시 per-item
 * speed/volume 갱신.
 * iOS: AVQueuePlayer + AVPlayerItem.forwardPlaybackEndTime. currentItem 변화 시 rate/volume 갱신.
 */
@Composable
expect fun VideoPlayer(
    items: List<VideoPlayerItem>,
    isPlaying: Boolean = true,
    seekToMs: Long? = null,
    onPositionChanged: (Long) -> Unit = {},
    onEnded: () -> Unit = {},
    modifier: Modifier = Modifier
)
