package com.example.dubcast.domain.usecase.tts

import com.example.dubcast.domain.model.Voice
import com.example.dubcast.fake.FakeTtsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetVoiceListUseCaseTest {

    private lateinit var ttsRepository: FakeTtsRepository
    private lateinit var useCase: GetVoiceListUseCase

    @Before
    fun setup() {
        ttsRepository = FakeTtsRepository()
        useCase = GetVoiceListUseCase(ttsRepository)
    }

    @Test
    fun `returns voice list from repository`() = runTest {
        val expected = listOf(
            Voice("v1", "Alice", null, "en"),
            Voice("v2", "Bob", "http://preview", "ko")
        )
        ttsRepository.voices = expected

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrThrow())
    }

    @Test
    fun `returns failure when repository fails`() = runTest {
        ttsRepository.voicesResult = Result.failure(RuntimeException("Network error"))

        val result = useCase()

        assertTrue(result.isFailure)
    }
}
