@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dubcast.shared.ui.export

import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.usecase.export.ExportWithDubbingUseCase
import com.dubcast.shared.domain.usecase.export.FrameInput
import com.dubcast.shared.domain.usecase.export.SegmentInput
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
 * iOS Export 어댑터 — Android 와 동등하게 [ExportWithDubbingUseCase] 에 위임.
 *
 * 실제 ffmpeg 합성은 BFF `/api/v2/render` 가 처리. 본 어댑터는 임시 디렉터리 생성과
 * 출력 경로 발급만 담당. iOS-전용 ffmpeg 자체 합성(AVFoundation)으로 가는 것은
 * 별도 결정 사항 (현재는 BFF 위임 통일).
 */
class IosExportPlatformAdapter(
    private val exportWithDubbing: ExportWithDubbingUseCase
) : ExportPlatformAdapter {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun executeExport(
        request: ExportRequest,
        onProgress: (percent: Int) -> Unit
    ): Result<String> = runCatching {
        // iOS 의 임시 디렉터리. 앱 종료 시 자동 정리.
        val tmpRoot = NSTemporaryDirectory()
        val cacheDir = "$tmpRoot/dubcast_export_${Clock.System.now().toEpochMilliseconds()}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = cacheDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        // segment 의 sourceUri 가 file:// 또는 절대 경로 — content URI 개념이 iOS 엔 없으므로 그대로 사용.
        // PHPickerViewController 가 반환한 file:// URI 도 [NSData.dataWithContentsOfURL] 로 읽힘.
        val segmentInputs = request.segments.map { segment ->
            val localPath = resolveSegmentSource(segment, cacheDir)
                ?: error("Failed to read segment source: ${segment.sourceUri}")
            segment.toInput(localPath)
        }

        val outputPath = "$cacheDir/export_${Clock.System.now().toEpochMilliseconds()}.mp4"

        val needsAss = request.subtitleClips.isNotEmpty() || request.textOverlays.isNotEmpty()
        val assFilePath = if (needsAss) "$cacheDir/subtitles_${request.projectId}.ass" else null

        // iOS 는 자막 burn-in 시 폰트 디렉터리 별도 번들링 필요. 현재는 BFF 가 자체 폰트 사용 가정.
        val fontDir: String? = null

        val frame = if (request.frameWidth > 0 && request.frameHeight > 0) {
            FrameInput(
                width = request.frameWidth,
                height = request.frameHeight,
                backgroundColorHex = request.backgroundColorHex
            )
        } else null

        val audioOverride = request.audioOverridePath?.takeIf { fileExists(it) }

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
            resolveImagePath = { uri -> copyUriToCache(uri, cacheDir, prefix = "image") },
            resolveAudioPath = { uri -> copyUriToCache(uri, cacheDir, prefix = "bgm") },
            onProgress = onProgress
        )

        result.getOrThrow()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun resolveSegmentSource(segment: Segment, cacheDir: String): String? {
        val uri = segment.sourceUri
        return if (uri.startsWith("file://") || uri.startsWith("/")) {
            // PHPicker 가 반환한 file:// URI 또는 절대 경로 — 그대로 사용.
            uri.removePrefix("file://")
        } else {
            copyUriToCache(uri, cacheDir, prefix = "seg")
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private suspend fun copyUriToCache(
        uri: String,
        cacheDir: String,
        prefix: String
    ): String? = withContext(Dispatchers.Default) {
        try {
            // 이미 절대 경로면 복사 없이 반환.
            if (uri.startsWith("/")) return@withContext uri
            if (uri.startsWith("file://")) return@withContext uri.removePrefix("file://")

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

    private fun fileExists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

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
