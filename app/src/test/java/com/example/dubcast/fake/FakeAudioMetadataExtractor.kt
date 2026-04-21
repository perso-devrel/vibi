package com.example.dubcast.fake

import com.example.dubcast.domain.usecase.input.AudioInfo
import com.example.dubcast.domain.usecase.input.AudioMetadataExtractor

class FakeAudioMetadataExtractor : AudioMetadataExtractor {
    var nextInfo: AudioInfo? = AudioInfo(uri = "fake://", durationMs = 60_000L)
    var lastUri: String? = null

    override suspend fun extract(uri: String): AudioInfo? {
        lastUri = uri
        return nextInfo?.copy(uri = uri)
    }
}
