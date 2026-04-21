package com.example.dubcast.domain.usecase.bgm

import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.fake.FakeBgmClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateBgmClipUseCaseTest {

    private lateinit var repo: FakeBgmClipRepository
    private lateinit var useCase: UpdateBgmClipUseCase

    private val base = BgmClip(
        id = "b1",
        projectId = "p1",
        sourceUri = "content://x",
        sourceDurationMs = 60_000L,
        startMs = 0L,
        volumeScale = 1f
    )

    @Before
    fun setup() = runTest {
        repo = FakeBgmClipRepository()
        repo.addClip(base)
        useCase = UpdateBgmClipUseCase(repo)
    }

    @Test
    fun `updates startMs only`() = runTest {
        val u = useCase("b1", startMs = 10_000L)
        assertEquals(10_000L, u.startMs)
        assertEquals(1f, u.volumeScale)
    }

    @Test
    fun `clamps volume on update`() = runTest {
        val u = useCase("b1", volumeScale = 5f)
        assertEquals(2f, u.volumeScale)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown clip`() = runTest {
        useCase("missing", startMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative startMs`() = runTest {
        useCase("b1", startMs = -1L)
    }
}
