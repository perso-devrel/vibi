---
name: api-domain
description: DubCast BFF v2 API 엔드포인트 및 도메인 모델 레퍼런스. TTS/립싱크/음성 API 호출 또는 EditProject/DubClip/SubtitleClip 등 도메인 모델을 다룰 때 참조.
user_invocable: true
trigger: api
---

# DubCast API / Domain 레퍼런스

## BFF v2 엔드포인트

- `GET  /api/v2/voices` — 음성 목록
- `POST /api/v2/tts` — TTS 합성
- `POST /api/v2/lipsync` — 립싱크 요청 (multipart)
- `GET  /api/v2/lipsync/{jobId}/status` — 립싱크 상태 폴링
- `GET  /api/v2/lipsync/{jobId}/download` — 립싱크 결과 다운로드

## 도메인 모델

- `EditProject` — 비디오 프로젝트 + 메타데이터 (URI, 해상도, 길이)
- `DubClip` — 타임라인 더빙 오디오 클립 (text, voice, audio file, position, duration)
- `SubtitleClip` — 자막 (text, 시간 범위, position)
- `Voice` — TTS 음성 (id, name, language)
- `SubtitlePosition` / `Anchor` — 자막 배치 (TOP/MIDDLE/BOTTOM + y-offset)
