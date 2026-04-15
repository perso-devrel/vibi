package com.example.dubcast.domain.usecase.lipsync

import app.cash.turbine.test
import com.example.dubcast.domain.repository.LipSyncStatus
import com.example.dubcast.fake.FakeLipSyncRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PollLipSyncUseCaseTest {

    private lateinit var repository: FakeLipSyncRepository
    private lateinit var useCase: PollLipSyncUseCase

    @Before
    fun setup() {
        repository = FakeLipSyncRepository()
        useCase = PollLipSyncUseCase(repository)
    }

    @Test
    fun `emits statuses until completed`() = runTest {
        repository.statusResults = mutableListOf(
            Result.success(LipSyncStatus("job-1", 30, false, null)),
            Result.success(LipSyncStatus("job-1", 70, false, null)),
            Result.success(LipSyncStatus("job-1", 100, true, "/result.mp4"))
        )

        useCase("job-1", intervalMs = 0L).test {
            val first = awaitItem()
            assertEquals(30, first.progress)
            assertFalse(first.isCompleted)

            val second = awaitItem()
            assertEquals(70, second.progress)
            assertFalse(second.isCompleted)

            val third = awaitItem()
            assertEquals(100, third.progress)
            assertTrue(third.isCompleted)
            assertEquals("/result.mp4", third.resultVideoPath)

            awaitComplete()
        }
    }
}
