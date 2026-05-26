@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.shared.platform

import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetExportPresetAppleM4A
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCancelled
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetExportSessionStatusFailed
import platform.AVFoundation.AVFileTypeAppleM4A
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.duration
import platform.AVFoundation.setTimeRange
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS 분리 audio 준비. `AVAssetExportSession(preset = AppleM4A)` 가 모든 케이스 (video / audio
 * compatible / audio incompatible) 를 한 API 로 처리 — preset 이 audio-only m4a (AAC) 출력으로 강제.
 *
 * - `shouldOptimizeForNetworkUse = true` — moov atom 을 파일 앞으로 이동 (Perso streaming 호환).
 * - `beginBackgroundTask` wrap — 사용자가 export 중 home 누르면 iOS 가 추가 grace time 부여, 만료
 *   시 `AudioExtractException.Cancelled` 로 떨어져 retry UX 안내.
 *
 * Concurrency invariants:
 * - **Single-resume**: `resumed` AtomicReference 로 completion handler / cancellation handler 가
 *   동시에 fire 해도 cont.resume 은 정확히 한 번. 두 번 resume 시 `IllegalStateException` 으로
 *   coroutine 깨짐 → 본 가드 없이는 사용자가 export 중 sheet dismiss 시 100% crash.
 * - **Single-end**: bg task 를 expiration handler 와 정상 종료 finally 양쪽이 동시에 end 시도해도
 *   `endBackgroundTask` 는 정확히 한 번 호출. 두 번 호출 시 `NSInternalInconsistencyException`.
 *   handler 가 register 와 ref 저장 사이에 동기적으로 fire 되는 edge 도 expirationFired flag 로 보완.
 */
class IosAudioExtractor : AudioExtractor {

    override val isSupported: Boolean = true

    override suspend fun prepareSeparationAudio(
        sourceUri: String,
        sourceKind: AudioSourceKind,
        startMs: Long?,
        endMs: Long?,
    ): PreparedAudio {
        val sourceUrl = resolveStoredUriToFileUrl(sourceUri)
            ?: throw AudioExtractException.SourceCorrupt

        val asset: AVAsset = AVURLAsset(
            uRL = sourceUrl,
            options = mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true),
        )

        val outputDir = "${cacheDirectory()}/separation-prep"
        ensureDir(outputDir)
        val outputPath = "$outputDir/${sourceUri.hashCode().toUInt()}_${startMs ?: 0}_${endMs ?: 0}.m4a"
        NSFileManager.defaultManager.removeItemAtPath(outputPath, error = null)
        val outputUrl = NSURL.fileURLWithPath(outputPath)

        val session = AVAssetExportSession(asset = asset, presetName = AVAssetExportPresetAppleM4A)
            ?: throw AudioExtractException.CodecUnsupported
        session.outputURL = outputUrl
        session.outputFileType = AVFileTypeAppleM4A
        session.shouldOptimizeForNetworkUse = true

        if (startMs != null && endMs != null && endMs > startMs) {
            val durationSec = asset.duration.useContents { CMTimeGetSeconds(this.readValue()) }
            val startSec = (startMs / 1000.0).coerceAtLeast(0.0).coerceAtMost(durationSec)
            val endSecRaw = (endMs / 1000.0).coerceAtMost(durationSec)
            val rangeDur = (endSecRaw - startSec).coerceAtLeast(0.0)
            val timescale = 600
            session.setTimeRange(
                CMTimeRangeMake(
                    start = CMTimeMakeWithSeconds(startSec, timescale),
                    duration = CMTimeMakeWithSeconds(rangeDur, timescale),
                )
            )
        } else {
            session.setTimeRange(
                CMTimeRangeMake(
                    start = kCMTimeZero.readValue(),
                    duration = asset.duration,
                )
            )
        }

        val app = UIApplication.sharedApplication
        // bgTaskRef: null sentinel = "이미 end 됨". getAndSet 으로 single-end 보장.
        // expirationFired: handler 가 register 와 bgTaskRef.value=taskId 사이에 동기적으로
        // fire 됐을 때 보완. ref 가 그 시점에 null 이라 handler 가 task 를 못 end 시키는 race 차단.
        val bgTaskRef = AtomicReference<UIBackgroundTaskIdentifier?>(null)
        // AtomicInt 0/1 사용 — AtomicReference<Boolean> 은 identity equality 라 boxed Boolean 의
        // 동등성 보장이 K/N 에서 일관되지 않다는 경고를 내고, false-true compareAndSet 이 fail 할 수 있음.
        val expirationFired = AtomicInt(0)
        val taskId = app.beginBackgroundTaskWithExpirationHandler {
            session.cancelExport()
            expirationFired.value = 1
            bgTaskRef.getAndSet(null)?.let { id ->
                if (id != UIBackgroundTaskInvalid) app.endBackgroundTask(id)
            }
        }
        if (expirationFired.value == 1) {
            // Handler 가 register 중 동기 fire — 위 분기에서 ref 가 null 이라 못 end 시킨 taskId 를 여기서 end.
            if (taskId != UIBackgroundTaskInvalid) app.endBackgroundTask(taskId)
        } else {
            bgTaskRef.value = taskId
        }

        // resumed: completion handler 와 invokeOnCancellation 가 동시에 fire 돼도 cont.resume 1회 보장.
        // invokeOnCancellation 자체는 kotlinx 가 cont 를 CancellationException 으로 resume 하므로
        // 우리가 추가 resume 안 함 — 다만 resumed flag 를 1 로 마킹해 completion 의 resume 차단.
        val resumed = AtomicInt(0)

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                cont.invokeOnCancellation {
                    resumed.value = 1
                    session.cancelExport()
                }
                session.exportAsynchronouslyWithCompletionHandler {
                    if (!resumed.compareAndSet(0, 1)) return@exportAsynchronouslyWithCompletionHandler
                    when (session.status) {
                        AVAssetExportSessionStatusCompleted -> cont.resume(Unit)
                        AVAssetExportSessionStatusCancelled ->
                            cont.resumeWithException(AudioExtractException.Cancelled)
                        AVAssetExportSessionStatusFailed ->
                            cont.resumeWithException(mapExportError(session.error))
                        else ->
                            cont.resumeWithException(AudioExtractException.Unknown("status=${session.status}"))
                    }
                }
            }
        } finally {
            bgTaskRef.getAndSet(null)?.let { id ->
                if (id != UIBackgroundTaskInvalid) app.endBackgroundTask(id)
            }
        }

        return PreparedAudio(path = outputPath, mimeType = "audio/mp4", ext = "m4a")
    }

    /** iOS `NSError` code 분석 → AudioExtractException 매핑. 정확한 code 매트릭스가 OS 버전에
     *  따라 흔들리므로 보수적으로 SourceCorrupt / Unknown 사이로만 분기. 디스크 부족은 NSError
     *  domain=NSPOSIXErrorDomain code=28 (ENOSPC) 인 경우 캐치. */
    private fun mapExportError(error: NSError?): AudioExtractException {
        val msg = error?.localizedDescription ?: "unknown export error"
        val domain = error?.domain
        val code = error?.code
        if (domain == "NSPOSIXErrorDomain" && code == 28L) return AudioExtractException.DiskFull
        if (msg.contains("could not be decoded", ignoreCase = true) ||
            msg.contains("corrupt", ignoreCase = true)
        ) return AudioExtractException.SourceCorrupt
        return AudioExtractException.Unknown(msg)
    }

    private fun cacheDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        return requireNotNull(paths.firstOrNull() as? String) { "Could not resolve iOS cache dir." }
    }

    private fun ensureDir(path: String) {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }
}
