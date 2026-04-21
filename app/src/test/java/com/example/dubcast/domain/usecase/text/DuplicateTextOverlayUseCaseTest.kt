package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.fake.FakeTextOverlayRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class DuplicateTextOverlayUseCaseTest {

    private lateinit var repo: FakeTextOverlayRepository
    private lateinit var useCase: DuplicateTextOverlayUseCase

    private val source = TextOverlay(
        id = "o1",
        projectId = "p1",
        text = "Hello",
        fontFamily = "noto_serif_kr",
        fontSizeSp = 32f,
        colorHex = "#FF112233",
        startMs = 1000L,
        endMs = 3000L,
        xPct = 25f,
        yPct = 75f
    )

    @Before
    fun setup() = runTest {
        repo = FakeTextOverlayRepository()
        repo.addOverlay(source)
        useCase = DuplicateTextOverlayUseCase(repo)
    }

    @Test
    fun `places duplicate immediately after source preserving duration and style`() = runTest {
        val dup = useCase("o1")
        assertNotEquals(source.id, dup.id)
        assertEquals(source.endMs, dup.startMs)
        assertEquals(source.endMs + (source.endMs - source.startMs), dup.endMs)
        assertEquals(source.text, dup.text)
        assertEquals(source.fontFamily, dup.fontFamily)
        assertEquals(source.fontSizeSp, dup.fontSizeSp)
        assertEquals(source.colorHex, dup.colorHex)
        assertEquals(source.xPct, dup.xPct)
        assertEquals(source.yPct, dup.yPct)
        assertEquals(2, repo.all().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown overlay`() = runTest {
        useCase("missing")
    }
}
