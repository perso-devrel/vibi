package com.vibi.cmp.platform

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vibi.shared.platform.currentTimeMillis
import java.io.File

/**
 * WaveformExtractor.scaleForDisplay 와 동일 ref. live recorder amplitude 를 같은 ref 로 정규화해야
 * post-recording WaveformPlayBar 와 막대 분포가 일치.
 */
private const val WAVEFORM_DISPLAY_REF = 0.4f

/** peak amplitude → RMS 근사. 일반 voice 의 peak/RMS 비. */
private const val PEAK_TO_RMS = 3f

/**
 * Android: MediaRecorder + RECORD_AUDIO 권한. AAC m4a 로 cacheDir 에 저장.
 */
@Composable
actual fun rememberAudioRecorder(
    onRecorded: (uri: String, durationMs: Long) -> Unit,
    onError: (message: String) -> Unit,
): AudioRecorderController {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputPath by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableStateOf(0L) }
    var pendingStart by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingStart) {
            pendingStart = false
            val ok = startRecording(
                context = context,
                onStart = { rec, path, ts ->
                    recorder = rec
                    outputPath = path
                    startedAt = ts
                    recording = true
                },
                onError = onError,
            )
            if (!ok) onError("녹음 시작 실패")
        } else if (!granted) {
            pendingStart = false
            onError("마이크 권한이 필요합니다")
        }
    }

    return remember {
        object : AudioRecorderController {
            override val isRecording: Boolean get() = recording
            override val currentLevel: Float get() {
                val rec = recorder ?: return 0f
                if (!recording) return 0f
                // maxAmplitude 는 16-bit PCM peak (0..32767) — 직전 호출 이후의 max.
                val amp = runCatching { rec.maxAmplitude }.getOrDefault(0)
                if (amp <= 0) return 0f
                // peak → linear (0..1). 일반 음성 peak 는 RMS 의 ~3x 라 PEAK_TO_RMS 로 보정 후
                // WaveformExtractor.scaleForDisplay 와 동일한 ref(0.4) 로 정규화 → 같은 분포.
                // (iOS extractAudioPeaks 와 표시 ref 를 통일.)
                val linearPeak = amp.toFloat() / 32767f
                val approxRms = linearPeak / PEAK_TO_RMS
                return (approxRms / WAVEFORM_DISPLAY_REF).coerceIn(0f, 1f)
            }

            override fun start() {
                if (recording) return
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    pendingStart = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return
                }
                val ok = startRecording(
                    context = context,
                    onStart = { rec, path, ts ->
                        recorder = rec
                        outputPath = path
                        startedAt = ts
                        recording = true
                    },
                    onError = onError,
                )
                if (!ok) onError("녹음 시작 실패")
            }

            override fun stop() {
                val rec = recorder ?: return
                val path = outputPath
                val durationMs = (currentTimeMillis() - startedAt).coerceAtLeast(0L)
                // rec.stop() 은 녹음이 너무 짧으면 (<~1s) IllegalStateException 을 던지고 파일이
                // 유효하지 않게 남는다. 성공했을 때만 onRecorded — 실패 시 깨진 파일 삭제 후 onError.
                val stopOk = runCatching { rec.stop() }.isSuccess
                runCatching { rec.release() }
                recorder = null
                recording = false
                if (stopOk && path != null) {
                    onRecorded(path, durationMs)
                } else {
                    path?.let { runCatching { File(it).delete() } }
                    onError("녹음이 너무 짧습니다")
                }
            }
        }
    }
}

/**
 * MediaRecorder 인스턴스 구성 + 시작. 콜백으로 recorder/path/시작 timestamp 를 host state 에 흘림.
 */
private fun startRecording(
    context: android.content.Context,
    onStart: (MediaRecorder, String, Long) -> Unit,
    onError: (String) -> Unit,
): Boolean {
    val name = "rec_${currentTimeMillis()}.m4a"
    val dest = File(context.cacheDir, "recordings").apply { mkdirs() }.resolve(name)
    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION") MediaRecorder()
    }
    return try {
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(128_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(dest.absolutePath)
        rec.prepare()
        rec.start()
        onStart(rec, dest.absolutePath, currentTimeMillis())
        true
    } catch (e: Exception) {
        runCatching { rec.release() }
        onError(e.message ?: "녹음 prepare 실패")
        false
    }
}
