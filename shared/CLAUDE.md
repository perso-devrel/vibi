# CLAUDE.md — `:shared` 모듈

## Project

vibi `:shared` — **Kotlin Multiplatform 비즈니스 로직 모듈**. 도메인 모델 + 리포지토리(인터페이스+구현) + Ktor Client multiplatform BFF 클라이언트 + Room v19 (multiplatform) + UseCase + ViewModel + Koin DI 를 `commonMain` 에 두고 `androidMain`/`iosMain` 으로 플랫폼 의존을 분리한다. UI 코드는 형제 모듈 `:cmp` 가 본 모듈을 소비한다.

- **gradle 모듈명**: `:shared` (gradle 루트는 `vibi-mobile/`, `include(":shared")`)
- 코드 스타일: official
- `org.gradle.configuration-cache=true` (일부 명령은 `--no-configuration-cache` 필요 — `.claude/skills/build.md` 참조)
- 코드 패키지는 아직 `com.dubcast.shared.*` (Room `DubCastDatabase` + iOS framework 이름 영향). 폴더·docs 는 `vibi` 로 통일하되 패키지 마이그레이션은 별도 작업.

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
    ├── commonMain/kotlin/com/dubcast/shared/
    │   ├── domain/
    │   │   ├── model/                       # DubClip, Segment, Voice, Stem, EditProject,
    │   │   │                                # SubtitleClip, BgmClip, ImageClip, TextOverlay,
    │   │   │                                # VideoInfo, ImageInfo, ValidationResult, ... (15 files)
    │   │   ├── repository/                  # 인터페이스 (TtsRepository, AudioSeparationRepository,
    │   │   │                                #         AutoDubRepository, AutoSubtitleRepository, ...)
    │   │   ├── usecase/                     # 11 카테고리 (input/tts/subtitle/separation/timeline/...)
    │   │   └── util/                        # LanePacking, ColorValidation
    │   ├── data/
    │   │   ├── remote/api/BffApi.kt         # Ktor Client multiplatform — 12 v2 엔드포인트 + lipsync
    │   │   ├── remote/dto/                  # kotlinx.serialization DTO
    │   │   ├── repository/                  # 인터페이스 구현 (Room + BFF)
    │   │   └── local/db/                    # Room v19 (DubCastDatabase, 7 entity + 7 DAO + Migrations)
    │   ├── ui/                              # ViewModel (InputVM, TimelineVM, ExportVM, ShareVM, ChatVM)
    │   ├── domain/chat/                     # ChatToolDispatcher · ProjectContextBuilder (Gemini routing)
    │   ├── platform/                        # FileSystem expect, currentTimeMillis 등
    │   └── di/                              # Koin 모듈 (database/network/repository/usecase/viewmodel)
    ├── androidMain/                         # AndroidVideoMetadataExtractor, AndroidGallerySaver,
    │                                        # AutoDubRepositoryImpl, AutoSubtitleRepositoryImpl,
    │                                        # MediaJobUploader, AndroidExportPlatformAdapter,
    │                                        # androidPlatformModule
    ├── iosMain/                             # IosVideoMetadataExtractor, IosGallerySaver,
    │                                        # IosAutoDubRepositoryImpl, IosAutoSubtitleRepositoryImpl,
    │                                        # IosMediaJobUploader, IosExportPlatformAdapter,
    │                                        # iosPlatformModule
    └── commonTest/                          # BffApiTest (13), MigrationsTest, Migration7To8Test (4)
