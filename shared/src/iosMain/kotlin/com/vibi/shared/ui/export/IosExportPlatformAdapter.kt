@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.vibi.shared.ui.export

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.usecase.export.BgmClipMixInput
import com.vibi.shared.domain.usecase.export.FfmpegExecutor
import com.vibi.shared.domain.usecase.export.FrameInput
import com.vibi.shared.domain.usecase.export.SegmentInput
import com.vibi.shared.domain.usecase.export.toExportInput
import com.vibi.shared.platform.resolveStoredUriToPath
import kotlin.uuid.Uuid
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

/**
 * iOS Export 어댑터 — BFF [FfmpegExecutor] (= RemoteRenderExecutor) 에 직접 위임.
 *
 * 에러 정책:
 *  - BGM 등 path 해소 실패 → fail-loud (silent drop 금지) 지만 raw URI 노출 안 하고
 *    파일명만 surface. 여러 BGM 실패 시 일괄 수집해서 한 번에 throw.
 *  - CancellationException 은 그대로 rethrow (구조적 동시성 보존).
 */
class IosExportPlatformAdapter(
    private val executor: FfmpegExecutor,
) : ExportPlatformAdapter {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun executeExport(
        request: ExportRequest,
        onProgress: (percent: Int) -> Unit
    ): Result<String> {
        // cacheDir 충돌 (같은 ms 내 동시 export) 방지 위해 UUID 포함.
        val tmpRoot = NSTemporaryDirectory()
        val cacheDir = "$tmpRoot/vibi_export_${Uuid.random()}"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = cacheDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return try {
            val segmentInputs = request.segments.map { segment ->
                val localPath = resolveSegmentSource(segment, cacheDir)
                    ?: error("Segment source unreadable: ${segment.sourceUri.fileNameOnly()}")
                segment.toInput(localPath)
            }

            val outputPath = "$cacheDir/export.mp4"

            val frame = if (request.frameWidth > 0 && request.frameHeight > 0) {
                FrameInput(
                    width = request.frameWidth,
                    height = request.frameHeight,
                    backgroundColorHex = request.backgroundColorHex
                )
            } else null

            // BGM 실패는 일괄 수집 — 사용자가 retry 사이클을 N번 돌지 않게.
            val bgmInputs = mutableListOf<BgmClipMixInput>()
            val bgmFailures = mutableListOf<String>()
            for (clip in request.bgmClips) {
                val localPath = copyUriToCache(clip.sourceUri, cacheDir, prefix = "bgm")
                if (localPath == null) {
                    bgmFailures += clip.sourceUri.fileNameOnly()
                    continue
                }
                bgmInputs += BgmClipMixInput(
                    audioFilePath = localPath,
                    startMs = clip.startMs,
                    volume = clip.volumeScale,
                    speed = clip.speedScale,
                    sourceTrimStartMs = clip.sourceTrimStartMs,
                    sourceTrimEndMs = clip.sourceTrimEndMs,
                )
            }
            if (bgmFailures.isNotEmpty()) {
                error("BGM unreadable: ${bgmFailures.joinToString(", ")}")
            }

            // directive 의 stem tempo = 앵커된 세그먼트의 speedScale. 세그먼트가 단일 진실원천이라
            // directive 는 speed 를 저장 안 하고 여기서 resolve 해 주입한다. 미앵커(legacy, segmentId
            // 빔)거나 세그먼트 부재면 1.0 (원본 tempo).
            val speedBySegmentId = request.segments.associate { it.id to it.speedScale }
            val directives = request.separationDirectives.mapNotNull { d ->
                val speed = speedBySegmentId[d.segmentId]?.takeIf { it > 0f } ?: 1f
                d.toExportInput(appliedSpeedScale = speed)
            }

            val outcome = executor.renderProject(
                segments = segmentInputs,
                outputPath = outputPath,
                frame = frame,
                bgmClips = bgmInputs,
                separationDirectives = directives,
                onProgress = onProgress,
            )
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            // 작업 디렉터리(복사된 원본/BGM 입력본)는 export 산출물(Caches 에 별도 저장)과 무관하므로
            // 성공/실패/취소 모두 정리. NSTemporaryDirectory 는 OS 가 즉시 비우지 않아 풀사이즈 영상
            // 사본이 반복 저장마다 누적 → 디스크가 차면 이후 export 가 ENOSPC 로 실패.
            NSFileManager.defaultManager.removeItemAtPath(cacheDir, null)
        }
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** URI 의 마지막 path segment만 추출 (raw URI / 토큰 / 식별자 metadata leak 방지). */
    private fun String.fileNameOnly(): String =
        substringAfterLast('/').take(64).ifBlank { "(unknown)" }

    private fun Segment.toInput(localPath: String) = SegmentInput(
        sourceFilePath = localPath,
        type = type,
        order = order,
        durationMs = durationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        width = width,
        height = height,
        volumeScale = volumeScale,
        speedScale = speedScale
    )
}
