package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.fake.FakeDubClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MoveDubClipUseCaseTest {

    private lateinit var repository: FakeDubClipRepository
    private lateinit var useCase: MoveDubClipUseCase

    private val clip = DubClip(
        id = "clip-1",
        projectId = "project-1",
        text = "Hello",
        voiceId = "voice-1",
        voiceName = "Rachel",
        audioFilePath = "/audio/test.mp3",
        startMs = 5000L,
        durationMs = 3000L
    )

    @Before
    fun setup() {
        repository = FakeDubClipRepository()
        useCase = MoveDubClipUseCase(repository)
    }

    @Test
    fun `moves clip to new position`() = runTest {
        repository.addClip(clip)

        val result = useCase(clip, 10000L, 30000L)

        assertEquals(10000L, result.startMs)
    }

    @Test
    fun `clamps start to zero when negative`() = runTest {
        repository.addClip(clip)

        val result = useCase(clip, -500L, 30000L)

        assertEquals(0L, result.startMs)
    }

    @Test
    fun `clamps start so clip does not exceed video duration`() = runTest {
        repository.addClip(clip)

        val result = useCase(clip, 29000L, 30000L)

        assertEquals(27000L, result.startMs)
    }

    @Test
    fun `handles clip longer than video`() = runTest {
        repository.addClip(clip)

        val result = useCase(clip, 5000L, 2000L)

        assertEquals(0L, result.startMs)
    }

    @Test
    fun `updates clip in repository`() = runTest {
        repository.addClip(clip)

        useCase(clip, 10000L, 30000L)

        val saved = repository.getClip("clip-1")
        assertEquals(10000L, saved?.startMs)
    }
}
