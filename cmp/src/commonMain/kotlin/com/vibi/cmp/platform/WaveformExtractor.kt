package com.vibi.cmp.platform

/**
 * 오디오 파일에서 N 개의 peak 값(0..1) 을 추출. WaveformPlayBar 의 막대 높이로 사용.
 *
 * 입력은 local file path (`/Users/...`, `file:///...`) 또는 BFF stem URL — 후자는 호출자가 미리
 * download 해서 local path 로 변환해야 한다 (AudioPreviewer/StemMixer 가 cache 에 두는 임시 경로
 * 그대로 활용 가능). remote URL 을 직접 넣으면 plat 마다 동작 다를 수 있어 미지원.
 *
 * @param samples 반환할 peak 개수. UI 의 막대 수와 같게.
 * @return 0..1 normalized peak. 추출 실패/지원 안 됨 → 빈 list.
 *
 * iOS: AVAudioFile + AVAudioPCMBuffer (processingFormat = Float32 PCM) 에서 채널0의 절대값 max 를
 *   bucket 단위로 계산. 음원분리 stem 같은 짧은 파일(<몇 MB) 가정 — 전체 frames 메모리 로드.
 * Android: 미구현 (no-op) — 사용자 요청에 따라 iOS 우선.
 */
expect suspend fun extractAudioPeaks(localPath: String, samples: Int = 120): List<Float>
