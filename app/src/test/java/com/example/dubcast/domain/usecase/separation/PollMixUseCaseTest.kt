package com.example.dubcast.domain.usecase.separation

import app.cash.turbine.test
import com.example.dubcast.domain.repository.MixStatus
import com.example.dubcast.fake.FakeAudioSeparationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PollMixUseCaseTest {

    private lateinit var repository: FakeAudioSeparationRepository
    private lateinit var useCase: PollMixUseCase

    @Before
    fun setup() {
        repository = FakeAudioSeparationRepository()
        useCase = PollMixUseCase(repository)
    }

    @Test
    fun `emits processing until completed`() = runTest {
        repository.mixStatusResults = mutableListOf(
            Result.success(MixStatus.Processing("mix-1", 0)),
            Result.success(MixStatus.Completed("mix-1", "/download/mix?token=abc"))
        )

        useCase("mix-1", intervalMs = 0L).test {
            val first = awaitItem() as MixStatus.Processing
            assertEquals(0, first.progress)

            val completed = awaitItem() as MixStatus.Completed
            assertEquals("/download/mix?token=abc", completed.downloadUrl)

            awaitComplete()
        }
    }
}
