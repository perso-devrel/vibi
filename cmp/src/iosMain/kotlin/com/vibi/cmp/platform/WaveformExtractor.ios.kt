@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.cmp.platform

import com.vibi.shared.platform.resolveStoredUriToFileUrl
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSURL

/**
 * 추출 결과 캐시 — 같은 URL 을 sheet 가 여러 번 열어도 PCM 디코딩은 1회. samples 키 포함해
 * UI 막대 수가 다른 두 호출은 별도 캐시.
 */
private val peaksCache = mutableMapOf<String, List<Float>>()

/**
 * AVAudioFile 로 전체 frames 를 메모리에 읽고 (processingFormat = Float32 PCM 보장),
 * 채널 0 의 |sample| max 를 bucket 단위로 다운샘플링.
 *
 * 음원분리 stem 또는 BGM 클립(보통 < 10MB, 몇 분) 가정 — 전체 frames in-memory OK.
 * 매우 큰 파일이면 frame 단위 chunk read + 누적으로 바꿔야 함 (현재 미적용).
 *
 * 입력 url 은:
 *  - file:// 또는 절대 path: 그대로 디코딩
 *  - Documents-relative ("picker_media/foo.mov" 등): [resolveStoredUriToFileUrl] 로 변환
 *  - http(s) BFF stem URL: [downloadAudioToCache] 로 임시 파일 받아 디코딩
 */
actual suspend fun extractAudioPeaks(localPath: String, samples: Int): List<Float> =
    withContext(Dispatchers.Default) {
        if (samples <= 0) return@withContext emptyList()
        val cacheKey = "$localPath#$samples"
        peaksCache[cacheKey]?.let { return@withContext it }

        val absolute = resolveAbsoluteAudioUrl(localPath)
        val isRemote = absolute.startsWith("http://") || absolute.startsWith("https://")
        val readableUrl: NSURL = if (isRemote) {
            val tempPath = downloadAudioToCache(absolute, prefix = "waveform") ?: run {
                println("[WaveformExtractor] remote download failed: $absolute")
                return@withContext emptyList()
            }
            NSURL.fileURLWithPath(tempPath)
        } else {
            // ios-kn-patterns: 절대 path → fileURLWithPath. URLWithString 은 invalid URL 객체 silent
            // 생성하므로 fallback. 또한 Documents-relative 는 resolveStoredUriToFileUrl 로 변환.
            resolveStoredUriToFileUrl(absolute) ?: run {
                if (absolute.startsWith("file://")) {
                    NSURL.URLWithString(absolute) ?: NSURL.fileURLWithPath(absolute.removePrefix("file://"))
                } else {
                    NSURL.fileURLWithPath(absolute)
                }
            }
        }

        val audioFile = runCatching {
            AVAudioFile(forReading = readableUrl, error = null)
        }.getOrNull() ?: run {
            println("[WaveformExtractor] AVAudioFile init failed: $localPath")
            return@withContext emptyList()
        }

        val format = audioFile.processingFormat
        val frameCount = audioFile.length
        if (frameCount <= 0L) return@withContext emptyList()

        val buffer = runCatching {
            AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount.toUInt())
        }.getOrNull() ?: return@withContext emptyList()

        val readOk = runCatching {
            audioFile.readIntoBuffer(buffer, error = null)
            true
        }.getOrDefault(false)
        if (!readOk) return@withContext emptyList()

        val frames = buffer.frameLength.toInt()
        if (frames <= 0) return@withContext emptyList()

        // floatChannelData: CPointer<CPointerVar<FloatVar>>?. ptr[0] = 채널0 의 CPointer<FloatVar>.
        // mono / stereo 모두 채널0 만 사용 — peak 표시 목적상 한 채널이면 충분.
        val channels = buffer.floatChannelData ?: return@withContext emptyList()
        val ch0: CPointer<FloatVar> = channels[0] ?: return@withContext emptyList()

        val bucketSize = (frames / samples).coerceAtLeast(1)
        val peaks = ArrayList<Float>(samples)
        var i = 0
        while (i < frames && peaks.size < samples) {
            val end = (i + bucketSize).coerceAtMost(frames)
            var maxAbs = 0f
            var j = i
            while (j < end) {
                val s = abs(ch0[j])
                if (s > maxAbs) maxAbs = s
                j++
            }
            peaks.add(maxAbs.coerceIn(0f, 1f))
            i = end
        }
        if (peaks.isNotEmpty()) peaksCache[cacheKey] = peaks
        peaks
    }
