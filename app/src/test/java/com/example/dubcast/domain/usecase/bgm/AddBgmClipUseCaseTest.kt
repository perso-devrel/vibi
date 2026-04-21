package com.example.dubcast.domain.usecase.bgm

import com.example.dubcast.fake.FakeBgmClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AddBgmClipUseCaseTest {

    private lateinit var repo: FakeBgmClipRepository
    private lateinit var useCase: AddBgmClipUseCase

    @Before
    fun setup() {
        repo = FakeBgmClipRepository()
        useCase = AddBgmClipUseCase(repo)
    }

    @Test
    fun `creates clip and persists to repository`() = runTest {
        val clip = useCase("p1", "content://song.mp3", sourceDurationMs = 120_000L, startMs = 5_000L, volumeScale = 0.8f)
        assertNotNull(repo.getClip(clip.id))
        assertEquals(120_000L, clip.sourceDurationMs)
        assertEquals(5_000L, clip.startMs)
        assertEquals(0.8f, clip.volumeScale)
    }

    @Test
    fun `clamps volume above max to 2`() = runTest {
        val clip = useCase("p1", "content://x", sourceDurationMs = 1000L, volumeScale = 5f)
        assertEquals(2f, clip.volumeScale)
    }

    @Test
    fun `clamps volume below 0 to 0`() = runTest {
        val clip = useCase("p1", "content://x", sourceDurationMs = 1000L, volumeScale = -1f)
        assertEquals(0f, clip.volumeScale)
    }

    @Test
    fun `supports multiple clips on same project`() = runTest {
        useCase("p1", "content://a", sourceDurationMs = 1000L, startMs = 0L)
        useCase("p1", "content://b", sourceDurationMs = 2000L, startMs = 5000L)
        assertEquals(2, repo.all().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank uri`() = runTest {
        useCase("p1", "  ", sourceDurationMs = 1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive duration`() = runTest {
        useCase("p1", "content://x", sourceDurationMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative startMs`() = runTest {
        useCase("p1", "content://x", sourceDurationMs = 1000L, startMs = -1L)
    }
}
