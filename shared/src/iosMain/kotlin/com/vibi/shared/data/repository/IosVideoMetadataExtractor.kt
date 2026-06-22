package com.vibi.shared.data.repository

import com.vibi.shared.domain.model.VideoInfo
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.platform.resolveStoredUriToFileUrl
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.timeRange
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.Foundation.NSFileManager

class IosVideoMetadataExtractor : VideoMetadataExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(uri: String): VideoInfo? = withContext(Dispatchers.Default) {
        // resolver / AVURLAsset / NSFileManager 모두 동기 디스크 호출 — caller dispatcher (보통 Main)
        // 에 머물면 UI 프레임 dropped. Default 로 분리.
        val url = resolveStoredUriToFileUrl(uri) ?: run {
            println("[Extractor] resolver returned null for uri=$uri")
            return@withContext null
        }
        // precise timing 은 sample/timing 테이블 전체 스캔(O(샘플수)=영상 길이 비례)을 강제해 긴 영상에서
        // 분리 확인 팝업이 뜨기까지 지연을 키운다. 여기 추출 결과는 (1) 검증(durationMs>300s, dim>1920 —
        // 초/정수픽셀 단위 coarse 게이트)과 (2) yes/no 팝업에만 쓰여 frame-accurate 가 필요 없고, 분리 range
        // 는 서버 actualDurationMs 를 우선하므로 container 의 mvhd duration(O(1))으로 충분하다. false.
        val asset = AVURLAsset(
            uRL = url,
            options = mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to false)
        )

        // iCloud-only / 대용량 영상은 async load 가 수백 ms 걸릴 수 있어 너무 짧으면 incomplete
        // asset 으로 진행돼 tracks().firstOrNull() 가 null → "메타데이터 못 읽음" 분기. 2000ms 유지.
        withTimeoutOrNull(2000) {
            suspendCancellableCoroutine<Unit> { cont ->
                asset.loadValuesAsynchronouslyForKeys(
                    keys = listOf("duration", "tracks")
                ) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }

        val tracks = asset.tracksWithMediaType(AVMediaTypeVideo)
        val videoTrack = tracks.firstOrNull() as? AVAssetTrack ?: run {
            println("[Extractor] no video track in $uri")
            return@withContext null
        }

        // duration: asset.duration 우선, 0 이면 track timeRange 로 fallback
        var durationSec = CMTimeGetSeconds(asset.duration)
        if (durationSec.isNaN() || durationSec <= 0.0) {
            val end = CMTimeRangeGetEnd(videoTrack.timeRange)
            durationSec = CMTimeGetSeconds(end)
        }
        if (durationSec.isNaN() || durationSec <= 0.0) {
            println("[Extractor] duration invalid for $uri")
            return@withContext null
        }
        val durationMs = (durationSec * 1000.0).toLong()

        val (width, height) = videoTrack.naturalSize.useContents { Pair(width.toInt(), height.toInt()) }

        val fileName = (url.lastPathComponent ?: "video.mp4")
        val fileSize = runCatching {
            val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(url.path ?: "", null)
            (attrs?.get(platform.Foundation.NSFileSize) as? Number)?.toLong() ?: 0L
        }.getOrDefault(0L)

        VideoInfo(
            uri = uri,
            fileName = fileName,
            mimeType = "video/mp4",
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = fileSize
        )
    }
}
