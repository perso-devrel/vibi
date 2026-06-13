# CLAUDE.md — `:cmp` 모듈

## Project

vibi `:cmp` — **Compose Multiplatform UI 모듈**. 타임라인 에디터, 음원 분리/조절 UI, 음원 삽입, 미리보기, 로그인, 계정/크레딧(인앱결제) 등 Android/iOS 공통 화면을 단일 `@Composable` 코드로 작성. 비즈니스 로직(도메인·리포지토리)은 형제 모듈 `:shared` 에서 가져온다.

- **gradle 모듈명**: `:cmp` (gradle 루트는 워크스페이스 루트, `include(":cmp")` 로 자동 인식)
- **현 상태**: Compose Multiplatform 활성. 세 source set 모두 사용 (commonMain UI + androidMain/iosMain 플랫폼 actual). Android entry 는 `VibiApplication` + `MainActivity`, iOS entry 는 `MainViewController` (UIKit) — `iosApp/` Swift 가 호출.
- **의존**: `implementation(project(":shared"))`
- **DI**: Koin (Hilt 가 아님 — KMP 호환을 위해)
- **코드 패키지**: `com.vibi.cmp.*` 통일

> 워크스페이스 지도와 형제 모듈 관계는 루트 `CLAUDE.md` 참조.

## 빌드

```bash
# Android APK
./gradlew :cmp:assembleDebug --no-configuration-cache

# iOS framework (실제 앱 빌드는 Xcode 에서 iosApp 실행)
./gradlew :shared:compileKotlinIosSimulatorArm64 --no-configuration-cache
```

## 현재 구조

```
cmp/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/vibi/cmp/
    │   ├── App.kt + ui/navigation/VibiNavHost.kt   # sealed Screen: Splash / Login / Input / Timeline
    │   ├── theme/                                  # VibiTheme · VibiTypography · VibiRadius · VibiSpacing
    │   ├── ui/splash/SplashScreen.kt               # 자동 로그인 시도 + Login/Input 분기
    │   ├── ui/auth/LoginScreen.kt                  # Google / Apple 버튼 (Apple 은 iOS 에서만 활성)
    │   ├── ui/input/InputScreen.kt                 # 영상 선택 + 최근 프로젝트
    │   ├── ui/timeline/
    │   │   ├── TimelineScreen.kt                   # 메인 에디터 + UnifiedTimelineBar (인라인 구간 선택)
    │   │   ├── AudioSeparationSheet.kt             # Setup→Processing→PickStems→Mixing→Done
    │   │   ├── AudioInsertSheet.kt                 # 음원 삽입 (파일 선택 / 즉시 녹음 모드 + 프리뷰)
    │   │   ├── BgmTrimSheet.kt                     # 영상보다 긴 BGM 의 sub-range 선택
    │   │   ├── DetailEditPanel.kt                  # segment/clip 디테일 (볼륨/속도)
    │   │   ├── WaveformPlayBar.kt                  # 영상 audio 파형 + 분리 구간 accent 표시
    │   │   ├── CustomColorPickerDialog.kt
    │   │   └── sounddeck/                          # SoundDeck · SoundCard · ABPreviewBar ·
    │   │                                           # AddSourceCard · EditEntryCard · IconLabelCard ·
    │   │                                           # SoundCardModel · EditActionsPanel
    │   ├── ui/share/ShareScreen.kt
    │   ├── ui/account/                             # UserMenuSheet (프로필 + 크레딧 잔액) · CreditPurchaseSheet (인앱결제)
    │   ├── ui/components/VibiCards.kt
    │   ├── ui/cupertino/Cupertino.kt               # iOS-look 위젯 (스위치 등)
    │   └── platform/                               # VideoPlayer / MediaPicker / AudioPicker /
    │                                               # AudioRecorder / AudioPreviewer /
    │                                               # WaveformExtractor / StemMixer /
    │                                               # BgmPlaybackSync / RuntimeFlags expect
    ├── androidMain/kotlin/com/vibi/cmp/
    │   ├── VibiApplication.kt + MainActivity.kt
    │   ├── platform/                               # Media3 VideoPlayer, PickVisualMedia MediaPicker,
    │                                               # Android AudioPicker/AudioRecorder, StemMixer,
    │                                               # WaveformExtractor, BgmPlaybackSync, AudioPreviewer
    │   └── ui/cupertino/Cupertino.android.kt
    └── iosMain/kotlin/com/vibi/cmp/
        ├── MainViewController.kt                   # UIKit entry → ComposeUIViewController
        ├── platform/                               # AVPlayer VideoPlayer, PHPicker MediaPicker,
        │                                            # iOS AudioPicker/AudioRecorder, AVAudioEngine StemMixer,
        │                                            # IosAudioCache, IosPickerSupport,
        │                                            # WaveformExtractor, BgmPlaybackSync, AudioPreviewer
        └── ui/cupertino/Cupertino.ios.kt
```

화면 흐름: **Splash → (signedIn?) Input ↔ Timeline / (signedOut) Login → Input**. 타임라인 sheet 군은 `TimelineScreen` 안에서 `ModalBottomSheet` 로 호출.

## 중요 제약

- UI(`@Composable`) 만. 도메인·리포지토리·네트워크는 `:shared` 에 두고 본 모듈에서 import.
- iOS 컴파일 깨짐 방지: JVM-only 라이브러리(Hilt/Retrofit/OkHttp 등) 추가 금지. Koin/Ktor Client/Coil 3 multiplatform 사용.
- BFF 호출은 `:shared` 의 리포지토리 인터페이스를 통해서만. 본 모듈에서 직접 HTTP 호출 금지.
- 플랫폼 의존 (player, picker, recorder, mixer, waveform 등) 은 모두 `expect/actual`. 새 플랫폼 의존 추가 시 `commonMain` 의 `platform/` 에 `expect` 정의 → `androidMain`/`iosMain` 에서 actual.

## Compose 규약

UI 코드 작성 시 따른다. 어겨도 컴파일은 되지만 recomposition / 상태 관리에서 사고 남.

- **ViewModel 에서 `mutableStateOf` 쓰지 말 것** — `StateFlow` 가 표준 (KMP 호환·테스트 용이성·Compose 비의존 ViewModel 원칙).
- **State 속성에 sealed class 남발 금지** — top-level `UiState.Loading / Success(data) / Error` 는 OK. `Success` 내부 부분 업데이트는 캐스팅 지옥 방지를 위해 일반 data class 로 분리해서 `.copy()` 자유롭게.
- **`@Composable` 파라미터 람다 안정성** — 매 recomposition 마다 새 인스턴스로 인식돼 자식 skip 깨짐. 우선순위:
  1. Compose Compiler 1.5+ (Kotlin 2.0+) strong skipping mode (대부분 자동)
  2. 메서드 레퍼런스: `onClick = viewModel::increment`
  3. `remember { { ... } }` 또는 `rememberUpdatedState`
- **iOS Compose 진입** — `ComposeUIViewController { App() }` 패턴. AVPlayer / PHPicker 등 UIKit-side 위젯 임베드는 `UIKitView` / `UIKitViewController` 로. 가시성 lifecycle 처리는 `shared/.claude/skills/ios-kn-patterns.md` 참고.

## Skills

별도 스킬 없음. 빌드/configuration-cache 관련은 `shared/.claude/skills/build.md`, iOS K/N 패턴은 `shared/.claude/skills/ios-kn-patterns.md` 참조.
