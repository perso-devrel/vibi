package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoveSegmentRangeUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var split: SplitSegmentUseCase
    private lateinit var remove: RemoveSegmentUseCase
    private lateinit var useCase: RemoveSegmentRangeUseCase

    private fun videoSeg(id: String, order: Int, durationMs: Long = 10_000L) = Segment(
        id = id,
        projectId = "p1",
        type = SegmentType.VIDEO,
        order = order,
        sourceUri = "content://$id",
        durationMs = durationMs,
        width = 1920,
        height = 1080
    )

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        split = SplitSegmentUseCase(repo)
        remove = RemoveSegmentUseCase(repo)
        useCase = RemoveSegmentRangeUseCase(repo, split, remove)
    }

    @Test
    fun `removes middle piece leaving pre and post`() = runTest {
        repo.addSegment(videoSeg("a", 0))

        useCase("a", 3_000L, 7_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(2, all.size)
        assertEquals("a", all[0].id)
        assertEquals(3_000L, all[0].trimEndMs)
        assertEquals(7_000L, all[1].trimStartMs)
        assertEquals(10_000L, all[1].trimEndMs)
        assertTrue(all.zipWithNext().all { (l, r) -> l.order + 1 == r.order })
    }

    @Test
    fun `noop when removal would clear timeline`() = runTest {
        repo.addSegment(videoSeg("a", 0, durationMs = 5_000L))

        useCase("a", 0L, 5_000L) // whole segment -> middle == original; shouldn't drop to 0

        val all = repo.getByProjectId("p1")
        assertEquals(1, all.size)
    }
}
