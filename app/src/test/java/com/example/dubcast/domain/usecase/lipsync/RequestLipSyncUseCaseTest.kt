package com.example.dubcast.domain.usecase.lipsync

import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.fake.FakeLipSyncRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLipSyncUseCaseTest {

    private lateinit var repository: FakeLipSyncRepository
    private lateinit var useCase: RequestLipSyncUseCase

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
        repository = FakeLipSyncRepository()
        useCase = RequestLipSyncUseCase(repository)
    }

    @Test
    fun `returns job id on success`() = runTest {
        repository.requestResult = Result.success("job-456")

        val result = useCase("content://video", clip)

        assertTrue(result.isSuccess)
        assertEquals("job-456", result.getOrThrow())
    }

    @Test
    fun `returns failure when repository fails`() = runTest {
        repository.requestResult = Result.failure(RuntimeException("Upload failed"))

        val result = useCase("content://video", clip)

        assertTrue(result.isFailure)
    }
}
