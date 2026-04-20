package com.example.dubcast.domain.usecase.image

import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.fake.FakeImageClipRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateImageClipUseCaseTest {

    private lateinit var repository: FakeImageClipRepository
    private lateinit var useCase: UpdateImageClipUseCase

    @Before
    fun setup() {
        repository = FakeImageClipRepository()
        useCase = UpdateImageClipUseCase(repository)
    }

    @Test
    fun `updates position and size`() = runTest {
        val original = ImageClip(
            id = "c1",
            projectId = "p1",
            imageUri = "content://x",
            startMs = 0L,
            endMs = 3000L
        )
        repository.addClip(original)

        useCase(original.copy(xPct = 25f, yPct = 75f, widthPct = 40f, heightPct = 40f))

        val saved = repository.observeClips("p1").first().first()
        assertEquals(25f, saved.xPct)
        assertEquals(75f, saved.yPct)
        assertEquals(40f, saved.widthPct)
        assertEquals(40f, saved.heightPct)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects inverted time range`() = runTest {
        val clip = ImageClip(
            id = "c1",
            projectId = "p1",
            imageUri = "content://x",
            startMs = 5000L,
            endMs = 4000L
        )
        useCase(clip)
    }
}
