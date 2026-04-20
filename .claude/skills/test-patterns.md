---
name: test-patterns
description: DubCast 테스트 작성 패턴 레퍼런스. MockK, Turbine, MainDispatcherRule, Fake 리포지토리 사용법. 유닛 테스트 또는 ViewModel 테스트를 작성/수정할 때 참조.
user_invocable: true
trigger: 테스트패턴
---

# DubCast 테스트 작성 패턴

> 테스트 *실행* 은 `test` 스킬 사용. 이 문서는 테스트 *작성* 레퍼런스.

## 도구

- **Mocking**: MockK
- **Flow 테스트**: Turbine
- **Coroutines**: `MainDispatcherRule` (`test/util/`)로 `Dispatchers.Main` 교체

## Fake 리포지토리

`fake/` 패키지에 사전 구현된 Fake들:
- `FakeDubClipRepository`
- `FakeSubtitleClipRepository`
- `FakeEditProjectRepository`
- `FakeTtsRepository`
- `FakeLipSyncRepository`
- `FakeGallerySaver`
- `FakeFfmpegExecutor`
- `FakeVideoMetadataExtractor`

## 작성 가이드

- 테스트 대상(use case / ViewModel)의 기존 테스트 패턴을 먼저 확인 후 동일한 스타일로 작성
- TDD: 실패 테스트 작성 → 최소 구현 → 리팩터링
- API 통합 테스트는 `@Category(ApiTest::class)` 부착 (기본 실행에서 제외됨)
