package com.example.dubcast.data.repository

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.dubcast.domain.usecase.export.DubClipMixInput
import com.example.dubcast.domain.usecase.export.FfmpegExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class FfmpegExecutorImpl @Inject constructor() : FfmpegExecutor {

    override suspend fun burnSubtitles(
        inputVideoPath: String,
        assFilePath: String,
        outputPath: String,
        fontDir: String,
        durationMs: Long,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = suspendCancellableCoroutine { continuation ->

        val escapedAssPath = escapeFilterPath(assFilePath)
        val vfFilter = if (fontDir.isNotEmpty()) {
            val escapedFontDir = escapeFilterPath(fontDir)
            "ass=$escapedAssPath:fontsdir=$escapedFontDir"
        } else {
            "ass=$escapedAssPath"
        }

        val command = "-i \"$inputVideoPath\" -vf \"$vfFilter\" -c:v mpeg4 -q:v 3 -c:a copy -y \"$outputPath\""

        val session = FFmpegKit.executeAsync(
            command,
            { session ->
                val returnCode = session.returnCode
                if (ReturnCode.isSuccess(returnCode)) {
                    onProgress(100)
                    continuation.resume(Result.success(outputPath))
                } else {
                    val logs = session.allLogsAsString ?: "Unknown error"
                    continuation.resume(Result.failure(RuntimeException("FFmpeg failed: $logs")))
                }
            },
            { /* log callback */ },
            { statistics ->
                if (durationMs > 0) {
                    val timeMs = statistics.time.toLong()
                    val percent = ((timeMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 99)
                    onProgress(percent)
                }
            }
        )

        continuation.invokeOnCancellation {
            session.cancel()
        }
    }

    override suspend fun mixAudioWithVideo(
        inputVideoPath: String,
        dubClips: List<DubClipMixInput>,
        outputPath: String,
        videoDurationMs: Long,
        assFilePath: String?,
        fontDir: String?,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = suspendCancellableCoroutine { continuation ->

        if (dubClips.isEmpty() && assFilePath == null) {
            val command = "-i \"$inputVideoPath\" -c copy -y \"$outputPath\""
            val session = FFmpegKit.executeAsync(
                command,
                { s -> handleCompletion(s, onProgress, continuation, outputPath) },
                { /* log */ },
                { stats -> handleProgress(stats, videoDurationMs, onProgress) }
            )
            continuation.invokeOnCancellation { session.cancel() }
            return@suspendCancellableCoroutine
        }

        val sb = StringBuilder()
        sb.append("-i \"$inputVideoPath\" ")
        for (clip in dubClips) {
            sb.append("-i \"${clip.audioFilePath}\" ")
        }

        val hasAudioMix = dubClips.isNotEmpty()
        val hasSubtitles = assFilePath != null

        if (hasAudioMix || hasSubtitles) {
            // Use filter_complex for everything to avoid -vf + -filter_complex conflict
            sb.append("-filter_complex \"")

            // Video chain: subtitle burn-in
            if (hasSubtitles) {
                val escapedAss = escapeFilterPath(assFilePath!!)
                val assFilter = if (fontDir != null) {
                    val escapedFont = escapeFilterPath(fontDir)
                    "ass=$escapedAss:fontsdir=$escapedFont"
                } else {
                    "ass=$escapedAss"
                }
                sb.append("[0:v]$assFilter[vout];")
            }

            // Audio chain: mix original + dub clips
            if (hasAudioMix) {
                val audioLabels = mutableListOf<String>()
                for ((index, clip) in dubClips.withIndex()) {
                    val inputIdx = index + 1
                    val label = "a$inputIdx"
                    sb.append("[${inputIdx}:a]adelay=${clip.startMs}|${clip.startMs},volume=${clip.volume}[$label];")
                    audioLabels.add("[$label]")
                }
                val totalInputs = dubClips.size + 1
                sb.append("[0:a]${audioLabels.joinToString("")}amix=inputs=$totalInputs:duration=first:dropout_transition=0:normalize=0[aout]")
            }

            // Remove trailing semicolon if present
            val filterStr = sb.toString().trimEnd(';')
            sb.clear()
            sb.append(filterStr)
            sb.append("\" ")

            // Map outputs
            val videoMap = if (hasSubtitles) "-map \"[vout]\"" else "-map 0:v"
            val audioMap = if (hasAudioMix) "-map \"[aout]\"" else "-map 0:a"
            val videoCodec = if (hasSubtitles) "-c:v mpeg4 -q:v 3" else "-c:v copy"
            val audioCodec = if (hasAudioMix) "-c:a aac -b:a 192k" else "-c:a copy"

            sb.append("$videoMap $audioMap $videoCodec $audioCodec ")
        }

        sb.append("-y \"$outputPath\"")

        val session = FFmpegKit.executeAsync(
            sb.toString(),
            { s -> handleCompletion(s, onProgress, continuation, outputPath) },
            { /* log */ },
            { stats -> handleProgress(stats, videoDurationMs, onProgress) }
        )
        continuation.invokeOnCancellation { session.cancel() }
    }

    private fun handleCompletion(
        session: com.arthenica.ffmpegkit.FFmpegSession,
        onProgress: (percent: Int) -> Unit,
        continuation: kotlinx.coroutines.CancellableContinuation<Result<String>>,
        outputPath: String
    ) {
        if (!continuation.isActive) return
        val returnCode = session.returnCode
        if (ReturnCode.isSuccess(returnCode)) {
            onProgress(100)
            continuation.resume(Result.success(outputPath))
        } else {
            val logs = session.allLogsAsString ?: "Unknown error"
            continuation.resume(Result.failure(RuntimeException("FFmpeg failed: $logs")))
        }
    }

    private fun handleProgress(
        statistics: com.arthenica.ffmpegkit.Statistics,
        durationMs: Long,
        onProgress: (percent: Int) -> Unit
    ) {
        if (durationMs > 0) {
            val timeMs = statistics.time.toLong()
            val percent = ((timeMs.toDouble() / durationMs) * 100).toInt().coerceIn(0, 99)
            onProgress(percent)
        }
    }

    private fun escapeFilterPath(path: String): String =
        path.replace("\\", "\\\\\\\\")
            .replace(":", "\\\\:")
            .replace("'", "\\\\'")
            .replace("[", "\\\\[")
            .replace("]", "\\\\]")
}
