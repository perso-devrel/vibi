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
@Composable
expect fun VideoPlayer(
    uri: String,
    isPlaying: Boolean = true,
    seekToMs: Long? = null,
    onPositionChanged: (Long) -> Unit = {},
    onEnded: () -> Unit = {},
    modifier: Modifier = Modifier
)
