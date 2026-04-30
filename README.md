# vibi

영상 하나로 AI 더빙 + 자동 자막을 만들어 다국어 버전을 한 번에 내보내는 모바일 앱.
**Kotlin Multiplatform** + **Compose Multiplatform** 으로 Android · iOS 단일 코드 베이스.

> 폴더·docs 는 `vibi` 로 통일됐지만 코드 패키지 (`com.dubcast.*`), Room `DubCastDatabase`,
> iOS framework 이름 등 일부 식별자는 legacy `dubcast` 명을 유지한다 — 별도 마이그레이션 예정.

## 의존 백엔드

이 앱은 **[vibi-bff](https://github.com/perso-devrel/vibi-bff)** (Kotlin/Ktor) 를 통해
TTS · 자막 · 자동 더빙 · 음성 분리 · 렌더 파이프라인을 호출한다.
모든 외부 API (ElevenLabs · Perso AI · Gemini) 는 BFF 에서만 다루며, 클라이언트는 `/api/v2` 만 사용.

빌드 전에 BFF 가 동작 중이어야 한다 (기본 `:8080`).

## 사전 준비

| 도구 | 용도 | 비고 |
|---|---|---|
| **JDK 21** | gradle 실행 | `java -version` 으로 확인 |
| **Android SDK** | Android 빌드 | Android Studio 설치 또는 cmdline-tools |
| **Xcode 15+** | iOS 빌드 | App Store 또는 developer.apple.com |
| **XcodeGen** | iOS 프로젝트 생성 | `brew install xcodegen` |
| **vibi-bff** | API 백엔드 | 별도 repo, `:8080` 에서 실행 중이어야 함 |

## 셋업

### 1) `local.properties` 작성

저장소 루트에 `local.properties` 를 만들고 (gitignore 됨):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
BFF_BASE_URL=http://10.0.2.2:8080/
```

`BFF_BASE_URL` 은 **실행 타깃에 맞는 주소** 를 써야 한다 — mock 인터셉터 없음:

| 타깃 | 주소 |
|---|---|
| Android 에뮬레이터 | `http://10.0.2.2:8080/` |
| Android 실기기 | `http://192.168.x.x:8080/` (맥 LAN IP) |
| iOS 시뮬레이터 | `http://localhost:8080/` 또는 맥 LAN IP |
| iOS 실기기 | 맥 LAN IP |

### 2) iOS 프로젝트 생성 (iOS 빌드 시 1회)

```bash
cd iosApp
xcodegen generate
```

`iosApp/iosApp.xcodeproj` 가 생성된다.

## 빌드 & 실행

### Android

```bash
# shared 모듈 + Android APK
./gradlew :shared:build :cmp:assembleDebug --no-configuration-cache

# APK: cmp/build/outputs/apk/debug/cmp-debug.apk
```

`--no-configuration-cache` 는 일부 KMP 태스크 호환을 위해 필수
(자세한 사항은 [`shared/.claude/skills/build.md`](./shared/.claude/skills/build.md)).

### iOS

```bash
# 1. KMP framework 컴파일 (Apple Silicon 시뮬레이터)
./gradlew :shared:compileKotlinIosSimulatorArm64 --no-configuration-cache

# 2. Xcode 에서 열기 → 시뮬레이터 실행
open iosApp/iosApp.xcodeproj
```

`iosApp/` 의 preBuildScripts 가 `:shared:embedAndSignAppleFrameworkForXcode` 를 자동 호출하므로
별도 수동 framework 임베드 불필요.

### 단위 테스트

```bash
./gradlew :shared:testDebugUnitTest --no-configuration-cache
./gradlew :shared:allTests --no-configuration-cache
```

## 모듈 구조

```
vibi-mobile/
├── shared/                                 # :shared — KMP 비즈니스 로직
│   ├── commonMain/com/dubcast/shared/
│   │   ├── domain/{model,repository,usecase}/
│   │   ├── data/{remote,repository,local/db}/    # Ktor Client + Room v19
│   │   ├── ui/{input,timeline,export,share}/     # ViewModel
│   │   └── di/                                   # Koin 모듈
│   ├── androidMain/  └── iosMain/                # 플랫폼별 actual
│   └── (commonTest 13개 + 마이그레이션 테스트)
├── cmp/                                    # :cmp — Compose Multiplatform UI
│   ├── commonMain/com/dubcast/cmp/
│   │   ├── App.kt + ui/navigation/         # sealed-class NavHost
│   │   ├── ui/{input,timeline,export,share}/
│   │   └── platform/                       # VideoPlayer / MediaPicker expect
│   ├── androidMain/  └── iosMain/          # Media3 / AVPlayer · PHPicker
└── iosApp/                                 # XcodeGen project.yml + Swift entry
```

핵심 화면: Input → Timeline (편집·sheet 군) → Export → Share.
시트: InsertDubbing · InsertSubtitle · AudioSeparation · RangeSelection · RegenerateSubtitles · DetailEdit.

## 6 핵심 기능 ↔ BFF 매핑

| 기능 | 클라이언트 | BFF 엔드포인트 |
|---|---|---|
| 영상 업로드 | InputScreen + MediaPicker | (로컬 segment) |
| TTS | InsertDubbingSheet | `POST /api/v2/tts` |
| 자막 (수동 + 자동) | InsertSubtitleSheet · GenerateAutoSubtitlesUseCase | `POST /api/v2/subtitles` (+ poll) |
| 자동 더빙 | GenerateAutoDubUseCase | `POST /api/v2/autodub` (+ poll) |
| 음성 분리 | AudioSeparationSheet | `POST /api/v2/separate` (+ stem mix) |
| 구간 선택 | RangeSelectionSheet | (로컬 Room) |

## 외부 의존 (BFF 경유, 클라이언트가 직접 호출하지 않음)

- **ElevenLabs** — TTS, 보이스
- **Perso AI** — 음성 분리, STT, 번역, 자동 더빙
- **Gemini** — 추가 번역

API 키는 모두 BFF env 에만 보관.

## 모듈별 상세

- [`shared/CLAUDE.md`](./shared/CLAUDE.md) — `:shared` 의 KMP 제약, 도메인/데이터 레이어 규약, 알려진 iOS 버그 패턴
- [`cmp/CLAUDE.md`](./cmp/CLAUDE.md) — `:cmp` 의 Compose 규약 (StateFlow, sealed UiState, 람다 안정성)
- [`shared/.claude/skills/build.md`](./shared/.claude/skills/build.md) — 빌드 명령 + configuration-cache 주의

## 트러블슈팅

- **`commonMain` 컴파일 에러: `java.io.File`, `java.util.UUID`, `System.currentTimeMillis`** —
  iOS 에서 안 됨. multiplatform 대안 (`kotlinx.io`, `kotlin.uuid.Uuid`, `kotlinx.datetime.Clock`) 사용 또는 `expect/actual`.
- **iOS framework 링크 실패** — `:shared:linkDebugFrameworkIosSimulatorArm64` 직접 실행해서 에러 확인.
- **iOS 시뮬레이터에서 영상 안 보임 / 메타데이터 unreadable** — PHPicker 가 반환하는 absolute path 처리 이슈.
  자세한 패턴은 `shared/CLAUDE.md` 의 "Known iOS bug patterns" 참조.
- **Android `BFF_BASE_URL` 에 localhost 썼더니 ConnectException** — Android 에뮬레이터는 host 의 localhost 가
  `10.0.2.2`. 실기기는 LAN IP 필요.
- **`./gradlew` 실행 권한 없음** — `chmod +x ./gradlew`.
