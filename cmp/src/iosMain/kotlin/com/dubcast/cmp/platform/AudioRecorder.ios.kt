@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dubcast.shared.platform.currentTimeMillis
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS: AVAudioRecorder + AVAudioSession (.playAndRecord). m4a (AAC) 로 cacheDir 저장.
 *
 * 마이크 권한은 Info.plist 의 NSMicrophoneUsageDescription 으로 prompt 자동 — 첫 record 시도 시
 * 시스템 다이얼로그. record() 가 false 반환하면 권한 거부로 간주.
 */
@Composable
actual fun rememberAudioRecorder(
    onRecorded: (uri: String, durationMs: Long) -> Unit,
    onError: (message: String) -> Unit,
): AudioRecorderController {
    var recorder by remember { mutableStateOf<AVAudioRecorder?>(null) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableStateOf(0L) }
    var recording by remember { mutableStateOf(false) }

    return remember {
        object : AudioRecorderController {
            override val isRecording: Boolean get() = recording
            override val currentLevel: Float get() {
                val rec = recorder ?: return 0f
                if (!recording) return 0f
                rec.updateMeters()
                // peakPower 는 dB (음수). -60dB → 0, 0dB → 1 로 매핑.
                val db = rec.peakPowerForChannel(0u)
                if (db.isNaN()) return 0f
                val clamped = db.coerceIn(-60f, 0f)
                return ((clamped + 60f) / 60f).coerceIn(0f, 1f)
            }

            override fun start() {
                if (recording) return
                val cachePath = (NSSearchPathForDirectoriesInDomains(
                    NSCachesDirectory, NSUserDomainMask, true
                ).firstOrNull() as? String) ?: run {
                    onError("cacheDir 접근 실패")
                    return
                }
                val dir = "$cachePath/recordings"
                NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
                val path = "$dir/rec_${currentTimeMillis()}.m4a"
                val url = NSURL.fileURLWithPath(path)

                // 권한 결정이 먼저 — setActive(true) 를 권한 grant 이전에 호출하면 hardware access
                // 가 절반 상태로 남아 silent 캡처될 수 있음. permission 확인 후 beginRecording
                // 안에서 session 을 fresh 활성화.
                val session = AVAudioSession.sharedInstance()
                when (session.recordPermission) {
                    AVAudioSessionRecordPermissionGranted -> beginRecording(url, path)
                    AVAudioSessionRecordPermissionDenied ->
                        onError("마이크 권한이 거부되었습니다. 설정 → 개인정보 → 마이크 에서 허용해주세요.")
                    else -> session.requestRecordPermission { granted: Boolean ->
                        // 콜백은 background queue 에서 올 수 있음 → main 으로 dispatch.
                        dispatch_async(dispatch_get_main_queue()) {
                            if (granted) beginRecording(url, path)
                            else onError("마이크 권한이 거부되었습니다.")
                        }
                    }
                }
            }

            private fun beginRecording(url: NSURL, path: String) {
                if (recording) return
                // 권한 grant 직후 session 을 fresh 활성화 — 이전 setActive 가 권한 결정 전에 호출돼
                // 있었다면 hardware access 가 partial 상태일 수 있음.
                val session = AVAudioSession.sharedInstance()
                runCatching {
                    session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
                    session.setActive(true, null)
                }.onFailure {
                    onError("AudioSession 설정 실패: ${it.message}")
                    return
                }
                // AVAudioQuality.AVAudioQualityHigh = 96 (NS_ENUM raw value).
                val settings = mapOf<Any?, Any?>(
                    AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
                    AVSampleRateKey to NSNumber(double = 44_100.0),
                    AVNumberOfChannelsKey to NSNumber(int = 1),
                    AVEncoderAudioQualityKey to NSNumber(long = 96L),
                )
                val rec = runCatching {
                    AVAudioRecorder(uRL = url, settings = settings, error = null)
                }.getOrNull() ?: run {
                    onError("AVAudioRecorder 초기화 실패")
                    return
                }
                rec.meteringEnabled = true
                if (!rec.prepareToRecord()) {
                    onError("녹음 준비 실패")
                    return
                }
                if (!rec.record()) {
                    onError("녹음 시작 실패")
                    return
                }
                recorder = rec
                outputPath = path
                startedAt = currentTimeMillis()
                recording = true
            }

            override fun stop() {
                val rec = recorder ?: return
                val path = outputPath
                val durationMs = (currentTimeMillis() - startedAt).coerceAtLeast(0L)
                rec.stop()
                runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
                // Diagnostic — m4a 컨테이너 헤더만으로도 600B 이상 → 그 이하면 마이크 hardware
                // 입력 없이 빈 파일 가까이 생성된 것. macOS 권한 미요청 시 이 케이스 빈번.
                if (path != null) {
                    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
                    val size = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
                    if (size < 1024L) {
                        onError("마이크 입력이 감지되지 않았습니다 (${size}B). macOS Simulator 의 마이크 권한을 확인해주세요.")
                    }
                }
                recorder = null
                recording = false
                if (path != null) onRecorded(path, durationMs)
            }
        }
    }
}
