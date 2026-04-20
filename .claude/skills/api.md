---
name: api
description: DubCast BFF v2 API 엔드포인트 레퍼런스. TTS/립싱크/음성 API 호출 코드를 작성/수정할 때 참조.
user_invocable: true
trigger: API
---

# DubCast BFF v2 엔드포인트

- `GET  /api/v2/voices` — 음성 목록
- `POST /api/v2/tts` — TTS 합성
- `POST /api/v2/lipsync` — 립싱크 요청 (multipart)
- `GET  /api/v2/lipsync/{jobId}/status` — 립싱크 상태 폴링
- `GET  /api/v2/lipsync/{jobId}/download` — 립싱크 결과 다운로드

`BFF_BASE_URL`은 `local.properties` → BuildConfig로 주입. mock 인터셉터 없이 항상 실제 BFF 서버에 연결.
