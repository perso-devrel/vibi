package com.vibi.cmp.platform

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.vibi.cmp.BuildConfig
import com.vibi.shared.platform.cacheDirPath
import com.vibi.shared.platform.firstAudioTrackIndex
import com.vibi.shared.platform.optInt
import com.vibi.shared.platform.optLong
import com.vibi.shared.platform.setPlatformSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt

/**
 * Android 파형 추출 — iOS [extractAudioPeaks] (AVAudioFile / AVAssetReader) 등가.
 *
 * `MediaExtractor` + `MediaCodec` 디코더로 오디오 트랙을 PCM 으로 풀어 **채널0 의 bucket 별 RMS** 를
 * 계산하고, iOS 와 **동일한 표시 ref(0.4)** 로 0..1 정규화한다 (같은 WaveformPlayBar 에 들어가도 막대
 * 분포가 iOS 와 일치). 영상 컨테이너(mp4/mov)면 audio 트랙만 디코딩하므로 별도 분기 불필요.
 *
 * 입력: content:// / file:// / 절대경로(영상·오디오 모두), http(s)(MediaExtractor 네이티브 스트리밍).
 * 실패 / 오디오 트랙 없음 → 빈 list (UnifiedTimelineBar 가 회색 strip 으로 fallback).
 */
private const val PEAKS_CACHE_MAX = 64
private const val DISPLAY_REF = 0.4f
private val peaksCache = LinkedHashMap<String, List<Float>>(16, 0.75f, /*accessOrder=*/true)
private val peaksCacheLock = Mutex()

actual suspend fun extractAudioPeaks(localPath: String, samples: Int): List<Float> =
    withContext(Dispatchers.Default) {
        if (samples <= 0 || localPath.isBlank()) return@withContext emptyList()
        val cacheKey = "$localPath#$samples"
        peaksCacheLock.withLock { peaksCache[cacheKey] }?.let { return@withContext it }

        // 원격(BFF) stem/오디오 URL 을 MediaExtractor 로 직접 스트리밍하면 HTTP 왕복과 프레임 디코드가
        // 뒤섞여 매우 느리다 — Android 파형 로딩 지연의 주원인 (iOS 는 downloadAudioToCache 로 파일
        // 전체를 먼저 받아 로컬 디코드). iOS 와 동일하게 원격이면 cacheDir 로 벌크 다운로드 후 로컬
        // 파일을 디코드한다. content:// · file:// · 로컬 절대경로는 그대로 로컬 디코드(다운로드 없음).
        val absolute = resolveAbsoluteAudioUrl(localPath)
        val isRemote = absolute.startsWith("http://") || absolute.startsWith("https://")
        val tempFile: File? = if (isRemote) {
            downloadToCache(absolute) ?: run {
                println("[WaveformExtractor] remote download failed: $absolute")
                return@withContext emptyList()
            }
        } else null
        val decodePath = tempFile?.absolutePath ?: absolute

        val peaks = try {
            runCatching { decodePeaks(decodePath, samples) }
                .getOrElse {
                    println("[WaveformExtractor] decode failed for $decodePath: ${it.message}")
                    emptyList()
                }
        } finally {
            // 파형 추출 후 임시 파일 즉시 회수 — cacheDir 누적 방지 (재생용 stem 캐시와 별개).
            tempFile?.let { runCatching { it.delete() } }
        }
        if (peaks.isNotEmpty()) {
            peaksCacheLock.withLock {
                if (peaksCache.size >= PEAKS_CACHE_MAX) {
                    peaksCache.keys.firstOrNull()?.let { peaksCache.remove(it) }
                }
                peaksCache[cacheKey] = peaks
            }
        }
        peaks
    }

/**
 * `/api/` 로 시작하는 BFF 서버 절대경로만 BFF base 를 prepend 해 절대 URL 로 보정 (iOS
 * [resolveAbsoluteAudioUrl] 과 동일 규칙). http(s) 는 그대로, content:// · file:// · 로컬 절대경로
 * (`/data`,`/storage`) 등은 미변경 → 로컬 디코드 경로로 흐른다.
 */
private fun resolveAbsoluteAudioUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (!url.startsWith("/api/")) return url
    val base = BuildConfig.BFF_BASE_URL.takeIf { it.isNotEmpty() } ?: return url
    return "${base.trimEnd('/')}/${url.trimStart('/')}"
}

/**
 * 원격 오디오를 cacheDir 임시 파일로 통째로 받아 File 반환 (iOS [downloadAudioToCache] 등가).
 * stem URL 은 인증 헤더가 필요 없어(iOS 도 plain download) 단순 스트림 복사. 실패 시 null.
 * Dispatchers.Default 워커에서 호출되므로 메인스레드 네트워크 예외 없음.
 */
private fun downloadToCache(url: String): File? = runCatching {
    val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase().ifEmpty { "audio" }
    val dest = File(cacheDirPath(), "waveform_${UUID.randomUUID()}.$ext")
    URL(url).openStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
    dest
}.getOrNull()

private fun decodePeaks(localPath: String, samples: Int): List<Float> {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
        extractor.setPlatformSource(localPath)
        val trackIndex = extractor.firstAudioTrackIndex() ?: return emptyList()
        extractor.selectTrack(trackIndex)
        val inFormat = extractor.getTrackFormat(trackIndex)
        val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return emptyList()

        var sampleRate = inFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, 44100)
        var channelCount = inFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
        val durationUs = inFormat.optLong(MediaFormat.KEY_DURATION, 0L)
        // KEY_DURATION 이 있으면 estimateFrames 추정 총 프레임으로 streaming bucketing
        // (iOS extractPeaksViaAssetReader 등가). 없으면 (durationUs<=0) 추정치가 samples 로 바닥나
        // globalFrame*samples/totalFrames == globalFrame → samples 번째 이후 frame 이 전부 마지막
        // bucket 으로 쏠린다. 이때는 채널0 PCM 전체를 모아 두고 종료 시 contiguous bucketSize=
        // frames/samples 로 나눈다 (iOS extractPeaksViaAudioFile 등가). 짧은 stem/BGM/오디오 클립
        // 가정 — 전체 frames in-memory.
        val hasDuration = durationUs > 0L
        // bucket 균등 분포용 추정 총 프레임수. 출력 포맷에서 sampleRate 가 확정되면 갱신.
        var totalFrames = estimateFrames(durationUs, sampleRate, samples)

        decoder = MediaCodec.createDecoderByType(mime).apply {
            configure(inFormat, null, null, 0)
            start()
        }

        val sumSq = DoubleArray(samples)
        val count = IntArray(samples)
        // duration 이 없으면 streaming bucketing 대신 채널0 frame 을 전부 모은다 (종료 시 일괄 bucketing).
        val collected = if (hasDuration) null else GrowableFloatArray()
        var globalFrame = 0L
        var pcmIsFloat = false
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)
                    val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                    if (size < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val out = decoder.outputFormat
                    sampleRate = out.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    channelCount = out.optInt(MediaFormat.KEY_CHANNEL_COUNT, channelCount).coerceAtLeast(1)
                    pcmIsFloat = out.optInt(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                        AudioFormat.ENCODING_PCM_FLOAT
                    totalFrames = estimateFrames(durationUs, sampleRate, samples)
                }

                outIdx >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (info.size > 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.order(ByteOrder.LITTLE_ENDIAN)
                            if (collected != null) {
                                collectChannel0(
                                    buf = outBuf,
                                    isFloat = pcmIsFloat,
                                    channels = channelCount,
                                    out = collected,
                                )
                            } else {
                                globalFrame = accumulateChannel0(
                                    buf = outBuf,
                                    isFloat = pcmIsFloat,
                                    channels = channelCount,
                                    samples = samples,
                                    totalFrames = totalFrames,
                                    startFrame = globalFrame,
                                    sumSq = sumSq,
                                    count = count,
                                )
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                }
                // INFO_TRY_AGAIN_LATER / INFO_OUTPUT_BUFFERS_CHANGED → 다음 루프
            }
        }

        return if (collected != null) {
            bucketContiguous(collected, samples)
        } else {
            (0 until samples).map { i ->
                val c = count[i]
                if (c <= 0) 0f else (sqrt(sumSq[i] / c).toFloat() / DISPLAY_REF).coerceIn(0f, 1f)
            }
        }
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching { extractor.release() }
    }
}

