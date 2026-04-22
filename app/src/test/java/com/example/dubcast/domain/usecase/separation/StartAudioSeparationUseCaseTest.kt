package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.model.SeparationMediaType
import com.example.dubcast.fake.FakeAudioSeparationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartAudioSeparationUseCaseTest {

    private lateinit var repository: FakeAudioSeparationRepository
    private lateinit var useCase: StartAudioSeparationUseCase

    @Before
    fun setup() {
        repository = FakeAudioSeparationRepository()
        useCase = StartAudioSeparationUseCase(repository)
    }

    @Test
    fun `returns jobId on success`() = runTest {
        repository.startResult = Result.success("sep-abc")

        val result = useCase("content://video", SeparationMediaType.VIDEO, 2)

        assertTrue(result.isSuccess)
        assertEquals("sep-abc", result.getOrThrow())
        assertEquals(SeparationMediaType.VIDEO, repository.lastStartArgs?.mediaType)
        assertEquals(2, repository.lastStartArgs?.numberOfSpeakers)
        assertEquals("auto", repository.lastStartArgs?.sourceLanguageCode)
    }

    @Test
    fun `rejects speakers below 1`() = runTest {
        val result = useCase("content://video", SeparationMediaType.VIDEO, 0)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `rejects speakers above 10`() = runTest {
        val result = useCase("content://video", SeparationMediaType.VIDEO, 11)
        assertTrue(result.isFailure)
    }

    @Test
    fun `propagates repository failure`() = runTest {
        repository.startResult = Result.failure(RuntimeException("402"))
        val result = useCase("content://video", SeparationMediaType.VIDEO, 2)
        assertTrue(result.isFailure)
    }
}
