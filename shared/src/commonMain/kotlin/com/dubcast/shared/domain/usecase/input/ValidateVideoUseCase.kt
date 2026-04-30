package com.dubcast.shared.domain.usecase.input

import com.dubcast.shared.domain.model.ValidationError
import com.dubcast.shared.domain.model.ValidationResult
import com.dubcast.shared.domain.model.VideoInfo

class ValidateVideoUseCase constructor() {

    companion object {
        private val SUPPORTED_MIME_TYPES = setOf(
            "video/mp4",
            "video/quicktime",
            "video/webm"
        )
        private const val MAX_DURATION_MS = 600_000L
        private const val MAX_DIMENSION = 1920
    }

    operator fun invoke(videoInfo: VideoInfo): ValidationResult {
        if (videoInfo.mimeType !in SUPPORTED_MIME_TYPES) {
            return ValidationResult.Invalid(ValidationError.UNSUPPORTED_FORMAT)
        }
        if (videoInfo.durationMs > MAX_DURATION_MS) {
            return ValidationResult.Invalid(ValidationError.DURATION_EXCEEDS_LIMIT)
        }
        if (maxOf(videoInfo.width, videoInfo.height) > MAX_DIMENSION) {
            return ValidationResult.Invalid(ValidationError.RESOLUTION_EXCEEDS_LIMIT)
        }
        return ValidationResult.Valid
    }
}
