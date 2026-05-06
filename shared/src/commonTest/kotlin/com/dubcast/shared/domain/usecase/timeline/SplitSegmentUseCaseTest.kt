package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitSegmentUseCaseTest {

    private class FakeSegmentRepository : SegmentRepository {
        val store = mutableMapOf<String, Segment>()
        override fun observeByProjectId(projectId: String): Flow<List<Segment>> =
            flowOf(store.values.filter { it.projectId == projectId }.sortedBy { it.order })
        override suspend fun getByProjectId(projectId: String): List<Segment> =
            store.values.filter { it.projectId == projectId }.sortedBy { it.order }
        override suspend fun getSegment(id: String): Segment? = store[id]
        override suspend fun addSegment(segment: Segment) { store[segment.id] = segment }
        override suspend fun updateSegment(segment: Segment) { store[segment.id] = segment }
        override suspend fun deleteSegment(id: String) { store.remove(id) }
        override suspend fun deleteAllByProjectId(projectId: String) {
            store.values.filter { it.projectId == projectId }.forEach { store.remove(it.id) }
        }
        override suspend fun getMaxOrder(projectId: String): Int =
            store.values.filter { it.projectId == projectId }.maxOfOrNull { it.order } ?: -1
        override suspend fun getFirstSourceUri(projectId: String): String? =
            store.values.filter { it.projectId == projectId }.minByOrNull { it.order }?.sourceUri
    }

    private fun videoSegment(
        id: String = "seg",
        order: Int = 0,
        durationMs: Long = 10_000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 10_000L,
    ) = Segment(
        id = id,
        projectId = "p1",
        type = SegmentType.VIDEO,
        order = order,
        sourceUri = "content://video",
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
    )

    @Test
    fun `normal split produces pre middle post all above MIN_FRAGMENT`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment()
        val useCase = SplitSegmentUseCase(repo)

        val r = useCase("seg", 2_000L, 7_000L)

        assertNotNull(r.pre)
        assertNotNull(r.post)
        assertEquals(0L, r.pre!!.trimStartMs)
        assertEquals(2_000L, r.pre.trimEndMs)
        assertEquals(2_000L, r.middle.trimStartMs)
        assertEquals(7_000L, r.middle.trimEndMs)
        assertEquals(7_000L, r.post!!.trimStartMs)
        assertEquals(10_000L, r.post.trimEndMs)
    }

    @Test
    fun `pre below MIN_FRAGMENT is absorbed into middle starting at original trimStart`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment()
        val useCase = SplitSegmentUseCase(repo)

        // pre candidate = 30ms (< MIN_FRAGMENT_MS=50)
        val r = useCase("seg", 30L, 7_000L)

        // pre 가 흡수되면 needsPre=false → 원본 segment 그대로 update (id 동일)
        assertNull(r.pre)
        assertEquals("seg", r.middle.id)
        assertEquals(0L, r.middle.trimStartMs)
        assertEquals(7_000L, r.middle.trimEndMs)
        assertNotNull(r.post)
    }

    @Test
    fun `post below MIN_FRAGMENT is absorbed into middle ending at original trimEnd`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment()
        val useCase = SplitSegmentUseCase(repo)

        // post candidate = 5ms (< MIN_FRAGMENT_MS=50)
        val r = useCase("seg", 2_000L, 9_995L)

        assertNotNull(r.pre)
        assertNull(r.post)
        assertEquals(2_000L, r.middle.trimStartMs)
        assertEquals(10_000L, r.middle.trimEndMs)
    }

    @Test
    fun `both pre and post below MIN_FRAGMENT collapse split into full segment edit`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment()
        val useCase = SplitSegmentUseCase(repo)

        // pre=20ms, post=10ms, middle=9_970ms
        val r = useCase("seg", 20L, 9_990L)

        assertNull(r.pre)
        assertNull(r.post)
        // 원본 segment 그대로 (split 없음 — middle 이 segment 전체로 확장)
        assertEquals("seg", r.middle.id)
        assertEquals(0L, r.middle.trimStartMs)
        assertEquals(10_000L, r.middle.trimEndMs)
    }

    @Test
    fun `range below MIN_RANGE rejects with IllegalArgumentException`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment()
        val useCase = SplitSegmentUseCase(repo)

        // middle 길이 = 10ms (< MIN_RANGE_MS=100)
        val ex = assertFails { useCase("seg", 2_000L, 2_010L) }
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `following segments order shifted by inserted fragment count`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment(order = 0)
        repo.store["next"] = videoSegment(id = "next", order = 1)
        val useCase = SplitSegmentUseCase(repo)

        // pre+middle+post 모두 생성 → 새 insert 2개
        useCase("seg", 2_000L, 7_000L)

        assertEquals(3, repo.store["next"]!!.order)
    }

    @Test
    fun `pre absorbed only inserts post so following shifts by 1`() = runTest {
        val repo = FakeSegmentRepository()
        repo.store["seg"] = videoSegment(order = 0)
        repo.store["next"] = videoSegment(id = "next", order = 1)
        val useCase = SplitSegmentUseCase(repo)

        // pre=30ms 흡수 → post 만 새 insert (1개)
        useCase("seg", 30L, 7_000L)

        assertEquals(2, repo.store["next"]!!.order)
    }
}
