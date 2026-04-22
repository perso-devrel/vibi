package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.repository.StemSelection
import com.example.dubcast.fake.FakeAudioSeparationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestStemMixUseCaseTest {

    private lateinit var repository: FakeAudioSeparationRepository
    private lateinit var useCase: RequestStemMixUseCase

    @Before
    fun setup() {
        repository = FakeAudioSeparationRepository()
        useCase = RequestStemMixUseCase(repository)
    }

    @Test
    fun `returns mixJobId and forwards selections`() = runTest {
        repository.mixRequestResult = Result.success("mix-42")
        val selections = listOf(
            StemSelection("background", 0.6f),
            StemSelection("speaker_0", 1.2f)
        )

        val result = useCase("sep-1", selections)

        assertTrue(result.isSuccess)
        assertEquals("mix-42", result.getOrThrow())
        assertEquals(1, repository.mixRequests.size)
        assertEquals("sep-1", repository.mixRequests[0].first)
        assertEquals(selections, repository.mixRequests[0].second)
    }

    @Test
    fun `rejects empty selections`() = runTest {
        val result = useCase("sep-1", emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects negative volume`() = runTest {
        val result = useCase("sep-1", listOf(StemSelection("background", -0.1f)))
        assertTrue(result.isFailure)
    }
}
