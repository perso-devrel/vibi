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
    fun `config roundtrips with image clips`() {
        val config = RenderConfig(
            dubClips = listOf(
                RenderDubClip(audioFileKey = "audio_0", startMs = 1000L, durationMs = 0L, volume = 1f)
            ),
            videoDurationMs = 10_000L,
            trimStartMs = 0L,
            trimEndMs = 0L,
            imageClips = listOf(
                RenderImageClip(
                    imageFileKey = "image_0",
                    startMs = 2000L,
                    endMs = 5000L,
                    xPct = 50f,
                    yPct = 50f,
                    widthPct = 30f,
                    heightPct = 30f
                )
            )
        )

        val json = adapter.toJson(config)
        assertTrue("json must contain imageClips", json.contains("imageClips"))
        assertTrue("json must contain imageFileKey", json.contains("image_0"))

        val parsed = adapter.fromJson(json)!!
        assertEquals(config, parsed)
    }

    @Test
    fun `config defaults to empty imageClips`() {
        val json = """{"dubClips":[],"videoDurationMs":1000}"""
        val parsed = adapter.fromJson(json)!!
        assertEquals(emptyList<RenderImageClip>(), parsed.imageClips)
    }
}
