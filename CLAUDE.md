# CLAUDE.md

## Project

DubCast — Android 비디오 더빙/로컬라이제이션 앱. 타임라인 에디터에서 AI 더빙 클립 추가, on-device FFmpeg으로 더빙/립싱크/자막 합친 영상 내보내기. BFF 서버가 TTS/립싱크/음성 API 제공.

- Kotlin + Jetpack Compose (XML 레이아웃 없음)
- 단일 모듈(`app/`), Clean Architecture(domain/data/ui) + MVVM
- TDD: 실패 테스트 → 최소 구현 → 리팩터링. 새 테스트는 기존 use case/ViewModel 테스트 스타일을 먼저 확인 후 동일하게 작성.
- README.md에 한국어 전체 제품 스펙

## 중요 제약

- BFF 연결: `BFF_BASE_URL`은 `local.properties` → BuildConfig로 주입. **mock 인터셉터 없음** — 모든 빌드가 실제 BFF를 호출하므로 `local.properties`에 실행 중인 BFF URL 필수.
- BFF 서버 코드(TTS/립싱크/음성 API 구현)는 별도 레포: https://github.com/perso-devrel/dubcast-bff.git 참조. 로컬 경로 아님.

## Skills (on-demand 참조)

상세 컨텍스트는 `.claude/skills/` 에 주제별로 분리:
- `build` — 빌드/테스트 명령어, SDK 타겟
- `architecture` — 계층 구조, DI 모듈, 핵심 설계 결정(FFmpeg, ASS 자막, 오디오 믹싱, Undo/Redo)
- `test-patterns` — 테스트 작성 레퍼런스(MockK/Turbine/Fake)
- `api` — BFF v2 엔드포인트
- `domain` — 도메인 모델
- `test` — 유닛 테스트 실행 스킬 (API 비용 관리)
- `review` — 코드 리뷰 스킬
