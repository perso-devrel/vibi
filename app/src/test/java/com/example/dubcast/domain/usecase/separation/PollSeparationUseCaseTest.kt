package com.example.dubcast.domain.usecase.separation

import app.cash.turbine.test
import com.example.dubcast.domain.model.Stem
import com.example.dubcast.domain.model.StemKind
import com.example.dubcast.domain.repository.SeparationStatus
import com.example.dubcast.fake.FakeAudioSeparationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PollSeparationUseCaseTest {

    private lateinit var repository: FakeAudioSeparationRepository
    private lateinit var useCase: PollSeparationUseCase

    @Before
    fun setup() {
        repository = FakeAudioSeparationRepository()
        useCase = PollSeparationUseCase(repository)
    }

    @Test
    fun `emits processing until ready and stops`() = runTest {
        val stems = listOf(
            Stem("background", "배경음", "/download/bg", StemKind.BACKGROUND),
            Stem("voice_all", "모든 화자", "/download/v", StemKind.VOICE_ALL)
        )
        repository.statusResults = mutableListOf(
            Result.success(SeparationStatus.Processing("job-1", 30, "Transcribing")),
            Result.success(SeparationStatus.Processing("job-1", 80, "Generating Voice")),
            Result.success(SeparationStatus.Ready("job-1", stems))
        )

        useCase("job-1", intervalMs = 0L).test {
            val first = awaitItem() as SeparationStatus.Processing
            assertEquals(30, first.progress)

            val second = awaitItem() as SeparationStatus.Processing
            assertEquals(80, second.progress)

            val third = awaitItem() as SeparationStatus.Ready
            assertEquals(2, third.stems.size)

            awaitComplete()
        }
    }

    @Test
    fun `stops on failed`() = runTest {
        repository.statusResults = mutableListOf(
            Result.success(SeparationStatus.Processing("job-1", 10, "Uploading")),
            Result.success(SeparationStatus.Failed("job-1", "Failed"))
        )

        useCase("job-1", intervalMs = 0L).test {
            awaitItem() as SeparationStatus.Processing
            val failed = awaitItem()
            assertTrue(failed is SeparationStatus.Failed)
            awaitComplete()
        }
    }

    @Test
    fun `stops on consumed`() = runTest {
        repository.statusResults = mutableListOf(
            Result.success(SeparationStatus.Consumed("job-1", "mix-9"))
        )

        useCase("job-1", intervalMs = 0L).test {
            val consumed = awaitItem() as SeparationStatus.Consumed
            assertEquals("mix-9", consumed.mixJobId)
            awaitComplete()
        }
    }
}
