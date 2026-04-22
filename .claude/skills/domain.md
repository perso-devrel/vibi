---
name: domain
description: DubCast 도메인 모델 레퍼런스. EditProject, Segment, DubClip, SubtitleClip, ImageClip, TextOverlay, BgmClip, Stem, Voice 등을 다룰 때 참조.
user_invocable: true
trigger: 도메인
---

# DubCast 도메인 모델

- `EditProject` — 프로젝트 루트 (프레임 크기, 배경색, 타임스탬프)
- `Segment` — 타임라인의 영상/이미지 세그먼트 단위. `SegmentType = VIDEO | IMAGE`. trim/split/range 편집, `volumeScale`/`speedScale` 필드
- `DubClip` — 타임라인 더빙 오디오 클립 (text, voice, audio file path, startMs, durationMs, volume)
- `SubtitleClip` — 자막 (text, 시간 범위, `SubtitlePosition`). `sourceDubClipId != null` 이면 자동 자막
- `ImageClip` — 영상 위 스티커 이미지 (imageUri, 시간 범위, xPct/yPct/widthPct/heightPct, lane)
- `TextOverlay` — 텍스트 오버레이 (text, fontFamily/fontSizeSp/colorHex, 시간 범위, xPct/yPct, lane)
- `BgmClip` — 배경음 클립 (sourceUri, sourceDurationMs, startMs, volumeScale 0~2f)
- `Stem` — 오디오 분리 결과 stem (`StemKind = BACKGROUND | VOICE_ALL | SPEAKER`)
- `Voice` — TTS 음성 (voiceId, name, previewUrl, language)
- `SubtitlePosition` / `Anchor` — 자막 배치 (TOP/MIDDLE/BOTTOM + yOffsetPct)
- `VideoInfo` / `ImageInfo` / `AudioInfo` — 메타데이터 추출 결과 (입력 유효성 검사용)
- `ValidationResult` — 입력 검증 결과 (`Valid` / `Invalid(ValidationError)`)
