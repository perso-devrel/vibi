package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.Voice

data class TtsResult(
    val localAudioPath: String,
    val durationMs: Long
)

interface TtsRepository {
    suspend fun getVoices(): Result<List<Voice>>
    suspend fun synthesize(text: String, voiceId: String): Result<TtsResult>
}
