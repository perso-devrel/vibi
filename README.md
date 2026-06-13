# vibi

영상에서 원하지 않는 소리만 골라 제거하는 모바일 AI 앱.
**Kotlin Multiplatform** + **Compose Multiplatform** 으로 Android · iOS 단일 코드 베이스.

> 폴더·docs·코드 패키지(`com.vibi.*`)·클래스 (`VibiApplication`, `VibiDatabase`, `VibiTheme`, `VibiNavHost`) 모두 `vibi` 통일.

## 의존 백엔드

이 앱은 **vibi-bff** (Kotlin/Ktor) 를 통해 음원 분리 · 렌더 · 소셜 로그인 (Google + Apple) 을 호출한다. 모든 외부 API (Perso · Google tokeninfo · Apple JWKS) 는 BFF 에서만 다루며, 클라이언트는 `/api/v2` 만 사용한다. 단 Google/Apple **native SDK 의 ID Token** 만 클라이언트가 받아 BFF 에 교환한다.

빌드 전에 BFF 가 동작 중이어야 한다 (기본 `:8080`).

> 자막 / 더빙 / lipsync 는 BFF surface (BFF commit `52f8d7c`) 와 모바일 코드 양쪽에서 모두 제거됨. 음원 분리·BGM·세그먼트 편집만 유지.

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

### 2) iOS 프로젝트 생성 (iOS 빌드 시 1회 또는 `project.yml` 변경 시마다)

```bash
cd iosApp
xcodegen generate
```

`iosApp/iosApp.xcodeproj` 가 생성된다. XcodeGen 은:
- `com.apple.developer.applesignin` entitlement (`iosApp.entitlements`) 를 single-source 로 관리.
- GoogleSignIn SPM (`https://github.com/google/GoogleSignIn-iOS`, from 8.0.0) 을 SPM 으로 추가.

### 3) BFF 환경 변수

BFF 가 사용하는 `GOOGLE_OAUTH_CLIENT_IDS` 에 모바일이 보낼 ID Token 의 `aud` (iOS / Android / Web client id) 가 포함돼 있어야 한다. Apple Sign In 활성화 시 `APPLE_OAUTH_CLIENT_IDS = com.vibi.ios` 도. 자세한 표는 `vibi-bff/README.md` 참조.

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
│   ├── commonMain/com/vibi/shared/
│   │   ├── domain/{model,repository,usecase,util}/
│   │   ├── data/{remote,repository,local,local/db}/    # Ktor Client + Room v14 (destructive migration)
│   │   ├── ui/{auth,input,timeline,account,share}/     # ViewModel
│   │   ├── platform/                                   # expect — GoogleSignInClient, AppleSignInClient, FileSystem, ...
│   │   └── di/                                         # Koin 모듈
│   ├── androidMain/  └── iosMain/                      # 플랫폼별 actual (인증 / 미디어 / DB / HttpClient)
│   └── (commonTest)
├── cmp/                                    # :cmp — Compose Multiplatform UI (Android+iOS)
│   ├── commonMain/com/vibi/cmp/
│   │   ├── App.kt + ui/navigation/VibiNavHost.kt       # Splash · Login · Input · Timeline
│   │   ├── theme/                                      # VibiTheme · Typography · Radius · Spacing
│   │   ├── ui/{splash,auth,input,timeline,share,account,components,cupertino}/
│   │   ├── ui/timeline/sounddeck/                      # SoundDeck · SoundCard · ABPreviewBar · ...
│   │   └── platform/                                   # VideoPlayer / MediaPicker / Audio* / StemMixer / Waveform expect
│   ├── androidMain/  └── iosMain/                      # Media3 / AVPlayer · PHPicker · GoogleSignIn 등 actual
└── iosApp/                                 # XcodeGen project.yml + Swift entry + iosApp.entitlements
                                            # (GoogleSignIn SPM, applesignin entitlement)
