package com.vibi.shared.domain.usecase.separation

import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.platform.AudioSourceKind

class StartAudioSeparationUseCase constructor(
    private val repository: AudioSeparationRepository
) {
    suspend operator fun invoke(
        sourceUri: String,
        sourceKind: AudioSourceKind,
        sourceLanguageCode: String = "auto",
        trimStartMs: Long? = null,
        trimEndMs: Long? = null,
    ): Result<String> {
        if (trimStartMs != null || trimEndMs != null) {
            if (trimStartMs == null || trimEndMs == null) {
                return Result.failure(IllegalArgumentException("trimStartMs and trimEndMs must be set together"))
            }
            if (trimStartMs < 0L || trimEndMs <= trimStartMs) {
                return Result.failure(IllegalArgumentException("invalid trim range"))
            }
            if (trimEndMs - trimStartMs < 500L) {
                return Result.failure(IllegalArgumentException("trim range too short (< 500ms)"))
            }
        }
        return repository.startSeparation(
            sourceUri = sourceUri,
            sourceKind = sourceKind,
            sourceLanguageCode = sourceLanguageCode,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
        )
    }
}