```

## 중요 제약

- `commonMain` 에 **Android/JVM 전용 API 금지** (`android.*`, `java.io.File`, `java.util.UUID`, `System.currentTimeMillis`). 플랫폼 의존(파일시스템·오디오·메타데이터·갤러리)은 `expect fun` 또는 platform adapter interface (예: `ExportPlatformAdapter`).
- Retrofit / Moshi / OkHttp / Hilt 는 iOS 불가 — 네트워크는 Ktor Client multiplatform, 직렬화는 kotlinx.serialization, DI 는 Koin.
- 본 모듈은 **로직만**. UI 코드(`@Composable`)는 `cmp/` 로.
- 본 모듈이 모바일 도메인의 단일 source of truth — legacy-android 시절의 모델은 모두 흡수됐다.

## 아키텍처·Flow 규약

도메인·리포지토리 설계 시 따른다. 과도한 추상화로 boilerplate 만 늘리지 않기 위함.

- **엄격한 클린 아키텍처 강제 안 함** — UseCase / Repository / DataSource 풀세트 대신 service 래퍼 한 겹으로 충분. 도메인 모델이 다중 데이터소스를 가로지를 때만 한 겹 더.
- **모든 앱에 도메인 레이어 필요 없음** — 비즈니스 로직이 화면과 1:1 이면 ViewModel 직결. 도메인 레이어는 "여러 화면이 같은 비즈니스 규칙 공유" 또는 "데이터 소스 추상화 필요" 일 때만 가치 있음. 안 그러면 매핑 boilerplate.
- **리포지토리 함수가 일회성이면 Flow 반환 금지** — `suspend fun login(): Result<User>` 가 `Flow<Result<User>>` 보다 명확. Flow 는 "값이 시간에 따라 바뀜" 의미라 일회성에 쓰면 의미 오염 + collect 강제·취소 처리·디바운스 부담.
- **Flow 반환 함수는 보통 non-suspend** — `fun observeUser(): Flow<User>` 는 cold flow 빌더라 호출 시점에 작업 시작 안 함. `suspend fun observeUser(): Flow<User>` 는 호출자가 collect 도 못 시작하는 어색한 API.

## Known iOS bug patterns

이미 두 번 이상 만난 버그 모음 — 새 코드 작성 시 같은 실수 반복 금지. 새 버그 만나면 여기 추가.

### NSURL 의 절대 경로 처리 — `URLWithString(absolutePath)` 는 nil 안 반환하고 invalid URL 객체 만듦

**증상**: `NSData.dataWithContentsOfURL`, `AVURLAsset.tracks`, `AVAsset.duration`, `AVPlayer` 등이 nil/empty/0 을 silent 하게 반환. 에러도 안 던짐. 영상 안 보임 / 메타데이터 unreadable / multipart 업로드 "cannot read source media".

**원인**: PHPicker 가 반환하는 path 는 `/Users/.../Documents/...` 같은 **절대 경로 (no scheme)**. Kotlin/Native 의
`NSURL.URLWithString("/Users/...")` 는 `nil` 을 반환할 거라 기대하지만 실제로는 invalid URL 객체를 만들어서
`?: NSURL.fileURLWithPath(uri)` fallback 이 발동 안 함.

**해결 패턴 (모든 NSURL 생성 site 에 적용)**:
```kotlin
val url = if (uri.startsWith("file://")) {
    NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
} else {
    NSURL.fileURLWithPath(uri)
}
```

**적용한 파일들** (참고): `IosVideoMetadataExtractor`, `IosMediaJobUploader`, `cmp/.../VideoPlayer.ios.kt`. 새 NSURL 사용처 추가 시 동일 패턴 사용 필수.

### AVAsset 의 lazy loading — `duration` / `tracks` 즉시 호출 시 0/empty

**증상**: `AVURLAsset(url).duration` 이 `CMTimeGetSeconds = 0.0` 또는 `tracksWithMediaType(...)` 가 빈 list.

**원인**: AVURLAsset 의 `duration`, `tracks` 등은 lazy. 즉시 호출하면 아직 로드 안 됨.

**해결**:
1. `AVURLAsset` 생성 시 `mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true)` 옵션
2. `loadValuesAsynchronouslyForKeys(listOf("duration", "tracks"))` 를 `suspendCancellableCoroutine` 으로 감싸 대기 후 사용
3. duration 이 여전히 0 이면 `videoTrack.timeRange` 의 `CMTimeRangeGetEnd` 로 fallback

### K/N AVFoundation cinterop 의 audio setter 누락

**증상**: `AVPlayer.muted = true`, `AVPlayer.volume = 0f`, `AVPlayerItem.audioMix = mix`, `AVMutableAudioMix.inputParameters = ...` 가 모두 unresolved reference.

**원인**: ios_simulator_arm64 platform klib 에 audio 관련 setter 다수가 미노출.

**우회**:
1. **BFF 에서 mux 된 mp4 받아 단일 AVPlayer 로 재생** (현재 더빙 미리보기 패턴 — 가장 robust)
2. Swift bridge — iosApp 측 `@objc class` 가 AVMutableAudioMix 처리 후 `AVPlayerItem` 반환, K/N 에서 protocol 통해 inject

cinterop 으로 우회 시도하지 말 것 — 매번 막힘.

### NSData → ByteArray 복사 — `allocArrayOf(bytes)` dest 에 쓰지 말 것

**증상**: 영상 업로드 후 BFF 콘솔에서 "ffprobe: moov atom not found" / "Invalid data found when processing input" / 71MB zero-filled file. Perso 가 silent 하게 결과 없음 (404, F5001, "no stems available" 등 다양한 후속 에러).

**원인**: `memcpy(allocArrayOf(bytes), src, len)` 의 `allocArrayOf(bytes)` 는 **새 native buffer 를 만들어 거기로 카피**. memScoped 종료 시 그 buffer free, 우리 ByteArray 는 0 그대로.

**해결 패턴**:
```kotlin
val bytes = ByteArray(length)
if (length > 0) {
    nsData.bytes?.let { src ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, length.toULong())
        }
    }
}
```
ByteArray 의 주소를 `usePinned` 로 잡아서 그게 memcpy 의 dest. 새 NSData ↔ ByteArray 변환 site 추가 시 동일 패턴.

### CMP 의 UIKitView 위에 Compose 가 안 그려짐

**증상**: `Box { VideoPlayer(...); Box(Modifier.align(TopCenter)) { ... } }` 의 두번째 Box 가 비디오 위에 안 보임.

**원인**: Compose Multiplatform iOS 에서 UIKit interop view 는 native layer 라 항상 최상위. 그 위에 Compose 가 paint 못 함.

**해결**: 비디오 영역 외부 (위/아래) 에 별도 Compose Row/Column 으로 배치. 진짜 overlay 가 필요하면 native UILabel 등을 K/N 에서 controller.view 에 addSubview.

## Skills

- `build` — `.claude/skills/build.md`. KMP 빌드 명령과 configuration-cache 주의.
