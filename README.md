# vibi

A mobile AI app that removes only the sounds you don't want from a video.
**Kotlin Multiplatform** + **Compose Multiplatform** — a single codebase for Android · iOS.

> Subtitles / dubbing / lipsync have been removed from both the BFF and the mobile code. Only audio separation, BGM, and segment editing remain.

---

## Quick start

You don't need to run a backend yourself — point at the deployed BFF (`api.vibi.fm`) and it builds and runs right away.

```bash
# 1) Create local.properties (gitignored). Pointing at the deployed BFF means no local backend.
#    sdk.dir=/Users/<you>/Library/Android/sdk
#    BFF_BASE_URL=https://api.vibi.fm/

# 2) Android build → cmp/build/outputs/apk/debug/cmp-debug.apk
./gradlew :shared:build :cmp:assembleDebug

# 3) iOS
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

> `api.vibi.fm` is the **live backend** (real credits & accounts). For work that touches the backend, use the local setup under [Backend (vibi-bff)](#backend-vibi-bff).

## Prerequisites

| Tool | Purpose | Notes |
|---|---|---|
| **JDK 17+** | run gradle | Output bytecode targets JVM 11 (`jvmTarget = JVM_11`) |
| **Android SDK** | Android build | Install Android Studio or cmdline-tools |
| **Xcode 15+** | iOS build | App Store or developer.apple.com |
| **XcodeGen** | generate the iOS project | `brew install xcodegen` |

## Setup

### 1) `local.properties`

Create it at the repo root (gitignored):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
BFF_BASE_URL=https://api.vibi.fm/
```

Set `BFF_BASE_URL` to match the **BFF you want to hit** — there is no mock interceptor:

