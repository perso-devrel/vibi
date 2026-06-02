package com.vibi.shared.platform

/**
 * 온디바이스 영상 export 의 Swift 브리지 인터페이스 (`:shared` iosMain 에 정의, iosApp 의
 * `OnDeviceVideoExportBridgeImpl` 가 구현).
 *
 * **Swift 로 미는 이유**: K/N iOS klib 에 `AVMutableAudioMix.inputParameters` 등 오디오 setter
 * 다수가 미노출이라(ios-kn-patterns skill 참조) per-segment 볼륨 믹스·속도 피치 보정을 Kotlin 에서
 * 구성할 수 없다. Swift 는 AVFoundation 전체 API 를 자유롭게 써서 AVMutableComposition +
 * AVMutableAudioMix + AVAssetExportSession(VideoToolbox HW 인코딩)으로 충실히 합성한다.
 *
 * 콜백 기반(IapBridge 동일 패턴) — [com.vibi.shared.platform.IosOnDeviceVideoEncoder] 가 suspend 로 wrap.
 */
interface OnDeviceVideoExportBridge {
    /**
     * [segments] 를 order 순으로 concat(각 trim/속도/볼륨 적용, 첫 트랙 회전 보존)하여 [outputPath]
     * mp4 로 인코딩한다.
     *
     * @param onProgress 0..100 진행률.
     * @param onComplete (outputPath, errorMessage) — 성공 시 outputPath(non-null)·error=null,
     *   실패 시 outputPath=null·error(non-null).
     * @return 진행 중 취소용 핸들.
     */
    fun export(
        segments: List<OnDeviceSegmentSpec>,
        outputPath: String,
        onProgress: (percent: Int) -> Unit,
        onComplete: (outputPath: String?, errorMessage: String?) -> Unit,
    ): OnDeviceExportCancellable
}

/** 진행 중 export 취소 핸들 — suspend wrapper 의 invokeOnCancellation 에서 호출. */
interface OnDeviceExportCancellable {
    fun cancel()
}

/**
 * Swift 로 넘기는 세그먼트 명세. [com.vibi.shared.domain.usecase.export.SegmentInput] 중 영상 합성에
 * 필요한 필드만 추려 K/N↔Swift 경계를 단순화(원본 파일 경로는 이미 로컬로 해소된 상태).
 */
data class OnDeviceSegmentSpec(
    val sourceFilePath: String,
    val order: Int,
    /** 소스 내부 trim 시작 ms. */
    val trimStartMs: Long,
    /** 소스 내부 trim 끝 ms (effective — 0 sentinel 은 상위에서 이미 durationMs 로 치환). */
    val trimEndMs: Long,
    /** 1.0 = 정상. AVAssetExportSession.audioTimePitchAlgorithm 로 피치 보존(ffmpeg atempo 파리티). */
    val speedScale: Float,
    val volumeScale: Float,
)
