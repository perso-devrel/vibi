# CLAUDE.md — `:cmp` 모듈

## Project

vibi `:cmp` — **Compose Multiplatform UI 모듈**. 타임라인 에디터, 더빙 클립 UI, 미리보기 등 Android/iOS 공통 화면을 단일 `@Composable` 코드로 작성. 비즈니스 로직(도메인·리포지토리)은 형제 모듈 `:shared` 에서 가져온다.

- **gradle 모듈명**: `:cmp` (gradle 루트는 워크스페이스 루트, `include(":cmp")` 로 자동 인식)
- **현 상태**: 아직 `libs.plugins.android.application` (Android-only) 기반. Compose Multiplatform 플러그인 전환 + iOS 타깃 추가는 향후 작업.
- **의존**: `implementation(project(":shared"))`
- **DI**: Koin (Hilt 가 아님 — KMP 호환을 위해)

> 워크스페이스 지도와 형제 모듈 관계는 루트 `CLAUDE.md` 참조.

## 빌드

```bash
# 워크스페이스 루트에서
./gradlew :cmp:assembleDebug --no-configuration-cache
```

## 현재 구조

```
cmp/
├── build.gradle.kts                   # android.application + compose; project(":shared") 의존
└── src/
    ├── androidMain/
    │   ├── AndroidManifest.xml
    │   ├── res/values/themes.xml
    │   └── kotlin/com/dubcast/android/
    │       ├── DubCastApplication.kt
    │       └── MainActivity.kt
    ├── commonMain/                    # (현재 비어있음 — CMP 전환 후 활성)
    └── iosMain/                       # (현재 비어있음 — iOS 타깃 추가 후 활성)
```

`com.dubcast.*` 패키지명은 앱 이름 변경(`DubCast → vibi`) 이전부터 사용. 폴더는 `vibi-mobile/cmp` 로 통일됐지만 코드 패키지는 `com.dubcast.cmp`/`com.dubcast.android` 로 남아있다 — 패키지 마이그레이션은 별도 작업 (iOS framework 이름 + Xcode 통합 영향).

## 중요 제약

- UI(`@Composable`) 만. 도메인·리포지토리·네트워크는 `:shared` 에 두고 본 모듈에서 import.
- Hilt/Retrofit 등 JVM-only 라이브러리 추가 금지 (CMP 전환 시 iOS 컴파일 깨짐). Koin/Ktor Client/Coil multiplatform 사용.
- BFF 호출은 `:shared` 의 리포지토리 인터페이스를 통해서만. 본 모듈에서 직접 HTTP 호출 금지.

## Compose 규약

UI 코드 작성 시 따른다. 어겨도 컴파일은 되지만 recomposition / 상태 관리에서 사고 남.

- **ViewModel 에서 `mutableStateOf` 쓰지 말 것** — `StateFlow` 가 표준. KMP 호환·테스트 용이성·Compose 비의존 ViewModel 원칙. 단일 화면 Android-only 라도 `cmp` 에서는 `StateFlow` 통일.
- **State 속성에 sealed class 남발 금지** — top-level `UiState.Loading / Success(data) / Error` 는 OK. `Success` 내부 필드 부분 업데이트 시 `(state as Success).copy(...)` 캐스팅 지옥 방지를 위해 **success 내부 데이터는 일반 data class** 로 분리해서 `.copy()` 자유롭게.
- **`@Composable` 파라미터 람다는 안정적이지 않음** — 매 recomposition 마다 새 인스턴스로 인식돼 자식 skip 깨짐. 해결책 우선순위:
  1. Compose Compiler 1.5+ (Kotlin 2.0+) 의 strong skipping mode 활성 — 대부분 자동 처리
  2. 메서드 레퍼런스: `onClick = viewModel::increment`
  3. `remember { { ... } }` 또는 `rememberUpdatedState`
- **Detekt + `compose-rules` (slack) 룰셋** — recomposition 사고(불안정 파라미터·람다 캡처)는 사람 눈으로 잡기 어려움. Compose Compiler Metrics 도 함께 활성.

## Skills

별도 스킬 없음. 빌드/configuration-cache 관련은 `shared/.claude/skills/build.md` 참조.

## 다음 단계 (정보 제공)

- Timeline 화면 본격 구현 (현재 골격 + 4 sheet placeholder. 후속: BottomSheet × 6, 비디오 프리뷰 + 플레이헤드, undo/redo)
- iosApp 시뮬레이터·실기기 검증 (PHPicker / PHPhotoLibrary 동작)
- iOS export 전략 결정 — AVFoundation 자체 합성 vs BFF `/api/v2/render` 위임 확대
