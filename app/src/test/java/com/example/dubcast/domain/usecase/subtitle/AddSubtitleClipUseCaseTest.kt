package com.example.dubcast.domain.usecase.subtitle

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.fake.FakeSubtitleClipRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AddSubtitleClipUseCaseTest {

    private lateinit var repository: FakeSubtitleClipRepository
    private lateinit var useCase: AddSubtitleClipUseCase

    @Before
    fun setup() {
        repository = FakeSubtitleClipRepository()
        useCase = AddSubtitleClipUseCase(repository)
    }

    @Test
    fun `adds subtitle clip to repository`() = runTest {
        val position = SubtitlePosition(Anchor.BOTTOM, 90f)

        val clip = useCase("project-1", "Hello", 1000L, 5000L, position)

        assertNotNull(clip.id)
        assertEquals("project-1", clip.projectId)
        assertEquals("Hello", clip.text)
        assertEquals(1000L, clip.startMs)
        assertEquals(5000L, clip.endMs)
        assertEquals(position, clip.position)

        val saved = repository.observeClips("project-1").first()
        assertEquals(1, saved.size)
        assertEquals(clip.id, saved[0].id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when endMs is not greater than startMs`() = runTest {
        useCase("project-1", "Hello", 5000L, 5000L, SubtitlePosition(Anchor.BOTTOM, 90f))
    }
}
