package com.dubcast.shared.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderConfigSerializationTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `RenderConfig round-trip preserves fields`() {
        val original = RenderConfig(
            dubClips = listOf(
                RenderDubClip(audioFileKey = "dub1.mp3", startMs = 1_000L, durationMs = 2_000L, volume = 0.8f)
            ),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "seg1.mp4",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 10_000L,
                    trimStartMs = 500L,
                    trimEndMs = 9_500L,
                    width = 1920,
                    height = 1080
                )
            ),
            frame = RenderFrame(width = 1080, height = 1920)
        )
        val encoded = json.encodeToString(RenderConfig.serializer(), original)
        val decoded = json.decodeFromString(RenderConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `TtsRequest encodes camelCase`() {
        val encoded = json.encodeToString(TtsRequest.serializer(), TtsRequest(text = "안녕", voiceId = "v1"))
        assertEquals("""{"text":"안녕","voiceId":"v1"}""", encoded)
    }
}
