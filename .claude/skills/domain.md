---
name: domain
description: DubCast 도메인 모델 레퍼런스. EditProject, DubClip, SubtitleClip, Voice, SubtitlePosition/Anchor 등을 다룰 때 참조.
user_invocable: true
trigger: 도메인
---

# DubCast 도메인 모델

- `EditProject` — 비디오 프로젝트 + 메타데이터 (URI, 해상도, 길이)
- `DubClip` — 타임라인 더빙 오디오 클립 (text, voice, audio file, position, duration)
- `SubtitleClip` — 자막 (text, 시간 범위, position)
- `Voice` — TTS 음성 (id, name, language)
- `SubtitlePosition` / `Anchor` — 자막 배치 (TOP/MIDDLE/BOTTOM + y-offset)
