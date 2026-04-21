package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun stickerClip(
        id: String = "auto-1",
        startMs: Long = 1000L,
        endMs: Long = 4000L,
        text: String = "Auto subtitle",
        xPct: Float = 50f,
        yPct: Float = 85f,
        widthPct: Float = 80f,
        heightPct: Float = 12f
    ) = SubtitleClip(
        id = id, projectId = "proj-1",
        startMs = startMs, endMs = endMs,
        text = text,
        position = SubtitlePosition(Anchor.BOTTOM, 90f),
        sourceDubClipId = "dub-1",
        xPct = xPct, yPct = yPct, widthPct = widthPct, heightPct = heightPct
    )

    @Test
    fun `sticker subtitle uses center-anchor an5 and absolute pos`() {
        // xPct=50 → posX=960, yPct=85 on 1080 → posY=918
        val result = generator.generateFromClips(listOf(stickerClip()), 1920, 1080)
        assertTrue(result.contains("\\an5"))
        assertTrue(result.contains("\\pos(960,918)"))
    }

    @Test
    fun `sticker subtitle font size derived from heightPct`() {
        // heightPct=12, videoHeight=1080 → fontSize = 0.12*1080 = 129
        val result = generator.generateFromClips(listOf(stickerClip(heightPct = 12f)), 1920, 1080)
        assertTrue("Expected \\fs129 in output", result.contains("\\fs129"))
    }

    @Test
    fun `sticker subtitle does not emit anchor tag`() {
        val result = generator.generateFromClips(listOf(stickerClip()), 1920, 1080)
        // Should use \an5 (center), not \an2/\an8
        assertTrue(result.contains("\\an5"))
        assertFalse("Sticker should not emit \\an2", result.contains("\\an2"))
        assertFalse("Sticker should not emit \\an8", result.contains("\\an8"))
    }

    @Test
    fun `mixed sticker and manual clips both appear in output`() {
        val clips = listOf(
            clip(id = "m1", startMs = 0, endMs = 2000, text = "Manual"),
            stickerClip(id = "a1", startMs = 2000, endMs = 4000, text = "Auto")
        )
        val result = generator.generateFromClips(clips, 1920, 1080)
        val dialogues = result.lines().count { it.startsWith("Dialogue:") }
        assertEquals(2, dialogues)
        assertTrue(result.contains("Manual"))
        assertTrue(result.contains("Auto"))
    }

    @Test
    fun `text overlays are emitted as additional Dialogue lines with font and color overrides`() {
        val overlay = TextOverlay(
            id = "t1",
            projectId = "proj-1",
            text = "Hi",
            fontFamily = "noto_serif_kr",
            fontSizeSp = 36f,
            colorHex = "#FFAABBCC",
            startMs = 0L,
            endMs = 1000L,
            xPct = 50f,
            yPct = 50f
        )
        val result = generator.generateFromClips(emptyList(), 1920, 1080, listOf(overlay))
        val dialogues = result.lines().filter { it.startsWith("Dialogue:") }
        assertEquals(1, dialogues.size)
        val line = dialogues.single()
        assertTrue("expects font override", line.contains("\\fnNoto Serif KR"))
        // ASS color order is BGR: RR=AA, GG=BB, BB=CC → &HFFCCBBAA&
        assertTrue("expects ass color &HFFCCBBAA&, got: $line", line.contains("\\c&HFFCCBBAA&"))
        assertTrue("expects positioning at center", line.contains("\\an5\\pos(960,540)"))
    }

    @Test
    fun `subtitles and text overlays both appear in output`() {
        val sub = clip(id = "s1", startMs = 0L, endMs = 500L, text = "Sub")
        val overlay = TextOverlay(
            id = "t1", projectId = "proj-1", text = "Overlay",
            startMs = 1000L, endMs = 2000L
        )
        val result = generator.generateFromClips(listOf(sub), 1920, 1080, listOf(overlay))
        val dialogues = result.lines().count { it.startsWith("Dialogue:") }
        assertEquals(2, dialogues)
        assertTrue(result.contains("Sub"))
        assertTrue(result.contains("Overlay"))
    }
}
