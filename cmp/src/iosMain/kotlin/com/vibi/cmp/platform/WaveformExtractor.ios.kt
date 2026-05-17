@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.cmp.platform

import com.vibi.shared.platform.resolveStoredUriToFileUrl
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVLinearPCMIsNonInterleaved
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFoundation.AVAssetReader
import platform.AVFoundation.AVAssetReaderStatusReading
import platform.AVFoundation.AVAssetReaderTrackOutput
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.tracksWithMediaType
import platform.AVFoundation.AVMediaTypeAudio
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreMedia.CMBlockBufferCopyDataBytes
import platform.CoreMedia.CMBlockBufferGetDataLength
import platform.CoreMedia.CMSampleBufferGetDataBuffer
import platform.CoreMedia.CMSampleBufferInvalidate
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSNumber
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
 *
 * AVAudioFile 은 순수 오디오 컨테이너(m4a/mp3/wav/caf) 만 OK. 영상 컨테이너(mp4/mov) 는
 * init 실패하므로 [extractPeaksViaAssetReader] 로 fallback — audio track 만 디코딩.
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

        val audioFilePeaks = extractPeaksViaAudioFile(readableUrl, samples)
        val peaks = if (audioFilePeaks.isNotEmpty()) {
            audioFilePeaks
        } else {
            // AVAudioFile 실패 — 영상 컨테이너(mp4/mov) 가능성. AVAssetReader 로 audio track 디코딩.
            extractPeaksViaAssetReader(readableUrl, samples)
        }
        if (peaks.isNotEmpty()) peaksCache[cacheKey] = peaks
        peaks
    }

/** 순수 오디오 파일(m4a/mp3/wav/caf) 디코딩 경로. 영상 컨테이너에서는 nil → emptyList. */
private fun extractPeaksViaAudioFile(url: NSURL, samples: Int): List<Float> {
    val audioFile = runCatching {
        AVAudioFile(forReading = url, error = null)
    }.getOrNull() ?: return emptyList()

    val format = audioFile.processingFormat
    val frameCount = audioFile.length
    if (frameCount <= 0L) return emptyList()

    val buffer = runCatching {
        AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount.toUInt())
    }.getOrNull() ?: return emptyList()

    val readOk = runCatching {
        audioFile.readIntoBuffer(buffer, error = null)
        true
    }.getOrDefault(false)
    if (!readOk) return emptyList()

    val frames = buffer.frameLength.toInt()
    if (frames <= 0) return emptyList()

    // floatChannelData: CPointer<CPointerVar<FloatVar>>?. ptr[0] = 채널0 의 CPointer<FloatVar>.
    // mono / stereo 모두 채널0 만 사용 — peak 표시 목적상 한 채널이면 충분.
    val channels = buffer.floatChannelData ?: return emptyList()
    val ch0: CPointer<FloatVar> = channels[0] ?: return emptyList()

    // bucket 별 RMS — bucket-MAX 는 음악/말소리에서 거의 모든 bucket 이 1.0 근처로 saturate (짧은 peak
    // 하나만 있어도 max=1). RMS 가 평균 에너지를 반영해 다이내믹 레인지를 보존.
    val bucketSize = (frames / samples).coerceAtLeast(1)
    val rmsList = ArrayList<Float>(samples)
    var i = 0
    while (i < frames && rmsList.size < samples) {
        val end = (i + bucketSize).coerceAtMost(frames)
        var sumSq = 0.0
        var j = i
        while (j < end) {
            val s = ch0[j].toDouble()
            sumSq += s * s
            j++
        }
        val rms = kotlin.math.sqrt(sumSq / (end - i)).toFloat()
        rmsList.add(rms)
        i = end
    }
    return rmsList.normalizeToMax()
}

/**
 * RMS list 를 최대값 1.0 으로 normalize — 전체 클립이 조용해도 상대적으로 큰 부분은 max bar 로 보이고
 * 빈 영역은 짧게 유지. 최대값 0 (전 구간 무음) 은 그대로 0 으로 둠.
 */
private fun List<Float>.normalizeToMax(): List<Float> {
    val maxRms = this.maxOrNull() ?: return this
    if (maxRms <= 0f) return this
    return this.map { (it / maxRms).coerceIn(0f, 1f) }
}

