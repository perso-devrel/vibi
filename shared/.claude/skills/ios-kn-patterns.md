---
name: ios-kn-patterns
description: iOS Kotlin/Native 자주 만나는 버그 패턴 모음 — NSURL/AVAsset/AVPlayer/AVMutableComposition/NSData↔ByteArray. iosMain 또는 cmp/iosMain 코드 쓸 때 참조.
user_invocable: false
trigger: iosMain
---

# iOS K/N Bug Patterns

이미 두 번 이상 만난 버그 모음 — 새 코드 작성 시 같은 실수 반복 금지. 새 버그 만나면 여기 추가.

## NSURL 의 절대 경로 처리 — `URLWithString(absolutePath)` 는 nil 안 반환하고 invalid URL 객체 만듦

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

## AVAsset 의 lazy loading — `duration` / `tracks` 즉시 호출 시 0/empty

**증상**: `AVURLAsset(url).duration` 이 `CMTimeGetSeconds = 0.0` 또는 `tracksWithMediaType(...)` 가 빈 list.

**원인**: AVURLAsset 의 `duration`, `tracks` 등은 lazy. 즉시 호출하면 아직 로드 안 됨.

**해결**:
1. `AVURLAsset` 생성 시 `mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true)` 옵션
2. `loadValuesAsynchronouslyForKeys(listOf("duration", "tracks"))` 를 `suspendCancellableCoroutine` 으로 감싸 대기 후 사용
3. duration 이 여전히 0 이면 `videoTrack.timeRange` 의 `CMTimeRangeGetEnd` 로 fallback

## K/N AVFoundation cinterop 의 audio setter 누락

**증상**: `AVPlayer.muted = true`, `AVPlayer.volume = 0f`, `AVPlayerItem.audioMix = mix`, `AVMutableAudioMix.inputParameters = ...` 가 모두 unresolved reference.

**원인**: ios_simulator_arm64 platform klib 에 audio 관련 setter 다수가 미노출.

**우회**:
1. **BFF 에서 mux 된 mp4 받아 단일 AVPlayer 로 재생** (현재 더빙 미리보기 패턴 — 가장 robust)
2. Swift bridge — iosApp 측 `@objc class` 가 AVMutableAudioMix 처리 후 `AVPlayerItem` 반환, K/N 에서 protocol 통해 inject

cinterop 으로 우회 시도하지 말 것 — 매번 막힘.

## iOS streaming AVPlayer (remote URL) 가 silent — 다운로드 후 AVAudioPlayer 로 우회

**증상**: `AVPlayer(uRL=remoteUrl)` 또는 `replaceCurrentItemWithPlayerItem(item)` 후 `play()` 호출해도 sound 안 남. KVC `setValue(NSNumber, forKey="volume")` 으로 volume 적용해도 동일.

**원인**: K/N AVFoundation cinterop 환경에서 AVPlayer streaming 의 audio output graph 연결이 silent fail. setter 누락의 연장선.

**해결 패턴**:
1. background coroutine (`Dispatchers.Default`) 에서 `NSData.dataWithContentsOfURL` 로 다운로드 — main thread sync 호출은 iOS 가 silent fail (`Synchronous URL loading should not occur on this application's main thread` 경고).
2. caches dir 에 임시 파일로 저장 (확장자 보존 — `.flac`/`.wav`/`.mp3`).
3. main thread 에서 `AVAudioPlayer(contentsOfURL=fileUrl, error=null)` init + play. data init (`AVAudioPlayer(data=)`) 보다 file mode 가 format 추측 안정.
4. release / new play 시 임시 파일 cleanup.

**적용 사이트** (참고): `cmp/.../platform/AudioPreviewer.ios.kt` (단일 stem ▶), `StemMixer.ios.kt` (multi-stem 동시 재생). 새 remote audio 재생 추가 시 동일 패턴.

## path-only URL → plist BFFBaseURL 직접 prepend 안전망

**증상**: `/api/v2/separate/.../stem/...?token=...` 같은 path-only URL 을 `NSURL.URLWithString` 에 넘기면 invalid URL 객체 (host 없음). AVPlayer streaming 이 `NSURLConnection error -1002` (badURL) 로 실패.

