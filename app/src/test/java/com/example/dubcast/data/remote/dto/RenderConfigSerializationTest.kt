package com.example.dubcast.data.remote.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderConfigSerializationTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RenderConfig::class.java)

    @Test
    fun `config with a single video segment roundtrips`() {
        val config = RenderConfig(
            dubClips = emptyList(),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "video_0",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 10_000L,
                    trimStartMs = 0L,
                    trimEndMs = 10_000L,
                    width = 1920,
                    height = 1080
                )
            )
        )
        val json = adapter.toJson(config)
        assertTrue(json.contains("\"segments\""))
        assertTrue(json.contains("video_0"))
        assertEquals(config, adapter.fromJson(json)!!)
    }

    @Test
    fun `config with mixed VIDEO and IMAGE segments roundtrips`() {
        val config = RenderConfig(
            dubClips = emptyList(),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "video_0",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 10_000L,
                    trimStartMs = 2_000L,
                    trimEndMs = 8_000L,
                    width = 1920,
                    height = 1080
                ),
                RenderSegment(
                    sourceFileKey = "segment_image_1",
                    type = "IMAGE",
                    order = 1,
                    durationMs = 3_000L,
                    width = 1024,
                    height = 768,
                    imageXPct = 40f,
                    imageYPct = 60f,
                    imageWidthPct = 70f,
                    imageHeightPct = 55f
                )
            )
        )
        val json = adapter.toJson(config)
        assertTrue(json.contains("segment_image_1"))
        assertTrue(json.contains("\"IMAGE\""))
        assertEquals(config, adapter.fromJson(json)!!)
    }

    @Test
    fun `video segment with volumeScale and speedScale roundtrips`() {
        val config = RenderConfig(
            dubClips = emptyList(),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "video_0",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 10_000L,
                    trimStartMs = 0L,
                    trimEndMs = 10_000L,
                    width = 1920,
                    height = 1080,
                    volumeScale = 1.5f,
                    speedScale = 0.5f
                )
            )
        )
        val json = adapter.toJson(config)
        assertTrue(json.contains("\"volumeScale\":1.5"))
        assertTrue(json.contains("\"speedScale\":0.5"))
        assertEquals(config, adapter.fromJson(json)!!)
    }

    @Test
    fun `config with image clips and segments roundtrips`() {
        val config = RenderConfig(
            dubClips = listOf(
                RenderDubClip("audio_0", startMs = 1000L, durationMs = 0L, volume = 1f)
            ),
            segments = listOf(
                RenderSegment(
                    sourceFileKey = "video_0",
                    type = "VIDEO",
                    order = 0,
                    durationMs = 20_000L,
                    width = 1280,
                    height = 720
                )
            ),
            imageClips = listOf(
                RenderImageClip("image_0", startMs = 2000L, endMs = 5000L, xPct = 50f, yPct = 50f, widthPct = 30f, heightPct = 30f)
            )
        )
        val json = adapter.toJson(config)
        val parsed = adapter.fromJson(json)!!
        assertEquals(config, parsed)
    }
}
