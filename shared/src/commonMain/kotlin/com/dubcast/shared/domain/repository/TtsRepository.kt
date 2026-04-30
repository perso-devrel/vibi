package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.Voice

data class TtsResult(
    val localAudioPath: String,
    val durationMs: Long
)

interface TtsRepository {
    suspend fun getVoices(): Result<List<Voice>>
    suspend fun synthesize(text: String, voiceId: String): Result<TtsResult>
}
