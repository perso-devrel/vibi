package com.example.dubcast.domain.usecase.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitDubTextToSubtitlesUseCaseTest {

    private val useCase = SplitDubTextToSubtitlesUseCase()

    private fun invoke(
        text: String,
        startMs: Long = 0L,
        durationMs: Long = 6000L
    ) = useCase(
        text = text,
        startMs = startMs,
        durationMs = durationMs,
        dubClipId = "dub-1",
        projectId = "proj-1"
    )

    @Test
    fun `single line produces one subtitle spanning full duration`() {
        val result = invoke("Hello world", startMs = 1000L, durationMs = 4000L)

        assertEquals(1, result.size)
        assertEquals(1000L, result[0].startMs)
        assertEquals(5000L, result[0].endMs)
        assertEquals("Hello world", result[0].text)
    }

    @Test
    fun `three equal-weight lines split duration evenly`() {
        // 각 5글자, total = 15, duration = 3000 → 1000ms each
        val result = invoke("AAAAA\nBBBBB\nCCCCC", startMs = 0L, durationMs = 3000L)

        assertEquals(3, result.size)
        assertEquals(0L, result[0].startMs)
        assertEquals(1000L, result[0].endMs)
        assertEquals(1000L, result[1].startMs)
        assertEquals(2000L, result[1].endMs)
        assertEquals(2000L, result[2].startMs)
        assertEquals(3000L, result[2].endMs)
    }

    @Test
    fun `unequal lines split by character weight`() {
        // 5/15/10글자, total=30, duration=6000 → 1000/3000/2000ms
        val text = "AAAAA\nBBBBBBBBBBBBBBB\nCCCCCCCCCC"
        val result = invoke(text, startMs = 0L, durationMs = 6000L)

        assertEquals(3, result.size)
        assertEquals(0L, result[0].startMs)
        assertEquals(1000L, result[0].endMs)  // 5/30 * 6000
        assertEquals(1000L, result[1].startMs)
        assertEquals(4000L, result[1].endMs)  // 15/30 * 6000
        assertEquals(4000L, result[2].startMs)
        assertEquals(6000L, result[2].endMs)  // forced to startMs + durationMs
    }

    @Test
    fun `last subtitle ends exactly at startMs plus durationMs`() {
        // Ensures rounding errors are absorbed by the last line
        val result = invoke("AAA\nBB\nC", startMs = 500L, durationMs = 5000L)

        assertEquals(5500L, result.last().endMs)
    }

    @Test
    fun `empty lines in input are filtered`() {
        val result = invoke("Line one\n\nLine two")

        assertEquals(2, result.size)
        assertEquals("Line one", result[0].text)
        assertEquals("Line two", result[1].text)
    }

    @Test
    fun `whitespace-only lines are filtered`() {
        val result = invoke("Hello\n   \nWorld")

        assertEquals(2, result.size)
        assertEquals("Hello", result[0].text)
        assertEquals("World", result[1].text)
    }

    @Test
    fun `all whitespace lines fallback to single subtitle`() {
        val result = invoke("   \n   ", startMs = 0L, durationMs = 3000L)

        assertEquals(1, result.size)
        assertEquals(0L, result[0].startMs)
        assertEquals(3000L, result[0].endMs)
    }

    @Test
    fun `auto subtitles have sourceDubClipId and sticker coordinates set`() {
        val result = invoke("Hello")

        val clip = result[0]
        assertEquals("dub-1", clip.sourceDubClipId)
        assertNotNull(clip.xPct)
        assertNotNull(clip.yPct)
        assertNotNull(clip.widthPct)
        assertNotNull(clip.heightPct)
        assertTrue(clip.isAuto)
        assertTrue(clip.isSticker)
    }
}
