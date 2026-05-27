package com.vibi.cmp.platform

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ExoPlayer 의 setVolume 은 0..1 하드 클램프 — >1.0 부스트 불가. 본 processor 가 PCM 16-bit sample
 * 을 직접 스케일링해 0..2 amplitude boost 를 구현. render path (ffmpeg `volume=2.0`) 와 동일 효과로
 * preview-render 대칭. gain == 1f 일 땐 pass-through (오버헤드 0).
 *
 * gain 은 @Volatile — UI thread (slider) 가 즉시 변경, audio thread (queueInput) 가 즉시 읽음.
 * sample 단위 atomicity 는 불필요 (값이 바뀌는 frame 경계에서 잠깐 섞여도 사람 귀엔 무의미).
 */
@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var gain: Float = 1f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        // ExoPlayer 의 DefaultAudioSink 는 보통 디코더 출력을 16-bit PCM 으로 통일. 그 외 포맷은
        // 부스트 불가하므로 UnhandledAudioFormatException 으로 chain 에서 자동 bypass.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val g = gain
        if (g == 1f) {
            // pass-through — 부스트 비활성 시 sample loop 비용 0.
            output.put(inputBuffer)
        } else {
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            output.order(ByteOrder.LITTLE_ENDIAN)
            while (inputBuffer.remaining() >= 2) {
                val sample = inputBuffer.short.toInt()
                val scaled = (sample * g).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(scaled.toShort())
            }
            // remaining 1 byte (있을 수 없지만 안전): drop.
        }
        output.flip()
    }
}
