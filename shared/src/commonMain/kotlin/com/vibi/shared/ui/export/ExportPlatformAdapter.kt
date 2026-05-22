package com.vibi.shared.ui.export

import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SeparationDirective

/**
 * 플랫폼별 export 실행 의존성을 한 곳으로 모은 어댑터.
 *
 * commonMain 의 ViewModel 이 절대 Android Context · Uri · File · assets · ffmpeg-kit
 * 같은 플랫폼 API 를 직접 참조하지 않도록 하기 위한 경계.
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
    val segments: List<Segment>,
    val bgmClips: List<BgmClip>,
    val separationDirectives: List<SeparationDirective>,
    val frameWidth: Int,
    val frameHeight: Int,
    val backgroundColorHex: String,
    val preUploadedInputId: String? = null,
)