**해결 패턴**: KMP framework 빌드 캐시 / ViewModel state 캐시 등으로 path-only URL 이 새어 들어올 가능성 — iOS 측 player 자체에 fallback. `NSBundle.mainBundle.objectForInfoDictionaryKey("BFFBaseURL")` 직접 읽어 prepend 하는 self-contained 안전망 (`resolveAbsoluteAudioUrl`).

**적용 사이트**: `cmp/.../platform/AudioPreviewer.ios.kt`, `StemMixer.ios.kt`.

## NSData → ByteArray 복사 — `allocArrayOf(bytes)` dest 에 쓰지 말 것

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

## AVMutableComposition track 에 source 의 `preferredTransform` 안 옮기면 영상 회전 깨짐

**증상**: split 한 segment 의 속도 조절 (= multi-segment composition 재빌드) 후 영상이 옆으로 눕거나 위아래 반전.

**원인**: iOS 카메라는 raw 프레임을 항상 landscape 로 기록하고 회전 정보는 `AVAssetTrack.preferredTransform` 에 메타로 저장. AVMutableCompositionTrack 은 default 가 identity transform 이라 source 의 transform 이 자동으로 안 옮겨짐. SingleItem 경로 (AVURLAsset 직결) 는 AVPlayer 가 asset 의 transform 을 자동 적용해서 정상.

**해결**: composition video track 에 첫 source video track 의 `preferredTransform` 을 명시적으로 복사. 같은 sourceUri 의 split segment 들은 transform 동일하므로 1회.
```kotlin
videoTrack.preferredTransform = srcVideo.preferredTransform
```

**적용 사이트** (참고): `cmp/.../VideoPlayer.ios.kt` 의 `buildCompositionPlayer`. 새 AVMutableComposition 사용처 추가 시 동일 패턴.

## CMP 의 UIKitView 위에 Compose 가 안 그려짐

**증상**: `Box { VideoPlayer(...); Box(Modifier.align(TopCenter)) { ... } }` 의 두번째 Box 가 비디오 위에 안 보임.

**원인**: Compose Multiplatform iOS 에서 UIKit interop view 는 native layer 라 항상 최상위. 그 위에 Compose 가 paint 못 함.

**해결**: 비디오 영역 외부 (위/아래) 에 별도 Compose Row/Column 으로 배치. 진짜 overlay 가 필요하면 native UILabel 등을 K/N 에서 controller.view 에 addSubview.

## `@Composable` 안 익명 object 의 nested local function + mutable state 캡처 → K/N LocalDeclarationsLowering 충돌

**증상**: `linkDebugFrameworkIosSimulatorArm64` 단계에서
`Internal error in body lowering: java.lang.IllegalStateException: No dispatch receiver parameter for FUN LOCAL_FUNCTION name:<localFn>`
크래시. `compileKotlinIosSimulatorArm64` 는 통과하고 final link 에서만 터짐.

**원인**: `@Composable` 안에 익명 `object : XHandle { ... }` 두고, 그 안 메서드에서 nested local function 정의해 외곽의 mutable state 다수를 캡처하면 K/N 컴파일러 LocalDeclarationsLowering 가 깨짐. 캡처 수가 적으면 통과하지만 임계점 넘으면 실패.

**해결 패턴**: object 대신 별도 `private class` 로 분리. mutable state 는 클래스 필드로 두고 helper 는 일반 private 메서드.
```kotlin
@Composable
actual fun rememberX(): XHandle {
    val scope = rememberCoroutineScope()
    val handle = remember(scope) { IosXHandle(scope) }
    DisposableEffect(handle) { onDispose { handle.dispose() } }
    return handle
}
private class IosXHandle(private val scope: CoroutineScope) : XHandle {
    private val stateA = mutableLongStateOf(0L)
    private var pollJob: Job? = null
    private fun cancelPoll() { pollJob?.cancel(); pollJob = null }
    private fun startWithFile(fileUrl: NSURL) { ... }
    override fun play(...) { ... }
    fun dispose() { ... }
}
```

**적용 사이트** (참고): `cmp/.../platform/AudioPreviewer.ios.kt` (변환 후 통과), `StemMixer.ios.kt` (원래부터 이 패턴이라 충돌 없었음). 새 `@Composable` actual 작성 시 캡처 많아질 것 같으면 처음부터 class 로.
