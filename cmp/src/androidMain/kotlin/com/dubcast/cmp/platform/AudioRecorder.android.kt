package com.dubcast.cmp.platform

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
import com.dubcast.shared.platform.currentTimeMillis
import java.io.File

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
                val amp = runCatching { rec.maxAmplitude }.getOrDefault(0)
                if (amp <= 0) return 0f
                val norm = (kotlin.math.log10(amp.toFloat()) - 1.5f) / 3.0f
                return norm.coerceIn(0f, 1f)
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
                runCatching { rec.stop() }
                runCatching { rec.release() }
                recorder = null
                recording = false
                if (path != null) onRecorded(path, durationMs)
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
