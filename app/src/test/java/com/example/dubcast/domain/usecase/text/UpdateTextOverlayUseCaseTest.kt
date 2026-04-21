package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.fake.FakeTextOverlayRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateTextOverlayUseCaseTest {

    private lateinit var repo: FakeTextOverlayRepository
    private lateinit var useCase: UpdateTextOverlayUseCase

    private val baseOverlay = TextOverlay(
        id = "o1",
        projectId = "p1",
        text = "old",
        startMs = 0L,
        endMs = 1000L
    )

    @Before
    fun setup() = runTest {
        repo = FakeTextOverlayRepository()
        repo.addOverlay(baseOverlay)
        useCase = UpdateTextOverlayUseCase(repo)
    }

    @Test
    fun `updates text only`() = runTest {
        val updated = useCase("o1", text = "new")
        assertEquals("new", updated.text)
        // unchanged fields
        assertEquals(0L, updated.startMs)
        assertEquals(1000L, updated.endMs)
    }

    @Test
    fun `updates font and color together`() = runTest {
        val updated = useCase(
            "o1",
            fontFamily = "noto_serif_kr",
            fontSizeSp = 36f,
            colorHex = "#FFAA0000"
        )
        assertEquals("noto_serif_kr", updated.fontFamily)
        assertEquals(36f, updated.fontSizeSp)
        assertEquals("#FFAA0000", updated.colorHex)
    }

    @Test
    fun `clamps position when provided`() = runTest {
        val updated = useCase("o1", xPct = -5f, yPct = 250f)
        assertEquals(0f, updated.xPct)
        assertEquals(100f, updated.yPct)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown overlay`() = runTest {
        useCase("missing", text = "x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects start equal to end after update`() = runTest {
        useCase("o1", endMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed color`() = runTest {
        useCase("o1", colorHex = "blue")
    }
}
