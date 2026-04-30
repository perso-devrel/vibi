@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dubcast.shared.ui.export

import android.content.Context
import android.net.Uri
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.usecase.export.ExportWithDubbingUseCase
import com.dubcast.shared.domain.usecase.export.FrameInput
import com.dubcast.shared.domain.usecase.export.SegmentInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import java.io.File
import java.util.UUID

/**
 * Android 구현 — legacy [com.example.dubcast.ui.export.ExportViewModel.startExport] 의
 * 파일시스템·assets·content URI 처리 부분을 그대로 이전.
 *
 * 실제 ffmpeg 실행은 [ExportWithDubbingUseCase] 에 위임 (commonMain 에서 KSP 로 주입된 FfmpegExecutor 사용 — 현재는 BFF 위임 [RemoteRenderExecutor]).
 */
class AndroidExportPlatformAdapter(
    private val context: Context,
    private val exportWithDubbing: ExportWithDubbingUseCase
) : ExportPlatformAdapter {

    override suspend fun executeExport(
        request: ExportRequest,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = runCatching {
        val cacheDir = context.cacheDir

        // Segment 의 content:// URI 를 로컬 cache 경로로 복사 (ffmpeg 가 직접 읽을 수 있어야 함).
        val segmentInputs = request.segments.map { segment ->
            val localPath = resolveSegmentSource(segment, cacheDir)
                ?: error("Failed to read segment source: ${segment.sourceUri}")
            segment.toInput(localPath)
        }

        val outputPath = File(cacheDir, "export_${Clock.System.now().toEpochMilliseconds()}.mp4")
            .absolutePath

        val needsAss = request.subtitleClips.isNotEmpty() || request.textOverlays.isNotEmpty()
        val assFilePath = if (needsAss) {
            File(cacheDir, "subtitles_${request.projectId}.ass").absolutePath
        } else null

        val fontDir = if (needsAss) {
            File(cacheDir, "fonts").apply { mkdirs() }.absolutePath.also {
                copyFontFromAssets(it)
            }
        } else null

        val frame = if (request.frameWidth > 0 && request.frameHeight > 0) {
            FrameInput(
                width = request.frameWidth,
                height = request.frameHeight,
                backgroundColorHex = request.backgroundColorHex
            )
        } else null

        val audioOverride = request.audioOverridePath?.takeIf { File(it).exists() }

        val result = exportWithDubbing.execute(
            segments = segmentInputs,
            dubClips = request.dubClips,
            subtitleClips = request.subtitleClips,
            outputPath = outputPath,
            assFilePath = assFilePath,
            fontDir = fontDir,
            frame = frame,
            imageClips = request.imageClips,
            textOverlays = request.textOverlays,
            bgmClips = request.bgmClips,
            audioOverridePath = audioOverride,
            separationDirectives = request.separationDirectives,
            preUploadedInputId = request.preUploadedInputId,
            resolveImagePath = { uri -> copyContentUriToCache(uri, cacheDir, prefix = "image") },
            resolveAudioPath = { uri -> copyContentUriToCache(uri, cacheDir, prefix = "bgm") },
            onProgress = onProgress
        )

        result.getOrThrow()
    }

    private suspend fun resolveSegmentSource(segment: Segment, cacheDir: File): String? {
        val uri = segment.sourceUri
        return if (uri.startsWith("content://")) {
            val prefix = if (segment.type == SegmentType.VIDEO) "seg_video" else "seg_image"
            copyContentUriToCache(uri, cacheDir, prefix = prefix)
        } else {
            uri
        }
    }

    private suspend fun copyContentUriToCache(
        uri: String,
        cacheDir: File,
        prefix: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!uri.startsWith("content://")) return@withContext uri
            val safeName = "${prefix}_${UUID.nameUUIDFromBytes(uri.toByteArray())}.bin"
            val dest = File(cacheDir, safeName)
            if (!dest.exists()) {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
            }
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun copyFontFromAssets(fontDir: String) {
        try {
            val fontFile = File(fontDir, "NotoSansKR-Regular.otf")
            if (!fontFile.exists()) {
                context.assets.open("fonts/NotoSansKR-Regular.otf").use { input ->
                    fontFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Exception) {
            // Font not bundled — ffmpeg fallback
        }
    }

    private fun Segment.toInput(localPath: String) = SegmentInput(
        sourceFilePath = localPath,
        type = type,
        order = order,
        durationMs = durationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        width = width,
        height = height,
        imageXPct = imageXPct,
        imageYPct = imageYPct,
        imageWidthPct = imageWidthPct,
        imageHeightPct = imageHeightPct,
        volumeScale = volumeScale,
        speedScale = speedScale
    )
}