/**
 * 영상 컨테이너(mp4/mov) 또는 AVAudioFile 이 못 읽는 포맷용. AVAssetReader 로 audio track 만
 * Float32 mono PCM 으로 디코딩, streaming 으로 bucket max 갱신.
 *
 * Streaming 채택 이유: 전체 PCM 을 메모리에 올리면 30분 영상 ≈ 320MB. estimatedTotalFrames 는
 * durationSec × 44.1kHz 로 추정 — 정확한 sampleRate 없어도 bucket index 분포만 결정하므로 OK.
 */
private fun extractPeaksViaAssetReader(url: NSURL, samples: Int): List<Float> {
    val asset = AVURLAsset(uRL = url, options = null)
    val audioTrack = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
        ?: run {
            println("[WaveformExtractor] no audio track: $url")
            return emptyList()
        }

    val durationSec = CMTimeGetSeconds(asset.duration)
    if (durationSec.isNaN() || durationSec <= 0.0) return emptyList()

    val reader = runCatching {
        AVAssetReader(asset = asset, error = null)
    }.getOrNull() ?: run {
        println("[WaveformExtractor] AVAssetReader init failed")
        return emptyList()
    }

    val outputSettings = mapOf<Any?, Any>(
        AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
        AVLinearPCMBitDepthKey to NSNumber(int = 32),
        AVLinearPCMIsFloatKey to true,
        AVLinearPCMIsBigEndianKey to false,
        AVLinearPCMIsNonInterleaved to false,
        AVNumberOfChannelsKey to NSNumber(int = 1)
    )
    val output = AVAssetReaderTrackOutput(track = audioTrack, outputSettings = outputSettings)
    if (!reader.canAddOutput(output)) return emptyList()
    reader.addOutput(output)
    if (!reader.startReading()) {
        println("[WaveformExtractor] startReading failed")
        return emptyList()
    }

    // bucket index = globalFrame * samples / estimatedTotalFrames. sampleRate 정확치 몰라도 균등
    // 분포 유지. 추정치가 실제보다 작으면 마지막 bucket 으로 over-saturate → coerceAtMost(last) 로 보호.
    // bucket 별 sum(s²) + count 누적 후 종료 시 RMS — MAX 누적은 음악에서 거의 모든 bucket 이 1.0 으로
    // saturate (한 sample 만 peak 여도 bucket max=1). RMS 가 평균 에너지를 보존해 다이내믹 차이 가시화.
    val sampleRate = 44100.0
    val estimatedTotalFrames = (durationSec * sampleRate).toLong().coerceAtLeast(samples.toLong())
    val sumSqPerBucket = DoubleArray(samples)
    val countPerBucket = IntArray(samples)
    var globalFrameIdx = 0L

    while (reader.status == AVAssetReaderStatusReading) {
        val sample = output.copyNextSampleBuffer() ?: break
        try {
            val block = CMSampleBufferGetDataBuffer(sample) ?: continue
            val totalBytes = CMBlockBufferGetDataLength(block)
            val floatCount = (totalBytes / 4u).toInt()
            if (floatCount <= 0) continue
            memScoped {
                val buf = allocArray<FloatVar>(floatCount)
                val err = CMBlockBufferCopyDataBytes(block, 0u, totalBytes, buf.reinterpret<ByteVar>())
                if (err == 0) {
                    for (i in 0 until floatCount) {
                        val bucketIdx = (((globalFrameIdx + i).toDouble() * samples) / estimatedTotalFrames)
                            .toInt().coerceIn(0, samples - 1)
                        val v = buf[i].toDouble()
                        sumSqPerBucket[bucketIdx] += v * v
                        countPerBucket[bucketIdx]++
                    }
                }
            }
            globalFrameIdx += floatCount
        } finally {
            // copyNextSampleBuffer 는 +1 retained CMSampleBufferRef 반환. K/N cinterop 이 CFRelease
            // 를 노출하지 않을 수 있어 CMSampleBufferInvalidate 로 internal resource 해제 후 GC 에 위임.
            CMSampleBufferInvalidate(sample)
        }
    }

    val rmsList = (0 until samples).map { i ->
        val c = countPerBucket[i]
        if (c <= 0) 0f else kotlin.math.sqrt(sumSqPerBucket[i] / c).toFloat()
    }
    return rmsList.normalizeToMax()
}
