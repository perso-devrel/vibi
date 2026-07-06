package com.vibi.shared.domain.usecase.input

import com.vibi.shared.domain.model.ValidationError
import com.vibi.shared.domain.model.ValidationResult
import com.vibi.shared.domain.model.VideoInfo

class ValidateVideoUseCase constructor() {

    companion object {
        private val SUPPORTED_MIME_TYPES = setOf(
            "video/mp4",
            "video/quicktime",
            "video/webm"
        )
        private const val MAX_DURATION_MS = 300_000L
    }

    operator fun invoke(videoInfo: VideoInfo): ValidationResult {
        if (videoInfo.mimeType !in SUPPORTED_MIME_TYPES) {
            return ValidationResult.Invalid(ValidationError.UNSUPPORTED_FORMAT)
        }
        if (videoInfo.durationMs > MAX_DURATION_MS) {
            return ValidationResult.Invalid(ValidationError.DURATION_EXCEEDS_LIMIT)
        }
        // 해상도 상한 없음 — 어떤 크기의 영상이든 허용하고, 프리뷰(PlayerView RESIZE_MODE_FIT)가
        // 고정 미리보기 박스에 맞춰 자동 축소해 렌더한다. (기존 1920px longest-side 제한 제거.)
        return ValidationResult.Valid
    }
}
