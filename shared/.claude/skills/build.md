---
name: build
description: vibi KMP 빌드/테스트 명령. configuration-cache 와 --no-configuration-cache 플래그 사용 기준 포함.
user_invocable: true
trigger: 빌드
---

# KMP Build Skill

`./gradlew` (`vibi-mobile/` 루트) 로 실행. `gradle.properties` 에 `org.gradle.configuration-cache=true` 가 켜져 있어 일부 작업은 `--no-configuration-cache` 플래그가 필요하다.

## 전체 빌드

```bash
./gradlew :shared:build :cmp:assembleDebug --no-configuration-cache
```

## shared 모듈만

```bash
# Kotlin metadata (IDE/리팩토링용)
./gradlew :shared:compileKotlinMetadata --no-configuration-cache

# commonMain 메타데이터 컴파일
./gradlew :shared:compileCommonMainKotlinMetadata --no-configuration-cache

# 전체 :shared 빌드
./gradlew :shared:build --no-configuration-cache
```

## :cmp 모듈 (Compose UI; 현재 Android 디버그 APK)

```bash
./gradlew :cmp:assembleDebug --no-configuration-cache
```

## iOS framework

```bash
# 시뮬레이터 (Apple Silicon)
./gradlew :shared:compileKotlinIosSimulatorArm64 --no-configuration-cache

# Xcode 통합 (iosApp/ 의 preBuildScripts 가 자동 호출)
./gradlew :shared:embedAndSignAppleFrameworkForXcode --no-configuration-cache
./gradlew :cmp:embedAndSignAppleFrameworkForXcode --no-configuration-cache
```

## 테스트

현재 `commonTest`/`androidUnitTest` 에 테스트 없음. 추가 시:

```bash
./gradlew :shared:commonTest
./gradlew :shared:testDebugUnitTest
./gradlew :shared:allTests --no-configuration-cache
```

## 사전 준비물

- `local.properties` 의 `sdk.dir` 가 유효한 Android SDK 경로 (현재 `/Users/jepark/Library/Android/sdk/` 가 기준)
- Gradle wrapper 는 `vibi-mobile/` 루트의 `./gradlew`. 실행 권한 없으면 `chmod +x ./gradlew`

## 자주 걸리는 이슈

- **configuration-cache 오류**: 위 플래그(`--no-configuration-cache`) 를 빼먹으면 특정 태스크에서 실패. 안전하게 항상 플래그 포함.
- **KMP 메타데이터 불일치**: `:shared:compileCommonMainKotlinMetadata` 실패 시 `./gradlew clean --no-configuration-cache` 후 재시도.
- **Android SDK 경로**: `local.properties` 가 없거나 경로가 틀리면 `:cmp` 구성 실패. `sdk.dir=/Users/jepark/Library/Android/sdk` 형태로 작성.

## 참고 — 실행 환경

- JVM args: `-Xmx4096M -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError`
- Gradle parallel/caching/configuration-cache 모두 on
- `kotlin.mpp.enableCInteropCommonization=true`
