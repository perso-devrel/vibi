package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssGeneratorTest {

    private val generator = AssGenerator()

    private fun clip(
        id: String = "clip-1",
        startMs: Long = 1000L,
        endMs: Long = 4500L,
        text: String = "Hello world",
        anchor: Anchor = Anchor.BOTTOM,
        yOffsetPct: Float = 90f
    ) = SubtitleClip(
        id = id, projectId = "proj-1",
        startMs = startMs, endMs = endMs,
        text = text,
        position = SubtitlePosition(anchor, yOffsetPct)
    )

    @Test
    fun `generates valid ASS header`() {
        val result = generator.generateFromClips(listOf(clip()), 1920, 1080)
        assertTrue(result.contains("[Script Info]"))
        assertTrue(result.contains("PlayResX: 1920"))
        assertTrue(result.contains("PlayResY: 1080"))
    }

    @Test
    fun `contains V4+ Styles section`() {
        val result = generator.generateFromClips(listOf(clip()), 1920, 1080)
        assertTrue(result.contains("[V4+ Styles]"))
        assertTrue(result.contains("Style: Default"))
    }

    @Test
    fun `contains Events section`() {
        val result = generator.generateFromClips(listOf(clip()), 1920, 1080)
        assertTrue(result.contains("[Events]"))
        assertTrue(result.contains("Dialogue:"))
    }

    @Test
    fun `bottom anchor uses an2`() {
        val result = generator.generateFromClips(listOf(clip(anchor = Anchor.BOTTOM)), 1920, 1080)
        assertTrue(result.contains("\\an2"))
    }

    @Test
    fun `top anchor uses an8`() {
        val result = generator.generateFromClips(listOf(clip(anchor = Anchor.TOP)), 1920, 1080)
        assertTrue(result.contains("\\an8"))
    }

    @Test
    fun `middle anchor uses an5`() {
        val result = generator.generateFromClips(listOf(clip(anchor = Anchor.MIDDLE)), 1920, 1080)
        assertTrue(result.contains("\\an5"))
    }

    @Test
    fun `yOffsetPct maps to pos coordinates`() {
        val result = generator.generateFromClips(
            listOf(clip(anchor = Anchor.BOTTOM, yOffsetPct = 50f)),
            1920, 1080
        )
        assertTrue(result.contains("\\pos(960,540)"))
    }

    @Test
    fun `timestamp format HH_MM_SS_cc`() {
        val result = generator.generateFromClips(
            listOf(clip(startMs = 61500, endMs = 65000)),
            1920, 1080
        )
        assertTrue(result.contains("0:01:01.50"))
    }

    @Test
    fun `newline in text converted to ASS newline`() {
        val result = generator.generateFromClips(
            listOf(clip(text = "Line one\nLine two")),
            1920, 1080
        )
        assertTrue(result.contains("Line one\\NLine two"))
    }

    @Test
    fun `multiple clips generate multiple Dialogue lines`() {
        val clips = listOf(
            clip(id = "1", startMs = 0, endMs = 2000, text = "First"),
            clip(id = "2", startMs = 2000, endMs = 4000, text = "Second"),
            clip(id = "3", startMs = 4000, endMs = 6000, text = "Third")
        )
        val result = generator.generateFromClips(clips, 1920, 1080)
        val dialogueCount = result.lines().count { it.startsWith("Dialogue:") }
        assertEquals(3, dialogueCount)
    }

    @Test
    fun `empty clips produces valid file with no events`() {
        val result = generator.generateFromClips(emptyList(), 1920, 1080)
        assertTrue(result.contains("[Script Info]"))
        val dialogueCount = result.lines().count { it.startsWith("Dialogue:") }
        assertEquals(0, dialogueCount)
    }

    @Test
    fun `CJK text passes through unchanged`() {
        val result = generator.generateFromClips(
            listOf(clip(text = "안녕하세요 세계")),
            1920, 1080
        )
        assertTrue(result.contains("안녕하세요 세계"))
    }
}