/** 인터리브 PCM 에서 채널0 만 골라 frame 단위 bucket 에 RMS(sum of squares + count) 누적. */
private fun accumulateChannel0(
    buf: ByteBuffer,
    isFloat: Boolean,
    channels: Int,
    samples: Int,
    totalFrames: Long,
    startFrame: Long,
    sumSq: DoubleArray,
    count: IntArray,
): Long {
    var globalFrame = startFrame
    if (isFloat) {
        val fb = buf.asFloatBuffer()
        val n = fb.remaining()
        var i = 0
        while (i < n) {
            val v = fb.get(i).toDouble()
            val bucket = ((globalFrame * samples) / totalFrames).toInt().coerceIn(0, samples - 1)
            sumSq[bucket] += v * v
            count[bucket]++
            globalFrame++
            i += channels
        }
    } else {
        val sb = buf.asShortBuffer()
        val n = sb.remaining()
        var i = 0
        while (i < n) {
            val v = sb.get(i) / 32768.0
            val bucket = ((globalFrame * samples) / totalFrames).toInt().coerceIn(0, samples - 1)
            sumSq[bucket] += v * v
            count[bucket]++
            globalFrame++
            i += channels
        }
    }
    return globalFrame
}

/** 인터리브 PCM 에서 채널0 만 골라 growable 버퍼에 frame 순서대로 누적 (duration 없는 경로). */
private fun collectChannel0(
    buf: ByteBuffer,
    isFloat: Boolean,
    channels: Int,
    out: GrowableFloatArray,
) {
    if (isFloat) {
        val fb = buf.asFloatBuffer()
        val n = fb.remaining()
        var i = 0
        while (i < n) {
            out.add(fb.get(i))
            i += channels
        }
    } else {
        val sb = buf.asShortBuffer()
        val n = sb.remaining()
        var i = 0
        while (i < n) {
            // short → -1..1 정규화 후 Float 저장. iOS 채널0 Float32 와 동일 정밀도(Float 저장→bucketing 시 toDouble).
            out.add((sb.get(i) / 32768.0).toFloat())
            i += channels
        }
    }
}

/**
 * 모은 채널0 frame 을 contiguous bucketSize=frames/samples 로 나눠 bucket 별 RMS → /DISPLAY_REF 스케일.
 * iOS [extractPeaksViaAudioFile] + scaleForDisplay 와 동일한 알고리즘 (frames<samples 면 결과 길이가
 * samples 보다 짧을 수 있음 — iOS 와 일치).
 */
private fun bucketContiguous(frames: GrowableFloatArray, samples: Int): List<Float> {
    val frameCount = frames.size
    if (frameCount <= 0) return emptyList()
    val bucketSize = (frameCount / samples).coerceAtLeast(1)
    val out = ArrayList<Float>(samples)
    var i = 0
    while (i < frameCount && out.size < samples) {
        val end = (i + bucketSize).coerceAtMost(frameCount)
        var sumSq = 0.0
        var j = i
        while (j < end) {
            val s = frames[j].toDouble()
            sumSq += s * s
            j++
        }
        val rms = sqrt(sumSq / (end - i)).toFloat()
        out.add((rms / DISPLAY_REF).coerceIn(0f, 1f))
        i = end
    }
    return out
}

/** 박싱 없이 frame 을 누적하는 growable FloatArray. 2배씩 증가. */
private class GrowableFloatArray(initialCapacity: Int = 1 shl 16) {
    private var data = FloatArray(initialCapacity.coerceAtLeast(16))
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(index: Int): Float = data[index]
}

private fun estimateFrames(durationUs: Long, sampleRate: Int, samples: Int): Long =
    ((durationUs / 1_000_000.0) * sampleRate).toLong().coerceAtLeast(samples.toLong())
