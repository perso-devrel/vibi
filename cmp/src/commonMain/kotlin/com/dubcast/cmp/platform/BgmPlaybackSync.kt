package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import com.dubcast.shared.domain.model.BgmClip

/**
 * 타임라인 video 재생 중 BgmClip 들을 video 재생 상태에 sync — 각 클립의 startMs 부터 시작해
 * volumeScale/speedScale 적용. video pause/seek 시 같이 멈추거나 재정렬.
 *
 * 영상 미리보기 옆에 항상 composed. clips/isPlaying/currentMs 변경에 반응하는 부수효과만.
 */
@Composable
expect fun BgmPlaybackSync(
    clips: List<BgmClip>,
    isPlaying: Boolean,
    currentMs: Long,
)
