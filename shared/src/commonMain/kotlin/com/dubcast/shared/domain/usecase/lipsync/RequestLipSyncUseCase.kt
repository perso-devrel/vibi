package com.dubcast.shared.domain.usecase.lipsync

import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.repository.LipSyncRepository

class RequestLipSyncUseCase constructor(
    private val lipSyncRepository: LipSyncRepository
) {
    suspend operator fun invoke(
        videoUri: String,
        clip: DubClip
    ): Result<String> {
        return lipSyncRepository.requestLipSync(
            videoUri = videoUri,
            audioFilePath = clip.audioFilePath,
            startMs = clip.startMs,
            durationMs = clip.durationMs
        )
    }
}
