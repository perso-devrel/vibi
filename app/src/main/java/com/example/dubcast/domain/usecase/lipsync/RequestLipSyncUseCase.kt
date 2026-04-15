package com.example.dubcast.domain.usecase.lipsync

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.repository.LipSyncRepository
import javax.inject.Inject

class RequestLipSyncUseCase @Inject constructor(
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
