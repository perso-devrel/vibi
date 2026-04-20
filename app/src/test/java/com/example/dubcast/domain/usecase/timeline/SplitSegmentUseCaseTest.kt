package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplitSegmentUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: SplitSegmentUseCase

    private fun videoSeg(
        id: String = "a",
        order: Int = 0,
        durationMs: Long = 10_000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L,
        volumeScale: Float = 1f,
        speedScale: Float = 1f
    ) = Segment(
        id = id,
        projectId = "p1",
        type = SegmentType.VIDEO,
        order = order,
        sourceUri = "content://$id",
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        volumeScale = volumeScale,
        speedScale = speedScale
    )

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        useCase = SplitSegmentUseCase(repo)
    }

    @Test
    fun `splits into three when range is strictly inside`() = runTest {
        repo.addSegment(videoSeg(id = "a", order = 0, durationMs = 10_000L))

        val result = useCase("a", 3_000L, 7_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(3, all.size)
        assertNotNull(result.pre)
        assertNotNull(result.post)

        val pre = all[0]
        val middle = all[1]
        val post = all[2]
        assertEquals("a", pre.id)
        assertEquals(0L, pre.trimStartMs)
        assertEquals(3_000L, pre.trimEndMs)
        assertEquals(3_000L, middle.trimStartMs)
        assertEquals(7_000L, middle.trimEndMs)
        assertEquals(7_000L, post.trimStartMs)
        assertEquals(10_000L, post.trimEndMs)
        assertNotEquals("a", middle.id)
        assertNotEquals("a", post.id)
    }

    @Test
    fun `pre is omitted when range starts at trim start`() = runTest {
        repo.addSegment(videoSeg(id = "a", order = 0, durationMs = 10_000L))

        val result = useCase("a", 0L, 4_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(2, all.size)
        assertNull(result.pre)
        assertNotNull(result.post)

        val middle = all[0]
        val post = all[1]
        assertEquals("a", middle.id)
        assertEquals(0L, middle.trimStartMs)
        assertEquals(4_000L, middle.trimEndMs)
        assertEquals(4_000L, post.trimStartMs)
        assertEquals(10_000L, post.trimEndMs)
    }

    @Test
    fun `post is omitted when range ends at trim end`() = runTest {
        repo.addSegment(videoSeg(id = "a", order = 0, durationMs = 10_000L))

        val result = useCase("a", 6_000L, 10_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(2, all.size)
        assertNotNull(result.pre)
        assertNull(result.post)

        val pre = all[0]
        val middle = all[1]
        assertEquals("a", pre.id)
        assertEquals(6_000L, pre.trimEndMs)
        assertEquals(6_000L, middle.trimStartMs)
        assertEquals(10_000L, middle.trimEndMs)
    }

    @Test
    fun `both omitted when range equals full trim`() = runTest {
        repo.addSegment(videoSeg(id = "a", order = 0, durationMs = 5_000L))

        val result = useCase("a", 0L, 5_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(1, all.size)
        assertNull(result.pre)
        assertNull(result.post)
        assertEquals("a", result.middle.id)
    }

    @Test
    fun `following segments are shifted by number of new inserts`() = runTest {
        repo.addSegment(videoSeg(id = "a", order = 0, durationMs = 10_000L))
        repo.addSegment(videoSeg(id = "b", order = 1, durationMs = 3_000L))
        repo.addSegment(videoSeg(id = "c", order = 2, durationMs = 3_000L))

        useCase("a", 3_000L, 7_000L) // creates middle+post → shift b,c by 2

        val all = repo.getByProjectId("p1")
        assertEquals(5, all.size)
        val byId = all.associateBy { it.id }
        assertEquals(3, byId["b"]!!.order)
        assertEquals(4, byId["c"]!!.order)
    }

    @Test
    fun `throws when range is less than 100ms`() = runTest {
        repo.addSegment(videoSeg(id = "a", durationMs = 10_000L))
        try {
            useCase("a", 1_000L, 1_050L)
            throw AssertionError("should throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `throws for IMAGE segment`() = runTest {
        val img = Segment(
            id = "img1",
            projectId = "p1",
            type = SegmentType.IMAGE,
            order = 0,
            sourceUri = "content://img",
            durationMs = 3_000L,
            width = 1080,
            height = 1920
        )
        repo.addSegment(img)
        try {
            useCase("img1", 0L, 1_000L)
            throw AssertionError("should throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `preserves volumeScale and speedScale across split pieces`() = runTest {
        repo.addSegment(
            videoSeg(
                id = "a",
                order = 0,
                durationMs = 10_000L,
                volumeScale = 1.5f,
                speedScale = 0.5f
            )
        )

        useCase("a", 3_000L, 7_000L)

        val all = repo.getByProjectId("p1")
        assertTrue(all.all { it.volumeScale == 1.5f })
        assertTrue(all.all { it.speedScale == 0.5f })
    }

    @Test
    fun `respects non-zero trimStart in local coordinates`() = runTest {
        // Original trim window is [2_000, 8_000] within a 10s source.
        repo.addSegment(
            videoSeg(
                id = "a",
                order = 0,
                durationMs = 10_000L,
                trimStartMs = 2_000L,
                trimEndMs = 8_000L
            )
        )

        val result = useCase("a", 4_000L, 6_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(3, all.size)
        val pre = all[0]
        val middle = all[1]
        val post = all[2]
        assertEquals(2_000L, pre.trimStartMs)
        assertEquals(4_000L, pre.trimEndMs)
        assertEquals(4_000L, middle.trimStartMs)
        assertEquals(6_000L, middle.trimEndMs)
        assertEquals(6_000L, post.trimStartMs)
        assertEquals(8_000L, post.trimEndMs)
        assertNotNull(result.pre)
        assertNotNull(result.post)
    }

    @Test
    fun `clamps range inside trim window`() = runTest {
        repo.addSegment(
            videoSeg(
                id = "a",
                order = 0,
                durationMs = 10_000L,
                trimStartMs = 2_000L,
                trimEndMs = 8_000L
            )
        )

        // Requested range goes outside [2000, 8000] — should clamp.
        useCase("a", 0L, 9_000L)

        val all = repo.getByProjectId("p1")
        assertEquals(1, all.size) // entire trim window, no pre/post
        assertEquals(2_000L, all[0].trimStartMs)
        assertEquals(8_000L, all[0].trimEndMs)
    }
}
