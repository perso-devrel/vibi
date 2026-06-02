package com.vibi.shared.domain.usecase.export

/**
 * 영상 세그먼트만 온디바이스(HW) 인코딩하는 인코더. 플랫폼 구현이 AVFoundation/MediaCodec 등으로
 * concat·trim·속도·볼륨·회전을 합성 후 HW 인코딩한다. 영상 외 차원(BGM·음원분리·frame)은 다루지
 * 않는다 — 그 판정은 [canEncodeOnDevice] 가 담당하고, 불가 케이스는 [HybridRenderExecutor] 가
 * 서버로 보낸다.
 *
 * 성공 시 출력 mp4 의 로컬 경로를 담은 Result. 취소는 [kotlinx.coroutines.CancellationException] 으로
 * 전파(rethrow)되어야 하며 Result.failure 로 감싸지 않는다.
 */
interface OnDeviceVideoEncoder {
    suspend fun export(
        segments: List<SegmentInput>,
        outputPath: String,
        onProgress: (percent: Int) -> Unit,
    ): Result<String>
}

/**
 * 온디바이스 인코딩으로 **서버와 동일한 출력**을 보장할 수 있는 편집 부분집합인지 판정.
 *
 * 영상 concat·trim·속도·per-segment 볼륨·회전만 composition + HW export 로 충실히 재현 가능하다.
 * 다음은 모두 서버(ffmpeg) 경로로 보낸다:
 *  - **BGM 믹스 / 음원분리** — 오디오 다중 트랙 amix·stem 다운로드는 서버 파이프라인 전용.
 *  - **실제 reframe** — 소스와 다른 치수로의 리사이즈/배경 letterbox. (frame 이 소스 치수 그대로면
 *    no-op 이라 통과 — 프로젝트 생성 시 frame 은 항상 videoInfo 치수로 설정되므로 이 passthrough
 *    구분이 없으면 온디바이스 경로가 절대 발동하지 않는다.)
 *  - **다중 소스 concat** — 혼합 해상도/회전 트랙은 AVMutableVideoComposition 정밀 매핑이 필요해
 *    v1 에선 제외(단일 소스의 trim/속도/분할/재정렬/볼륨이 압도적 다수 케이스라 먼저 커버).
 *
 * 무편집 단일 segment 는 상류 [com.vibi.shared.domain.usecase.save.SaveAllVariantsUseCase] 의 bypass 가
 * 이미 처리하므로 여기로 오지 않는다.
 */
internal fun canEncodeOnDevice(
    segments: List<SegmentInput>,
    frame: FrameInput?,
    bgmClips: List<BgmClipMixInput>,
    separationDirectives: List<SeparationDirectiveInput>,
): Boolean {
    if (bgmClips.isNotEmpty()) return false
    if (separationDirectives.isNotEmpty()) return false
    if (segments.isEmpty()) return false
    // 단일 소스만 — 혼합 해상도/transform concat 회피(위 KDoc 참조).
    if (segments.map { it.sourceFilePath }.distinct().size != 1) return false
    // frame 이 소스 치수와 다르면(실제 리사이즈/letterbox) 서버. 같으면 no-op 이라 온디바이스 OK —
    // 단일 소스라 모든 세그먼트 치수가 동일하므로 첫 세그먼트와 비교.
    if (frame != null) {
        val s = segments.first()
        if (frame.width != s.width || frame.height != s.height) return false
    }
    return true
}

/**
 * 온디바이스 fast-path + 서버 fallback 을 합친 [FfmpegExecutor].
 *
 * [canEncodeOnDevice] 가 참이고 [onDevice] 인코더가 있으면 온디바이스로 인코딩한다(네트워크 왕복 2회 +
 * 서버 인코딩 전부 제거). 온디바이스가 **실패**하면 회귀 방지를 위해 그대로 서버로 fallback 한다 —
 * 출력은 동일 편집의 순수 함수라 어느 경로로 만들어도 결과가 같아야 하며, 둘 다 실패해야 비로소 실패다.
 *
 * Android 등 온디바이스 인코더가 없는 플랫폼은 [onDevice] = null 로 주입해 항상 [fallback] 을 쓴다.
 */
class HybridRenderExecutor(
    private val onDevice: OnDeviceVideoEncoder?,
    private val fallback: FfmpegExecutor,
) : FfmpegExecutor {
    override suspend fun renderProject(
        segments: List<SegmentInput>,
        outputPath: String,
        frame: FrameInput?,
        bgmClips: List<BgmClipMixInput>,
        separationDirectives: List<SeparationDirectiveInput>,
        onProgress: (percent: Int) -> Unit,
    ): Result<String> {
        if (onDevice != null && canEncodeOnDevice(segments, frame, bgmClips, separationDirectives)) {
            // 취소는 export 내부에서 CancellationException 으로 rethrow → 여기로 전파(fallback 안 함).
            val result = onDevice.export(segments, outputPath, onProgress)
            if (result.isSuccess) return result
            // 온디바이스 인코딩 실패(코덱/디스크/미지원 입력 등) → 서버로 재시도. progress 는 0 부터 다시.
        }
        return fallback.renderProject(
            segments = segments,
            outputPath = outputPath,
            frame = frame,
            bgmClips = bgmClips,
            separationDirectives = separationDirectives,
            onProgress = onProgress,
        )
    }
}
