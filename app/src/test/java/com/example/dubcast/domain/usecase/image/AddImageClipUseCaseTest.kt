package com.example.dubcast.domain.usecase.image

import com.example.dubcast.fake.FakeImageClipRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AddImageClipUseCaseTest {

    private lateinit var repository: FakeImageClipRepository
    private lateinit var useCase: AddImageClipUseCase

    @Before
    fun setup() {
        repository = FakeImageClipRepository()
        useCase = AddImageClipUseCase(repository)
    }

    @Test
    fun `adds image clip with default position and size`() = runTest {
        val clip = useCase(
            projectId = "p1",
            imageUri = "content://sample.jpg",
            startMs = 1000L,
            endMs = 4000L
        )

        assertNotNull(clip.id)
        assertEquals("p1", clip.projectId)
        assertEquals(1000L, clip.startMs)
        assertEquals(4000L, clip.endMs)
        assertEquals(50f, clip.xPct)
        assertEquals(30f, clip.widthPct)

        val saved = repository.observeClips("p1").first()
        assertEquals(1, saved.size)
        assertEquals(clip.id, saved[0].id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when endMs is not greater than startMs`() = runTest {
        useCase(
            projectId = "p1",
            imageUri = "content://sample.jpg",
            startMs = 5000L,
            endMs = 5000L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when startMs is negative`() = runTest {
        useCase(
            projectId = "p1",
            imageUri = "content://sample.jpg",
            startMs = -1L,
            endMs = 500L
        )
    }
}
