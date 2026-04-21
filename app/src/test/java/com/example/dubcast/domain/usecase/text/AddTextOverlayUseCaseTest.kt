package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.fake.FakeTextOverlayRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AddTextOverlayUseCaseTest {

    private lateinit var repo: FakeTextOverlayRepository
    private lateinit var useCase: AddTextOverlayUseCase

    @Before
    fun setup() {
        repo = FakeTextOverlayRepository()
        useCase = AddTextOverlayUseCase(repo)
    }

    @Test
    fun `creates overlay with defaults`() = runTest {
        val overlay = useCase("p1", "Hello", 0L, 2_000L)
        assertNotNull(repo.getOverlay(overlay.id))
        assertEquals("Hello", overlay.text)
        assertEquals(TextOverlay.DEFAULT_FONT_FAMILY, overlay.fontFamily)
        assertEquals(TextOverlay.DEFAULT_FONT_SIZE_SP, overlay.fontSizeSp)
        assertEquals(TextOverlay.DEFAULT_COLOR_HEX, overlay.colorHex)
    }

    @Test
    fun `clamps xPct yPct to bounds`() = runTest {
        val overlay = useCase("p1", "x", 0L, 1000L, xPct = 150f, yPct = -10f)
        assertEquals(100f, overlay.xPct)
        assertEquals(0f, overlay.yPct)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank text`() = runTest {
        useCase("p1", "  ", 0L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects endMs equal to startMs`() = runTest {
        useCase("p1", "x", 1000L, 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported fontFamily`() = runTest {
        useCase("p1", "x", 0L, 1000L, fontFamily = "comic-sans")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects fontSize below minimum`() = runTest {
        useCase("p1", "x", 0L, 1000L, fontSizeSp = 4f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects fontSize above maximum`() = runTest {
        useCase("p1", "x", 0L, 1000L, fontSizeSp = 200f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed color`() = runTest {
        useCase("p1", "x", 0L, 1000L, colorHex = "red")
    }
}
