package com.vibi.cmp.platform

/**
 * Android: 미구현 — 사용자 요청에 따라 iOS 우선. WaveformPlayBar 는 빈 list 면 stub 으로 그리거나
 * 표시 자체를 스킵. 추후 MediaCodec 디코더 + 채널0 sample max bucket 추출로 구현.
 */
actual suspend fun extractAudioPeaks(localPath: String, samples: Int): List<Float> = emptyList()
