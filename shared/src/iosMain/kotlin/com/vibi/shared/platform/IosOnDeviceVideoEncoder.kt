package com.vibi.shared.platform

import com.vibi.shared.domain.usecase.export.OnDeviceVideoEncoder
import com.vibi.shared.domain.usecase.export.SegmentInput
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [OnDeviceVideoEncoder] 의 iOS 구현 — 콜백 기반 [OnDeviceVideoExportBridge] (Swift) 를 suspend 로 wrap.
 *
 * [SegmentInput] → [OnDeviceSegmentSpec] 매핑 시 order 정렬 + effectiveTrimEndMs(0 sentinel → durationMs)
 * 를 미리 적용해 Swift 가 sentinel 을 몰라도 되게 한다. 취소 시 invokeOnCancellation 으로 진행 중
 * AVAssetExportSession 을 cancel.
 */
class IosOnDeviceVideoEncoder(
    private val bridge: OnDeviceVideoExportBridge,
) : OnDeviceVideoEncoder {

    override suspend fun export(
        segments: List<SegmentInput>,
        outputPath: String,
        onProgress: (percent: Int) -> Unit,
    ): Result<String> = suspendCancellableCoroutine { cont ->
        val specs = segments.sortedBy { it.order }.map { s ->
            OnDeviceSegmentSpec(
                sourceFilePath = s.sourceFilePath,
                order = s.order,
                trimStartMs = s.trimStartMs,
                trimEndMs = s.effectiveTrimEndMs,
                speedScale = s.speedScale,
                volumeScale = s.volumeScale,
            )
        }
        val cancellable = bridge.export(
            segments = specs,
            outputPath = outputPath,
            onProgress = { p -> onProgress(p) },
            onComplete = { path, error ->
                if (cont.isActive) {
                    // resume(value){} 의 빈 onCancellation 람다는 K/N coroutines 1.9.0 인식 한계 회피
                    // (VideoPlayer.ios.kt buildCompositionPlayer 와 동일 사유).
                    if (path != null) {
                        cont.resume(Result.success(path)) {}
                    } else {
                        cont.resume(
                            Result.failure(RuntimeException(error ?: "on-device export failed")),
                        ) {}
                    }
                }
            },
        )
        cont.invokeOnCancellation { cancellable.cancel() }
    }
}
