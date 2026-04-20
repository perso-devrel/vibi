# CLAUDE.md

## Project

DubCast — Android 비디오 더빙/로컬라이제이션 앱. 타임라인 에디터에서 AI 더빙 클립 추가, on-device FFmpeg으로 더빙/립싱크/자막 합친 영상 내보내기. BFF 서버가 TTS/립싱크/음성 API 제공.

- Kotlin + Jetpack Compose (XML 레이아웃 없음)
- 단일 모듈(`app/`), Clean Architecture(domain/data/ui) + MVVM
- TDD: 실패 테스트 → 최소 구현 → 리팩터링
- README.md에 한국어 전체 제품 스펙

## Skills (on-demand 참조)

상세 컨텍스트는 `.claude/skills/` 에 주제별로 분리:
- `build` — 빌드/테스트 명령어, SDK 타겟
- `architecture` — 계층 구조, DI 모듈, 핵심 설계 결정(FFmpeg, ASS 자막, 오디오 믹싱, Undo/Redo)
- `testing` — MockK/Turbine/Fake 사용 패턴
- `api-domain` — BFF v2 엔드포인트, 도메인 모델
- `test` — 유닛 테스트 실행 스킬 (API 비용 관리)
- `review` — 코드 리뷰 스킬

BFF 서버 코드는 이 리포 범위 밖.
