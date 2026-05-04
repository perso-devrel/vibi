package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import com.dubcast.shared.domain.model.BgmClip

// TODO: Android impl — MediaPlayer per clip + sync. iOS 우선 구현.
@Composable
actual fun BgmPlaybackSync(
    clips: List<BgmClip>,
    isPlaying: Boolean,
    currentMs: Long,
) {
}
