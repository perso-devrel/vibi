# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DubCast is an Android app for video dubbing and localization. Users select a video, add AI dubbing clips on a timeline editor, and export the final video with dubbing/lip-sync/subtitles using on-device FFmpeg rendering. The BFF server provides TTS, lip-sync, and voice APIs.

The app is written entirely in Kotlin using Jetpack Compose (no XML layouts). README.md contains the full product spec in Korean.

## Build Commands

```bash
./gradlew assembleDebug                # Build debug APK
./gradlew test                         # Run unit tests (excludes @ApiTest)
./gradlew test -Pinclude.api.tests     # Run all tests including API integration tests
./gradlew testDebugUnitTest --tests "*.AssGeneratorTest"  # Run a single test class
./gradlew connectedAndroidTest         # Instrumented tests (requires device/emulator)
```

API integration tests (`BffApiIntegrationTest`) are excluded by default via the `@ApiTest` JUnit category marker.

## Architecture

Single-module app (`app/`) using Clean Architecture (domain/data/ui layers) with MVVM.

**Layers and data flow:**
- **UI** (`ui/`): Compose screens + ViewModels. Screens: Input, Timeline, Export, Share. Each has its own ViewModel exposing `StateFlow<UiState>`.
- **Domain** (`domain/`): Pure Kotlin use cases and repository interfaces. Use cases organized by feature: `input/`, `tts/`, `timeline/`, `subtitle/`, `lipsync/`, `export/`.
- **Data** (`data/`): Room database (`local/db/`), Retrofit API (`remote/api/`), and repository implementations (`repository/`).

**Key architectural decisions:**
- Navigation uses a sealed class `Screen` with type-safe routes: Input → Timeline → Export → Share.
- `BFF_BASE_URL` is injected from `local.properties` via BuildConfig.
- All builds connect to the real BFF server. No mock interceptor — `BFF_BASE_URL` in `local.properties` must point to a running BFF instance.
- Undo/redo uses a generic `UndoRedoManager<T>` with ArrayDeque (max 50 states).
- Subtitle rendering generates `.ass` (Advanced SubStation Alpha) files with Noto Sans KR font, rendered via ffmpeg-kit (`io.github.maitrungduc1410:ffmpeg-kit-min`).
- Audio mixing uses ffmpeg adelay+amix filter chain for dubbing overlay.
- Export screen offers two modes: original-only or with translation (dubbing/lip-sync/auto-subtitles + language selection).

## Domain Models

- `EditProject` — video project with metadata (URI, dimensions, duration)
- `DubClip` — dubbing audio clip placed on timeline (text, voice, audio file, position, duration)
- `SubtitleClip` — subtitle with text, time range, and position
- `Voice` — TTS voice (id, name, language)
- `SubtitlePosition` / `Anchor` — subtitle placement (TOP/MIDDLE/BOTTOM + y-offset)

## API Endpoints (BFF v2)

- `GET /api/v2/voices` — voice list
- `POST /api/v2/tts` — TTS synthesis
- `POST /api/v2/lipsync` — lip-sync request (multipart)
- `GET /api/v2/lipsync/{jobId}/status` — lip-sync status polling
- `GET /api/v2/lipsync/{jobId}/download` — lip-sync result download

## Dependency Injection

Hilt with three modules in `di/`:
- `DatabaseModule` — Room singleton with migrations (v1→v2→v3→v4)
- `NetworkModule` — Retrofit + OkHttp + Moshi + logging
- `RepositoryModule` — binds repository interfaces to implementations

## Testing Patterns

- **Mocking**: MockK
- **Flow testing**: Turbine
- **Coroutines**: `MainDispatcherRule` (in `test/util/`) replaces `Dispatchers.Main`
- **Fakes**: `fake/` package contains `FakeDubClipRepository`, `FakeSubtitleClipRepository`, `FakeEditProjectRepository`, `FakeTtsRepository`, `FakeLipSyncRepository`, `FakeGallerySaver`, `FakeFfmpegExecutor`, `FakeVideoMetadataExtractor`
- Tests follow the pattern of the use case / ViewModel under test — check existing tests before writing new ones

## SDK Targets

compileSdk/targetSdk: 36, minSdk: 24, Java 11 compatibility.

## Development Approach

The project follows TDD: write failing tests first, implement minimal code to pass, then refactor. BFF server-side functionality is out of scope for this codebase.