| Target | URL |
|---|---|
| Deployed BFF (no local backend) | `https://api.vibi.fm/` |
| Local BFF · Android emulator | `http://10.0.2.2:8080/` |
| Local BFF · Android device | `http://192.168.x.x:8080/` (your Mac's LAN IP) |
| Local BFF · iOS simulator | `http://localhost:8080/` or the Mac's LAN IP |
| Local BFF · iOS device | the Mac's LAN IP |

### 2) Generate the iOS project (once for iOS builds, and whenever `project.yml` changes)

```bash
cd iosApp
xcodegen generate
```

This produces `iosApp/iosApp.xcodeproj`. XcodeGen manages the `com.apple.developer.applesignin` entitlement (`iosApp.entitlements`) and the GoogleSignIn SPM dependency (`google/GoogleSignIn-iOS`, from 8.0.0) as the single source of truth.

## Build & run

### Android

```bash
./gradlew :shared:build :cmp:assembleDebug
# APK: cmp/build/outputs/apk/debug/cmp-debug.apk
```

`gradle.properties` enables the configuration cache, so normal builds work without any flag. Only add `--no-configuration-cache` for the specific tasks where the cache breaks (details in [`build.md`](./shared/.claude/skills/build.md)).

### iOS

```bash
# 1. Compile the KMP framework (Apple Silicon simulator)
./gradlew :shared:compileKotlinIosSimulatorArm64
# 2. Open in Xcode → run on a simulator
open iosApp/iosApp.xcodeproj
```

The `iosApp/` preBuildScripts call `:shared:embedAndSignAppleFrameworkForXcode` automatically, so no manual framework embedding is needed.

### Unit tests

```bash
./gradlew :shared:testDebugUnitTest
./gradlew :shared:allTests
```

---

## Backend (vibi-bff)

The app calls **vibi-bff** (Kotlin/Ktor) for audio separation, rendering, and social login (Google + Apple). Every external API (Perso · Google tokeninfo · Apple JWKS) is handled only on the BFF; the client uses `/api/v2` exclusively. The only thing the client obtains directly is the **ID Token from the Google/Apple native SDK**, which it then exchanges with the BFF.

**Two ways to connect:**

- **Deployed** — `BFF_BASE_URL=https://api.vibi.fm/`. No local backend (the Quick start default).
- **Run locally** — clone the sibling repo `../vibi-bff`, start it on `:8080`, and set `BFF_BASE_URL` to `10.0.2.2` / `localhost`. See `vibi-bff/README.md` for the exact run command and env.

**BFF env (when running locally):** `GOOGLE_OAUTH_CLIENT_IDS` must include the `aud` (iOS / Android / Web client id) of the ID Token the mobile app sends. With Apple Sign In enabled, also `APPLE_OAUTH_CLIENT_IDS = com.vibi.ios`. See `vibi-bff/README.md` for the full table.

**Current operational state:** free pre-launch — in-app purchase is disabled (`RuntimeFlags.iapEnabled = false`; only free credits are shown, and when the balance is insufficient an "I want this" demand tab appears instead of a purchase button). The separation cost is 1 credit per started 5 minutes (ceil, minimum 1), computed by the BFF. Separated stems are cached permanently for offline editing and preview. The authoritative definitions of the gating and cost formula live in code (`RuntimeFlags` KDoc, BFF).

### Feature ↔ BFF mapping

| Feature | Client | BFF endpoint |
|---|---|---|
| Social login (Google + Apple) | LoginScreen · AuthRepository · `GoogleSignInClient` / `AppleSignInClient` | `POST /api/v2/auth/google` · `POST /api/v2/auth/apple` · `DELETE /api/v2/auth/account` |
| Credits (free pre-launch) | UserMenuSheet · CreditPurchaseSheet · `UserMenuViewModel` · `IapBridge` / `PurchaseLauncher` | `GET /api/v2/credits` · `GET /api/v2/credits/cost` · `POST /api/v2/credits/purchase` · `POST /api/v2/intent/paid-credits` ("I want this" demand signal) |
| Video upload | InputScreen + MediaPicker (PickVisualMedia / PHPicker) · VideoThumbnailExtractor | (local segment) |
| Audio separation (video segment + BGM clip) | AudioSeparationSheet · `StartAudioSeparationUseCase` / `PollSeparationUseCase` · local StemMixer | `POST /api/v2/separate` → poll `GET /api/v2/separate/{jobId}` (download stem urls from the response → local mix preview) |
| Range selection | UnifiedTimelineBar (inline) | (local Room) |
| Audio insert (file + instant recording + BGM trim) | AudioInsertSheet · `AudioPicker` / `AudioRecorder` · BgmTrimSheet | (local BgmClip) |
| Export | TimelineScreen save · `HybridRenderExecutor` · `SaveExportUseCase` · `PrewarmAssetUploadUseCase` → ShareScreen | `POST /api/v2/assets/upload-url` → `PUT` (R2) → `POST /api/v2/render/v3` → poll `GET /api/v2/render/{jobId}/status` → `GET /api/v2/render/{jobId}/download` |

### External dependencies (via the BFF — never called directly by the client)

- **Perso AI** — audio separation
- **Google tokeninfo / Apple JWKS** — ID Token verification (the mobile app only obtains the ID Token via the native SDK and forwards it to the BFF)

All API keys are kept only in the BFF env.

---

## Code structure (maintainers)

```
vibi-mobile/
├── shared/   # :shared — KMP business logic: domain · data (Ktor + Room v14) · ui (ViewModel) · platform (expect) · di (Koin)
│             #   commonMain · androidMain · iosMain · commonTest
├── cmp/      # :cmp — Compose Multiplatform UI: Splash · Login · Input · Timeline · Share
│             #   commonMain · androidMain (Media3, etc.) · iosMain (AVPlayer · PHPicker · GoogleSignIn, etc.)
└── iosApp/   # XcodeGen project.yml + Swift entry + iosApp.entitlements
```

Screen flow: **Splash → (signedIn) Input ↔ Timeline / (signedOut) Login → Input**.
Range selection on the timeline is inline via `UnifiedTimelineBar`, not a separate sheet. The sheet family (AudioSeparation · AudioInsert · BgmTrim · UserMenu · CreditPurchase) and the timeline UI details are in `cmp/CLAUDE.md`.

Per-layer packages, domain/data conventions, Room migrations, Compose conventions, iOS K/N patterns, and the like are delegated to the module docs:

- [`shared/CLAUDE.md`](./shared/CLAUDE.md) — KMP constraints, domain/data layer conventions, Timeline stepper behavior, the Auth flow, known iOS bug patterns
- [`cmp/CLAUDE.md`](./cmp/CLAUDE.md) — Compose conventions (StateFlow, sealed UiState, lambda stability), screen structure
- [`shared/.claude/skills/build.md`](./shared/.claude/skills/build.md) — build commands + configuration-cache caveats
- [`shared/.claude/skills/ios-kn-patterns.md`](./shared/.claude/skills/ios-kn-patterns.md) — iOS K/N patterns

---

## Troubleshooting

- **`commonMain` compile errors: `java.io.File`, `java.util.UUID`, `System.currentTimeMillis`** —
  unavailable on iOS. Use multiplatform alternatives (`kotlinx.io`, `kotlin.uuid.Uuid`, `kotlinx.datetime.Clock`) or `expect/actual`.
- **iOS framework link failure** — run `:shared:linkDebugFrameworkIosSimulatorArm64` directly to see the error.
- **Video not visible in the iOS simulator / metadata unreadable** — an issue handling the absolute path PHPicker returns. See `shared/.claude/skills/ios-kn-patterns.md` for the pattern.
- **`ConnectException` after setting `BFF_BASE_URL` to localhost on Android** — on the Android emulator the host's localhost is `10.0.2.2`. A physical device needs the LAN IP.
- **iOS Google Sign In not working** — check that the GoogleSignIn SPM is added in `project.yml` and that `Info.plist`'s `CFBundleURLTypes` has the reversed-client-id URL scheme. Run `xcodegen generate` after SPM changes.
- **iOS Apple Sign In error 1000** — a Personal Team build cannot use the Sign In with Apple capability. A paid Apple Developer Program membership is required. Enable it under App ID Capabilities and verify `com.apple.developer.applesignin` in `iosApp.entitlements`.
- **`./gradlew` not executable** — `chmod +x ./gradlew`.
