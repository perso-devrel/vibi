package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.repository.AudioSeparationRepository
import com.example.dubcast.domain.usecase.bgm.AddBgmClipUseCase
import com.example.dubcast.domain.usecase.input.AudioMetadataExtractor
import javax.inject.Inject

class ApplyMixAsBgmUseCase @Inject constructor(
    private val separationRepository: AudioSeparationRepository,
    private val audioMetadataExtractor: AudioMetadataExtractor,
    private val addBgmClipUseCase: AddBgmClipUseCase
) {
    suspend operator fun invoke(
        projectId: String,
        mixJobId: String,
        downloadUrl: String,
        startMs: Long = 0L,
        volumeScale: Float = 1.0f
    ): Result<BgmClip> = runCatching {
        val outputFile = "mix_$mixJobId.mp3"
        val localPath = separationRepository
            .downloadMix(mixJobId, downloadUrl, outputFile)
            .getOrThrow()
        val info = audioMetadataExtractor.extract(localPath)
            ?: throw IllegalStateException("Cannot read audio metadata: $localPath")
        addBgmClipUseCase(
            projectId = projectId,
            sourceUri = localPath,
            sourceDurationMs = info.durationMs,
            startMs = startMs,
            volumeScale = volumeScale
        )
    }
}
