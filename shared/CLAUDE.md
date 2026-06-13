# CLAUDE.md — `:shared` 모듈

## Project

vibi `:shared` — **Kotlin Multiplatform 비즈니스 로직 모듈**. 도메인 모델 + 리포지토리(인터페이스+구현) + Ktor Client multiplatform BFF 클라이언트 + Room v14 (multiplatform, destructive migration 허용) + UseCase + ViewModel + Auth (GoogleSignIn / Apple AuthenticationServices) + 크레딧/IAP + Koin DI 를 `commonMain` 에 두고 `androidMain`/`iosMain` 으로 플랫폼 의존을 분리한다. UI 코드는 형제 모듈 `:cmp` 가 본 모듈을 소비한다.

- **gradle 모듈명**: `:shared` (gradle 루트는 `vibi-mobile/`, `include(":shared")`)
- 코드 스타일: official
- `org.gradle.configuration-cache=true` (일부 명령은 `--no-configuration-cache` 필요 — `.claude/skills/build.md` 참조)
- 코드 패키지는 `com.vibi.shared.*` 통일. Room DB 클래스명 `VibiDatabase`, iOS framework 이름도 `vibi`.

> 워크스페이스 지도와 형제 모듈(`:cmp`, `iosApp`) 관계는 워크스페이스 루트 `CLAUDE.md` 참조.

## BFF_BASE_URL 타깃별 주의

`vibi-mobile/local.properties` 의 `BFF_BASE_URL` 단일 값을 Android/iOS 양쪽에서 쓴다. 타깃별로 필요한 주소가 다르므로 네트워크 클라이언트 구현 시 `expect val bffBaseUrl: String` 로 `androidMain`/`iosMain` 에서 각각 다르게 주입하거나 빌드 플레이버로 분기.

- Android 에뮬레이터 → `http://10.0.2.2:8080/`
- Android 실기기 → 맥 LAN IP
- iOS 시뮬레이터 → `http://localhost:8080/` 또는 맥 LAN IP
- iOS 실기기 → 맥 LAN IP

## 현재 구조

```
shared/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/vibi/shared/
    │   ├── domain/
    │   │   ├── model/                       # Segment, Stem, EditProject, BgmClip, AuthUser,
    │   │   │                                # VideoInfo, ValidationResult, SeparationDirective,
    │   │   │                                # SeparationMediaType, DirectiveAnchor,
    │   │   │                                # SeparationCost, IapPlatform
    │   │   ├── repository/                  # 인터페이스 — AudioSeparation, BgmClip, EditProject,
    │   │   │                                # Segment, SeparationDirective
    │   │   ├── usecase/                     # 카테고리: input/separation/timeline/bgm/export/save/share/draft
    │   │   └── util/                        # LanePacking, ColorValidation, UndoRedoManager
    │   ├── data/
    │   │   ├── remote/api/BffApi.kt         # Ktor Client multiplatform.
    │   │   │                                # 현행: auth/google · auth/apple · auth/account(DELETE) ·
    │   │   │                                #      credits(+cost/purchase/admin-grant) ·
    │   │   │                                #      render(+inputs/v3) · assets/upload-url ·
    │   │   │                                #      separate · testdata.
    │   │   ├── remote/dto/                  # kotlinx.serialization DTO (Auth/Credit/Render/Separation)
    │   │   ├── remote/HttpClientFactory.kt  # expect/actual — Authorization 헤더 인젝션, Json 설정
    │   │   ├── repository/                  # 인터페이스 구현 (Room + BFF) + AuthRepository + RemoteRenderExecutor
    │   │   ├── local/db/                    # Room v14 — VibiDatabase, 4 entity + 4 DAO,
    │   │   │                                # fallbackToDestructiveMigration(dropAllTables=true)
    │   │   └── local/                       # AuthTokenStore · UserSession · JwtSubject · UserPreferencesStore (Multiplatform Settings)
    │   ├── ui/                              # ViewModel — InputVM, TimelineVM, LoginVM, UserMenuVM, ShareVM
    │   ├── platform/                        # expect — FileSystem, VideoThumbnailExtractor, TimeFormat,
    │   │                                    # GoogleSignInClient, AppleSignInClient
    │   └── di/                              # Koin 모듈 (DatabaseModule, NetworkModule, RepositoryModule, UseCaseModule, ViewModelModule)
    ├── androidMain/                         # AndroidVideoMetadataExtractor / AndroidGallerySaver /
    │                                        # AndroidAudio·ImageMetadataExtractor /
    │                                        # AndroidGoogleSignInClient (Credential Manager) /
    │                                        # AndroidAppleSignInClient (placeholder) /
    │                                        # AndroidExportPlatformAdapter / AndroidShareSheetLauncher /
    │                                        # MediaJobUploader / DatabaseBuilder.android /
    │                                        # HttpClientFactory.android / FileSystem.android /
    │                                        # androidPlatformModule
    ├── iosMain/                             # IosVideoMetadataExtractor / IosGallerySaver /
    │                                        # IosAudio·ImageMetadataExtractor /
    │                                        # IosGoogleSignInClient (GoogleSignIn SPM) /
    │                                        # IosAppleSignInClient (AuthenticationServices) /
    │                                        # IosExportPlatformAdapter / IosShareSheetLauncher /
    │                                        # IosMediaJobUploader / IosFilePathResolver /
    │                                        # DatabaseBuilder.ios / HttpClientFactory.ios /
    │                                        # FileSystem.ios / KoinHelper / iosPlatformModule
    └── commonTest/                          # BffApi · 직렬화 · Repository · Migration 등 unit test
```

