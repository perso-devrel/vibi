package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.fake.FakeDubClipRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeleteDubClipUseCaseTest {

    private lateinit var repository: FakeDubClipRepository
    private lateinit var useCase: DeleteDubClipUseCase

    private val clip = DubClip(
        id = "clip-1",
        projectId = "project-1",
        text = "Hello",
        voiceId = "voice-1",
        voiceName = "Rachel",
        audioFilePath = "/nonexistent/audio.mp3",
        startMs = 5000L,
        durationMs = 3000L
    )

    @Before
    fun setup() {
        repository = FakeDubClipRepository()
        useCase = DeleteDubClipUseCase(repository)
    }

    @Test
    fun `deletes clip from repository`() = runTest {
        repository.addClip(clip)

        useCase("clip-1")

        assertNull(repository.getClip("clip-1"))
        assertEquals(0, repository.observeClips("project-1").first().size)
    }

    @Test
    fun `does nothing when clip does not exist`() = runTest {
        useCase("nonexistent")
        // should not throw
    }
}
