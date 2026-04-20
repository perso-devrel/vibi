---
name: architecture
description: DubCast 아키텍처 상세. Clean Architecture 계층(ui/domain/data), 네비게이션, DI 모듈, 핵심 설계 결정(FFmpeg, ASS 자막, 오디오 믹싱, Undo/Redo) 관련 작업 시 참조.
user_invocable: true
trigger: 아키텍처
---

# DubCast 아키텍처

단일 모듈 앱(`app/`), Clean Architecture + MVVM.

## 계층 구조

- **UI** (`ui/`): Compose 스크린 + ViewModel. 화면: Input, Timeline, Export, Share. 각 화면은 `StateFlow<UiState>` 노출.
- **Domain** (`domain/`): 순수 Kotlin use case / repository 인터페이스. 기능별 폴더: `input/`, `tts/`, `timeline/`, `subtitle/`, `lipsync/`, `export/`.
- **Data** (`data/`): Room DB (`local/db/`), Retrofit API (`remote/api/`), repository 구현 (`repository/`).

## 핵심 설계 결정

- 네비게이션: sealed class `Screen` 타입 세이프 라우트. Input → Timeline → Export → Share.
- `BFF_BASE_URL`: `local.properties` → BuildConfig 주입. 모든 빌드가 실제 BFF 서버에 연결 (mock 인터셉터 없음).
- Undo/Redo: 제네릭 `UndoRedoManager<T>` + ArrayDeque (최대 50 상태).
- 자막 렌더링: `.ass` (Advanced SubStation Alpha) 파일, Noto Sans KR 폰트, ffmpeg-kit (`io.github.maitrungduc1410:ffmpeg-kit-min`).
- 오디오 믹싱: ffmpeg `adelay` + `amix` 필터 체인으로 더빙 오버레이.
- Export 화면: original-only 또는 translation(더빙/립싱크/자동자막 + 언어 선택) 2가지 모드.

## Dependency Injection (Hilt)

`di/` 하위 3개 모듈:
- `DatabaseModule` — Room singleton + 마이그레이션 (v1→v2→v3→v4)
- `NetworkModule` — Retrofit + OkHttp + Moshi + logging
- `RepositoryModule` — repository 인터페이스 ↔ 구현 바인딩
