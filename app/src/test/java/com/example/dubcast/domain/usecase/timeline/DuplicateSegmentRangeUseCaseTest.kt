package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DuplicateSegmentRangeUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var split: SplitSegmentUseCase
    private lateinit var useCase: DuplicateSegmentRangeUseCase

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
        useCase = DuplicateSegmentRangeUseCase(repo, split)
    }

    @Test
    fun `inserts duplicate right after middle in a 3-way split`() = runTest {
        repo.addSegment(videoSeg("a", 0))

        val dup = useCase("a", 3_000L, 7_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(4, all.size)
        // Expected order: pre(a), middle(new1), duplicate, post(new2)
        assertEquals("a", all[0].id)
        assertEquals(3_000L, all[1].trimStartMs)
        assertEquals(7_000L, all[1].trimEndMs)
        assertEquals(dup.id, all[2].id)
        assertEquals(3_000L, all[2].trimStartMs)
        assertEquals(7_000L, all[2].trimEndMs)
        assertEquals(7_000L, all[3].trimStartMs)
        assertEquals(10_000L, all[3].trimEndMs)

        assertNotEquals(all[1].id, all[2].id)

        // The duplicate carries a pointer to its source middle segment;
        // surrounding pieces stay unmarked so the timeline only highlights
        // the newly-inserted clone.
        assertEquals(all[1].id, all[2].duplicatedFromId)
        assertNull(all[0].duplicatedFromId)
        assertNull(all[1].duplicatedFromId)
        assertNull(all[3].duplicatedFromId)
    }

    @Test
    fun `shifts following siblings by split inserts plus duplicate`() = runTest {
        repo.addSegment(videoSeg("a", 0))
        repo.addSegment(videoSeg("b", 1))

        useCase("a", 3_000L, 7_000L)

        val all = repo.getByProjectId("p1")
        // a(pre) middle dup post b → b should end up at order 4
        val b = all.single { it.id == "b" }
        assertEquals(4, b.order)
    }
}
