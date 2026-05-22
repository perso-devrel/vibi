@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.vibi.shared.ui.export

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.usecase.export.BgmClipMixInput
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.domain.usecase.export.FrameInput
import com.vibi.shared.domain.usecase.export.SegmentInput
import com.vibi.shared.domain.usecase.export.toExportInput
import com.vibi.shared.platform.resolveStoredUriToPath
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

/**
 * iOS Export 어댑터 — BFF [FfmpegExecutor] (= RemoteRenderExecutor) 에 직접 위임.
 * 자막/더빙 제거 후 어댑터가 segment/bgm/separation 변환만 담당.
 */
class IosExportPlatformAdapter(
    private val executor: FfmpegExecutor,
) : ExportPlatformAdapter {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun executeExport(
        request: ExportRequest,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = runCatching {
        val tmpRoot = NSTemporaryDirectory()
        val cacheDir = "$tmpRoot/vibi_export_${Clock.System.now().toEpochMilliseconds()}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = cacheDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        val segmentInputs = request.segments.map { segment ->
            val localPath = resolveSegmentSource(segment, cacheDir)
                ?: error("Failed to read segment source: ${segment.sourceUri}")
            segment.toInput(localPath)
        }

        val outputPath = "$cacheDir/export_${Clock.System.now().toEpochMilliseconds()}.mp4"

        val frame = if (request.frameWidth > 0 && request.frameHeight > 0) {
            FrameInput(
                width = request.frameWidth,
                height = request.frameHeight,
                backgroundColorHex = request.backgroundColorHex
            )
        } else null

        val bgmInputs = request.bgmClips.map { clip ->
            // BGM 은 사용자가 timeline 에 명시적으로 배치한 결과물이라 silent drop 금지.
            // 권한 만료 / HTTP 4xx / 사라진 content:// 등으로 path 해소 실패 시 fail-loud.
            val localPath = copyUriToCache(clip.sourceUri, cacheDir, prefix = "bgm")
                ?: error("BGM source unreadable: ${clip.sourceUri}")
            BgmClipMixInput(
                audioFilePath = localPath,
                startMs = clip.startMs,
                volume = clip.volumeScale,
                speed = clip.speedScale,
                sourceTrimStartMs = clip.sourceTrimStartMs,
                sourceTrimEndMs = clip.sourceTrimEndMs,
            )
        }

        val directives = request.separationDirectives.mapNotNull { it.toExportInput() }

        val result = executor.renderProject(
            segments = segmentInputs,
            outputPath = outputPath,
            frame = frame,
            bgmClips = bgmInputs,
            separationDirectives = directives,
            preUploadedInputId = request.preUploadedInputId,
            onProgress = onProgress,
        )
        result.getOrThrow()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun resolveSegmentSource(segment: Segment, cacheDir: String): String? {
        val uri = segment.sourceUri
        val resolved = resolveStoredUriToPath(uri)
        if (resolved != null && NSFileManager.defaultManager.fileExistsAtPath(resolved)) {
            return resolved
        }
        return copyUriToCache(uri, cacheDir, prefix = "seg")
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun copyUriToCache(
        uri: String,
        cacheDir: String,
        prefix: String
    ): String? = withContext(Dispatchers.Default) {
        try {
            if (uri.startsWith("/") || uri.startsWith("file://") ||
                !uri.contains("://")
            ) {
                val resolved = resolveStoredUriToPath(uri) ?: return@withContext null
                return@withContext resolved
            }

            val url = NSURL.URLWithString(uri) ?: return@withContext null
            val data: NSData = NSData.dataWithContentsOfURL(url) ?: return@withContext null
            val safeName = "${prefix}_${uri.hashCode().toUInt()}.bin"
            val dest = "$cacheDir/$safeName"
            data.writeToFile(dest, atomically = true)
            dest
        } catch (_: Exception) {
            null
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
