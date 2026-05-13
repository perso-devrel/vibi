---
name: build
description: vibi KMP 빌드/테스트 명령. configuration-cache 와 --no-configuration-cache 플래그 사용 기준 포함.
user_invocable: true
trigger: 빌드
---

# KMP Build Skill

`./gradlew` (`vibi-mobile/` 루트) 로 실행. `gradle.properties` 에 `org.gradle.configuration-cache=true` 가 켜져 있어 **기본적으로 cache 를 활용**한다. 일반 빌드(`:cmp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`) 는 플래그 없이 작동하며, hot incremental 은 1~2초 수준이다.

## 전체 빌드

```bash
./gradlew :shared:build :cmp:assembleDebug
```

## shared 모듈만

```bash
# commonMain 메타데이터 컴파일
./gradlew :shared:compileCommonMainKotlinMetadata

# 전체 :shared 빌드
./gradlew :shared:build
```

## :cmp 모듈 (Compose UI; 현재 Android 디버그 APK)

```bash
./gradlew :cmp:assembleDebug
```

## iOS framework

```bash
# 시뮬레이터 (Apple Silicon)
./gradlew :shared:compileKotlinIosSimulatorArm64

# Xcode 통합 (iosApp/ 의 preBuildScripts 가 자동 호출)
./gradlew :shared:embedAndSignAppleFrameworkForXcode
./gradlew :cmp:embedAndSignAppleFrameworkForXcode
```

## 테스트

현재 `commonTest`/`androidUnitTest` 에 테스트 없음. 추가 시:

```bash
./gradlew :shared:commonTest
./gradlew :shared:testDebugUnitTest
./gradlew :shared:allTests
```

## 사전 준비물

- `local.properties` 의 `sdk.dir` 가 유효한 Android SDK 경로 (현재 `/Users/jepark/Library/Android/sdk/` 가 기준)
- Gradle wrapper 는 `vibi-mobile/` 루트의 `./gradlew`. 실행 권한 없으면 `chmod +x ./gradlew`

## 자주 걸리는 이슈

- **configuration-cache 오류**: 일부 task (예: `--rerun-tasks` 또는 plugin 충돌 케이스) 에서 cache 가 실패할 수 있다. 그때만 `--no-configuration-cache` 추가. 평소 매번 붙이지 말 것 — 매 빌드마다 configuration 재구성으로 30s+ 비용.
- **KMP 메타데이터 불일치**: `:shared:compileCommonMainKotlinMetadata` 실패 시 `./gradlew clean` 후 재시도.
- **Android SDK 경로**: `local.properties` 가 없거나 경로가 틀리면 `:cmp` 구성 실패. `sdk.dir=/Users/jepark/Library/Android/sdk` 형태로 작성.
- **JVM args 변경 적용**: gradle.properties 의 `org.gradle.jvmargs` / `kotlin.daemon.jvmargs` 수정 후엔 `./gradlew --stop` 으로 daemon 재시작.

## 참고 — 실행 환경

- Gradle JVM heap: `-Xmx4608M -Xms1024M` + ParallelGC + Metaspace 1G
- Kotlin daemon: `-Xmx2560M` (Gradle JVM 과 분리)
- Gradle parallel / caching / configuration-cache 모두 on, workers.max=8
- `kotlin.parallel.tasks.in.project=true` (모듈 내 task 병렬)
- KSP incremental + intermodule on
- `kotlin.mpp.enableCInteropCommonization=true`
