---
name: build
description: DubCast 빌드 및 테스트 명령어 레퍼런스. Gradle 빌드, 유닛 테스트, API 통합 테스트, 단일 테스트 실행 시 참조.
user_invocable: true
trigger: 빌드
---

# DubCast 빌드/테스트 명령어

```bash
./gradlew assembleDebug                # Debug APK 빌드
./gradlew test                         # 유닛 테스트 (@ApiTest 제외)
./gradlew test -Pinclude.api.tests     # API 통합 테스트 포함 전체 실행
./gradlew testDebugUnitTest --tests "*.AssGeneratorTest"  # 단일 테스트 클래스
./gradlew connectedAndroidTest         # 계측 테스트 (device/emulator 필요)
```

API 통합 테스트(`BffApiIntegrationTest`)는 `ApiTest` 마커 인터페이스로 기본 제외됨 (`-Pinclude.api.tests` 플래그로 포함).

## SDK

- compileSdk/targetSdk: 36
- minSdk: 24
- Java 11 호환
