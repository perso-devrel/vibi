package com.example.dubcast.fake

import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.repository.TtsResult

class FakeTtsRepository : TtsRepository {

    var voices: List<Voice> = listOf(
        Voice("voice-1", "Rachel", null, "en"),
        Voice("voice-2", "Josh", null, "en")
    )
    var synthesizeResult: Result<TtsResult> = Result.success(
        TtsResult("/fake/audio.mp3", 3000L)
    )
    var voicesResult: Result<List<Voice>>? = null

    override suspend fun getVoices(): Result<List<Voice>> {
        return voicesResult ?: Result.success(voices)
    }

    override suspend fun synthesize(text: String, voiceId: String): Result<TtsResult> {
        return synthesizeResult
    }
}
