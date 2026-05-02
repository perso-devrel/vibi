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
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
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

                runCatching {
                    val session = AVAudioSession.sharedInstance()
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

                if (!rec.prepareToRecord()) {
                    onError("녹음 준비 실패")
                    return
                }
                if (!rec.record()) {
                    onError("마이크 권한이 필요합니다")
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
                recorder = null
                recording = false
                if (path != null) onRecorded(path, durationMs)
            }
        }
    }
}