```

핵심 화면 흐름: **Splash → (signedIn) Input ↔ Timeline / (signedOut) Login → Input**.

타임라인 sheet 군: AudioSeparation · AudioInsert · BgmTrim. 계정 sheet: UserMenu · CreditPurchase.
타임라인 구간 선택은 별도 sheet 가 아니라 `UnifiedTimelineBar` 인라인 (28~56dp 바, segment/directive content strip + range fill + bracket 핸들 + 재생 marker).

## 핵심 기능 ↔ BFF 매핑

| 기능 | 클라이언트 | BFF 엔드포인트 |
|---|---|---|
| 소셜 로그인 (Google + Apple) | LoginScreen · AuthRepository · `GoogleSignInClient` / `AppleSignInClient` (expect) | `POST /api/v2/auth/google` · `POST /api/v2/auth/apple` · `DELETE /api/v2/auth/account` |
| 크레딧 / 인앱결제 | UserMenuSheet · CreditPurchaseSheet · `UserMenuViewModel` · `IapBridge` / `PurchaseLauncher` | `GET /api/v2/credits` · `GET /api/v2/credits/cost` · `POST /api/v2/credits/purchase` |
| 영상 업로드 | InputScreen + MediaPicker (PickVisualMedia / PHPicker) · VideoThumbnailExtractor | (로컬 segment) |
| 음원 분리 (영상 segment + BGM clip) | AudioSeparationSheet · `StartAudioSeparationUseCase` / `PollSeparationUseCase` · 로컬 StemMixer | `POST /api/v2/separate` → poll → `GET /api/v2/separate/{id}/stem/{stemId}` (로컬 합성 프리뷰) |
| 구간 선택 | UnifiedTimelineBar (인라인) | (로컬 Room) |
| 음원 삽입 (파일 + 즉시 녹음 + BGM trim) | AudioInsertSheet · `AudioPicker` / `AudioRecorder` · BgmTrimSheet | (로컬 BgmClip) |
| 익스포트 | TimelineScreen 저장 · `HybridRenderExecutor` · `SaveExportUseCase` · `PrewarmAssetUploadUseCase` → ShareScreen | `POST /api/v2/assets/upload-url` → `PUT` (R2) → `POST /api/v2/render/v3` → poll → `GET /api/v2/render/{id}/download` |

## 외부 의존 (BFF 경유, 클라이언트가 직접 호출하지 않음)

- **Perso AI** — 음원 분리
- **Google tokeninfo / Apple JWKS** — ID Token 검증 (모바일은 native SDK 가 ID Token 만 받아 BFF 에 전달)

API 키는 모두 BFF env 에만 보관.

## 모듈별 상세

- [`shared/CLAUDE.md`](./shared/CLAUDE.md) — `:shared` 의 KMP 제약, 도메인/데이터 레이어 규약, Timeline stepper 동작 규약, Auth 흐름, 알려진 iOS 버그 패턴
- [`cmp/CLAUDE.md`](./cmp/CLAUDE.md) — `:cmp` 의 Compose 규약 (StateFlow, sealed UiState, 람다 안정성), 화면 구조
- [`shared/.claude/skills/build.md`](./shared/.claude/skills/build.md) — 빌드 명령 + configuration-cache 주의
- [`shared/.claude/skills/ios-kn-patterns.md`](./shared/.claude/skills/ios-kn-patterns.md) — iOS K/N 패턴

## 트러블슈팅

- **`commonMain` 컴파일 에러: `java.io.File`, `java.util.UUID`, `System.currentTimeMillis`** —
  iOS 에서 안 됨. multiplatform 대안 (`kotlinx.io`, `kotlin.uuid.Uuid`, `kotlinx.datetime.Clock`) 사용 또는 `expect/actual`.
- **iOS framework 링크 실패** — `:shared:linkDebugFrameworkIosSimulatorArm64` 직접 실행해서 에러 확인.
- **iOS 시뮬레이터에서 영상 안 보임 / 메타데이터 unreadable** — PHPicker 가 반환하는 absolute path 처리 이슈. 자세한 패턴은 `shared/.claude/skills/ios-kn-patterns.md` 참조.
- **Android `BFF_BASE_URL` 에 localhost 썼더니 ConnectException** — Android 에뮬레이터는 host 의 localhost 가 `10.0.2.2`. 실기기는 LAN IP 필요.
- **iOS Google Sign In 동작 안 함** — `project.yml` 에 GoogleSignIn SPM 이 추가됐는지, `Info.plist` 의 `CFBundleURLTypes` 에 reversed client id URL scheme 이 있는지 확인. SPM 변경 후 `xcodegen generate`.
- **iOS Apple Sign In 1000 에러** — Personal Team 빌드는 Sign In with Apple capability 사용 불가. 유료 Apple Developer Program 가입 필요. App ID Capabilities 에서 활성화 + `iosApp.entitlements` 의 `com.apple.developer.applesignin` 확인.
- **`./gradlew` 실행 권한 없음** — `chmod +x ./gradlew`.
