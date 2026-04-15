package com.example.dubcast.domain.usecase.subtitle

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.fake.FakeSubtitleClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EditSubtitleClipUseCaseTest {

    private lateinit var repository: FakeSubtitleClipRepository
    private lateinit var useCase: EditSubtitleClipUseCase

    private val clip = SubtitleClip(
        id = "sub-1",
        projectId = "project-1",
        text = "Hello",
        startMs = 1000L,
        endMs = 5000L,
        position = SubtitlePosition(Anchor.BOTTOM, 90f)
    )

    @Before
    fun setup() {
        repository = FakeSubtitleClipRepository()
        useCase = EditSubtitleClipUseCase(repository)
    }

    @Test
    fun `updates text only`() = runTest {
        repository.addClip(clip)

        val result = useCase(clip, text = "New text")

        assertEquals("New text", result.text)
        assertEquals(clip.startMs, result.startMs)
        assertEquals(clip.endMs, result.endMs)
        assertEquals(clip.position, result.position)
    }

    @Test
    fun `updates position only`() = runTest {
        repository.addClip(clip)
        val newPos = SubtitlePosition(Anchor.TOP, 10f)

        val result = useCase(clip, position = newPos)

        assertEquals(clip.text, result.text)
        assertEquals(newPos, result.position)
    }

    @Test
    fun `updates timing`() = runTest {
        repository.addClip(clip)

        val result = useCase(clip, startMs = 2000L, endMs = 6000L)

        assertEquals(2000L, result.startMs)
        assertEquals(6000L, result.endMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when endMs is not greater than startMs`() = runTest {
        repository.addClip(clip)
        useCase(clip, startMs = 5000L, endMs = 3000L)
    }

    @Test
    fun `saves updated clip to repository`() = runTest {
        repository.addClip(clip)

        useCase(clip, text = "Updated")

        val saved = repository.getClip("sub-1")
        assertEquals("Updated", saved?.text)
    }
}
