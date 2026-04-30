package com.dubcast.shared.ui.export

import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SeparationDirective
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.TextOverlay

/**
 * 플랫폼별 export 실행 의존성을 한 곳으로 모은 어댑터.
 *
 * commonMain 의 ViewModel 이 절대 Android Context · Uri · File · assets · ffmpeg-kit
 * 같은 플랫폼 API 를 직접 참조하지 않도록 하기 위한 경계. 각 플랫폼의 actual 구현은:
 * - Android: cacheDir + contentResolver + ffmpeg-kit
 * - iOS: NSTemporaryDirectory + AVFoundation 또는 BFF 위임 (별도 결정)
 *
 * shared/cmp 은 이 인터페이스 시그니처만 안다.
 */
interface ExportPlatformAdapter {

    /**
     * 본 ViewModel 이 요청한 export 를 플랫폼 측에서 실행한다.
     *
     * @return 성공 시 갤러리 저장 가능한 절대경로(또는 URI), 실패 시 Result.failure
     */
    suspend fun executeExport(
        request: ExportRequest,
        onProgress: (percent: Int) -> Unit
    ): Result<String>
}

data class ExportRequest(
    val projectId: String,
    /** 결과 영상의 언어 코드. "" 또는 "original" 이면 원본 (audioOverride 없음). */
    val outputLanguageCode: String,
    val segments: List<Segment>,
    val dubClips: List<DubClip>,
    val subtitleClips: List<SubtitleClip>,
    val imageClips: List<ImageClip>,
    val textOverlays: List<TextOverlay>,
    val bgmClips: List<BgmClip>,
    /** my_plan: 모든 결과 영상에 동일하게 적용되는 음성분리 명세. */
    val separationDirectives: List<SeparationDirective>,
    val frameWidth: Int,
    val frameHeight: Int,
    val backgroundColorHex: String,
    val audioOverridePath: String?,
    val burnSubtitles: Boolean,
    /**
     * Multi-variant export 시 ExportViewModel 이 한 번 [BffApi.uploadRenderInputs] 로
     * video + dub audios 를 캐시 업로드하고 받은 inputId. non-null 이면 RemoteRenderExecutor 가
     * video/audio multipart 를 생략하고 BFF 캐시 재사용. variant 1개면 null.
     */
    val preUploadedInputId: String? = null,
)
