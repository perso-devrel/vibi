package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateSegmentSpeedUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: UpdateSegmentSpeedUseCase

    private fun seg(type: SegmentType = SegmentType.VIDEO, speed: Float = 1f) = Segment(
        id = "s1",
        projectId = "p1",
        type = type,
        order = 0,
        sourceUri = "content://s1",
        durationMs = 5_000L,
        width = 1920,
        height = 1080,
        speedScale = speed
    )

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        useCase = UpdateSegmentSpeedUseCase(repo)
    }

    @Test
    fun `sets speed within range`() = runTest {
        repo.addSegment(seg())
        useCase("s1", 2f)
        assertEquals(2f, repo.getSegment("s1")!!.speedScale)
    }

    @Test
    fun `clamps above max`() = runTest {
        repo.addSegment(seg())
        useCase("s1", 10f)
        assertEquals(4f, repo.getSegment("s1")!!.speedScale)
    }

    @Test
    fun `clamps below min`() = runTest {
        repo.addSegment(seg())
        useCase("s1", 0.1f)
        assertEquals(0.25f, repo.getSegment("s1")!!.speedScale)
    }

    @Test
    fun `throws for IMAGE segment`() = runTest {
        repo.addSegment(seg(type = SegmentType.IMAGE))
        try {
            useCase("s1", 2f)
            throw AssertionError("should throw")
        } catch (_: IllegalArgumentException) {
        }
    }
}
