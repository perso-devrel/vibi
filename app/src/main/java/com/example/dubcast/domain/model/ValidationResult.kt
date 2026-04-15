package com.example.dubcast.domain.model

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: ValidationError) : ValidationResult
}

enum class ValidationError {
    UNSUPPORTED_FORMAT,
    DURATION_EXCEEDS_LIMIT,
    RESOLUTION_EXCEEDS_LIMIT,
    METADATA_UNREADABLE
}
