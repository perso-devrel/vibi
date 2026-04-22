---
name: test-patterns
description: DubCast 테스트 작성 패턴 레퍼런스. Turbine, MainDispatcherRule, Fake 리포지토리 사용법. 유닛 테스트 또는 ViewModel 테스트를 작성/수정할 때 참조.
user_invocable: true
trigger: 테스트패턴
---

# DubCast 테스트 작성 패턴

> 테스트 *실행* 은 `test` 스킬 사용. 이 문서는 테스트 *작성* 레퍼런스.

## 도구

- **Test doubles**: 순수 Kotlin Fake 선호. 단일 사용처면 테스트 안에 익명 `object : Interface` 로 인라인. MockK 등 모킹 라이브러리 사용하지 않음 (의존성에서 제거됨)
- **Flow 테스트**: Turbine
- **Coroutines**: `MainDispatcherRule` (`test/util/`)로 `Dispatchers.Main` 교체

## Fake 리포지토리

`fake/` 패키지에 사전 구현된 Fake들:
- `FakeSegmentRepository`
- `FakeEditProjectRepository`
- `FakeDubClipRepository`
- `FakeSubtitleClipRepository`
- `FakeImageClipRepository`
- `FakeTextOverlayRepository`
- `FakeBgmClipRepository`
- `FakeTtsRepository`
- `FakeLipSyncRepository`
- `FakeAudioSeparationRepository`
- `FakeVideoMetadataExtractor`
- `FakeImageMetadataExtractor`
- `FakeAudioMetadataExtractor`
- `FakeFfmpegExecutor`

## 작성 가이드

- 테스트 대상(use case / ViewModel)의 기존 테스트 패턴을 먼저 확인 후 동일한 스타일로 작성
- TDD: 실패 테스트 작성 → 최소 구현 → 리팩터링
- **위임만 검증하는 테스트는 작성하지 말 것** (예: 1-line `repo.delete(id)` 호출을 assert만 하는 테스트). 실제 로직(clamping, validation, state machine, 조합)을 검증
- API 통합 테스트는 `ApiTest` 마커 인터페이스 구현 (기본 실행에서 제외됨)
- domain 인터페이스가 Android framework 타입(`android.net.Uri` 등)을 노출하면 안 됨 — 테스트가 어려워지고 계층 경계가 무너짐
