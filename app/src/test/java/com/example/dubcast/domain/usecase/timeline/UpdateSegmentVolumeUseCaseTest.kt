package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateSegmentVolumeUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: UpdateSegmentVolumeUseCase

    private fun videoSeg(volume: Float = 1f) = Segment(
        id = "s1",
        projectId = "p1",
        type = SegmentType.VIDEO,
        order = 0,
        sourceUri = "content://s1",
        durationMs = 5_000L,
        width = 1920,
        height = 1080,
        volumeScale = volume
    )

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        useCase = UpdateSegmentVolumeUseCase(repo)
    }

    @Test
    fun `sets volume within range`() = runTest {
        repo.addSegment(videoSeg())
        useCase("s1", 1.5f)
        assertEquals(1.5f, repo.getSegment("s1")!!.volumeScale)
    }

    @Test
    fun `clamps above max`() = runTest {
        repo.addSegment(videoSeg())
        useCase("s1", 5f)
        assertEquals(2f, repo.getSegment("s1")!!.volumeScale)
    }

    @Test
    fun `clamps below min`() = runTest {
        repo.addSegment(videoSeg())
        useCase("s1", -1f)
        assertEquals(0f, repo.getSegment("s1")!!.volumeScale)
    }

    @Test
    fun `does nothing when segment missing`() = runTest {
        useCase("missing", 1.5f)
        assertEquals(0, repo.getByProjectId("p1").size)
    }
}
