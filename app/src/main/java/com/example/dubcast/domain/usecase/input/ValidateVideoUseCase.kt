package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.ValidationError
import com.example.dubcast.domain.model.ValidationResult
import com.example.dubcast.domain.model.VideoInfo
import javax.inject.Inject

class ValidateVideoUseCase @Inject constructor() {

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
