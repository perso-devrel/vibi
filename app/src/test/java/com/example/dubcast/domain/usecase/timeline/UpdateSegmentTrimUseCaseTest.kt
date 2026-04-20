package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateSegmentTrimUseCaseTest {

    private lateinit var segmentRepo: FakeSegmentRepository
    private lateinit var useCase: UpdateSegmentTrimUseCase

    private val videoSegment = Segment(
        id = "s1",
        projectId = "p1",
        type = SegmentType.VIDEO,
        order = 0,
        sourceUri = "content://v",
        durationMs = 10_000L,
        width = 1920,
        height = 1080
    )

    @Before
    fun setup() = runTest {
        segmentRepo = FakeSegmentRepository()
        segmentRepo.addSegment(videoSegment)
        useCase = UpdateSegmentTrimUseCase(segmentRepo)
    }

    @Test
    fun `applies trim values`() = runTest {
        useCase("s1", trimStartMs = 2_000L, trimEndMs = 8_000L)
        val updated = segmentRepo.getSegment("s1")!!
        assertEquals(2_000L, updated.trimStartMs)
        assertEquals(8_000L, updated.trimEndMs)
    }

    @Test
    fun `clamps negative start to zero`() = runTest {
        useCase("s1", trimStartMs = -500L, trimEndMs = 5_000L)
        val updated = segmentRepo.getSegment("s1")!!
        assertEquals(0L, updated.trimStartMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when trimEnd less than or equal to trimStart`() = runTest {
        useCase("s1", trimStartMs = 3_000L, trimEndMs = 3_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when trimEnd exceeds segment duration`() = runTest {
        useCase("s1", trimStartMs = 0L, trimEndMs = 20_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when segment not found`() = runTest {
        useCase("missing", trimStartMs = 0L, trimEndMs = 1_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects trimming an image segment`() = runTest {
        segmentRepo.addSegment(
            videoSegment.copy(id = "img", type = SegmentType.IMAGE)
        )
        useCase("img", trimStartMs = 0L, trimEndMs = 1_000L)
    }
}