## 중요 제약

- `commonMain` 에 **Android/JVM 전용 API 금지** (`android.*`, `java.io.File`, `java.util.UUID`, `System.currentTimeMillis`). 플랫폼 의존(파일시스템·오디오·메타데이터·갤러리·인증)은 `expect fun` 또는 platform adapter interface (예: `ExportPlatformAdapter`, `GoogleSignInClient`).
- Retrofit / Moshi / OkHttp / Hilt 는 iOS 불가 — 네트워크는 Ktor Client multiplatform, 직렬화는 kotlinx.serialization, DI 는 Koin, KV 저장은 Multiplatform Settings.
- 본 모듈은 **로직만**. UI 코드(`@Composable`)는 `cmp/` 로.
- 본 모듈이 모바일 도메인의 단일 source of truth — legacy-android 시절의 모델은 모두 흡수됐다.
- **Room v14 + destructive migration** — 시연 단계라 `fallbackToDestructiveMigration(dropAllTables=true)`. schema 변경 시 기존 row 는 drop, migration 코드 작성 불필요. 출시 단계에서 정책 재검토.

## 아키텍처·Flow 규약

도메인·리포지토리 설계 시 따른다. 과도한 추상화로 boilerplate 만 늘리지 않기 위함.

- **엄격한 클린 아키텍처 강제 안 함** — UseCase / Repository / DataSource 풀세트 대신 service 래퍼 한 겹으로 충분. 도메인 모델이 다중 데이터소스를 가로지를 때만 한 겹 더.
- **모든 앱에 도메인 레이어 필요 없음** — 비즈니스 로직이 화면과 1:1 이면 ViewModel 직결. 도메인 레이어는 "여러 화면이 같은 비즈니스 규칙 공유" 또는 "데이터 소스 추상화 필요" 일 때만 가치 있음. 안 그러면 매핑 boilerplate.
- **리포지토리 함수가 일회성이면 Flow 반환 금지** — `suspend fun login(): Result<User>` 가 `Flow<Result<User>>` 보다 명확. Flow 는 "값이 시간에 따라 바뀜" 의미라 일회성에 쓰면 의미 오염 + collect 강제·취소 처리·디바운스 부담.
- **Flow 반환 함수는 보통 non-suspend** — `fun observeUser(): Flow<User>` 는 cold flow 빌더라 호출 시점에 작업 시작 안 함. `suspend fun observeUser(): Flow<User>` 는 호출자가 collect 도 못 시작하는 어색한 API.

## Timeline 동작 규약

자막/더빙 제거 후 `TimelineStep` 은 `EditAudio` 단일 값. 영상 segment 편집 + BGM 삽입/조정 + 음원분리가 한 화면에 통합.

- **`commitSegmentEdit` 만 산출물 wipe** — 영상편집 모드의 ✓로 segment 자체를 바꿨을 때만 `resetTimelineDerivedResults()` 가 BGM/separation 을 정리.
- **Undo/redo 단일 스택** — BGM / 음원분리 / audio edit 모두 같은 `UndoRedoManager` 공유. segment edit 모드만 별도 `editModeUndoRedoManager`.
- **EnsureLatestRender 시점은 lazy** — export 등 산출물이 필요한 시점에 `EnsureLatestRenderUseCase` 가 BFF 에 단일 영상 render 잡 제출 (BGM atrim+amix 포함).

## Auth 흐름 요약

```
LoginScreen → LoginViewModel.signInWithGoogle()
  → AuthRepository.signInWithGoogle()
    → GoogleSignInClient.signIn()        // platform actual (SPM iOS / Credential Manager Android)
    → BffApi.exchangeGoogleIdToken(idToken)
    → AuthTokenStore.save(jwt, expiry)
    → UserSession.set(userId)
    → emit Result.success(AuthUser)
```

Apple 도 동일 패턴 (`AppleSignInClient` expect, BFF `/auth/apple`). 토큰은 `HttpClientFactory` 가 모든 outgoing 요청에 `Authorization: Bearer <jwt>` 헤더 인젝션 (구현 위치는 `HttpClientFactory.kt` 직접 확인).

## Skills

- `build` — `.claude/skills/build.md`. KMP 빌드 명령과 configuration-cache 주의.
- `ios-kn-patterns` — `.claude/skills/ios-kn-patterns.md`. iOS Kotlin/Native 자주 만나는 버그 패턴 모음 (NSURL, AVAsset, AVPlayer, K/N cinterop, NSData↔ByteArray, AVMutableComposition, CMP UIKitView, GoogleSignIn SPM, AuthenticationServices). iosMain 또는 cmp/iosMain 코드 쓸 때 참조. 새 버그 만나면 본 skill 에 추가.
