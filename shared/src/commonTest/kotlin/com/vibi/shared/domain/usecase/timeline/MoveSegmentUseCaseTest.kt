package com.vibi.shared.domain.usecase.timeline

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveSegmentUseCaseTest {

    private fun repoWith(vararg ids: String): FakeSegmentRepository {
        val repo = FakeSegmentRepository()
        ids.forEachIndexed { i, id ->
            repo.store[id] = Segment(
                id = id, projectId = "p", type = SegmentType.VIDEO, order = i,
                sourceUri = "u", durationMs = 1000L, width = 1, height = 1,
            )
        }
        return repo
    }

    private suspend fun orderedIds(repo: FakeSegmentRepository) =
        repo.getByProjectId("p").map { it.id }

    @Test
    fun `move first to end`() = runTest {
        val repo = repoWith("a", "b", "c")
        val newOrder = MoveSegmentUseCase(repo)("a", 2)
        assertEquals(2, newOrder)
        assertEquals(listOf("b", "c", "a"), orderedIds(repo))
    }

    @Test
    fun `move last to front`() = runTest {
        val repo = repoWith("a", "b", "c")
        val newOrder = MoveSegmentUseCase(repo)("c", 0)
        assertEquals(0, newOrder)
        assertEquals(listOf("c", "a", "b"), orderedIds(repo))
    }

    @Test
    fun `move middle leaves contiguous 0 to n-1 order`() = runTest {
        val repo = repoWith("a", "b", "c", "d")
        MoveSegmentUseCase(repo)("b", 2)
        assertEquals(listOf("a", "c", "b", "d"), orderedIds(repo))
        assertEquals(listOf(0, 1, 2, 3), repo.getByProjectId("p").map { it.order })
    }

    @Test
    fun `move to same position is no-op`() = runTest {
        val repo = repoWith("a", "b", "c")
        assertNull(MoveSegmentUseCase(repo)("b", 1))
        assertEquals(listOf("a", "b", "c"), orderedIds(repo))
    }

    @Test
    fun `targetIndex beyond end clamps to append`() = runTest {
        val repo = repoWith("a", "b", "c")
        MoveSegmentUseCase(repo)("a", 99)
        assertEquals(listOf("b", "c", "a"), orderedIds(repo))
    }

    @Test
    fun `single segment is no-op`() = runTest {
        val repo = repoWith("a")
        assertNull(MoveSegmentUseCase(repo)("a", 0))
    }

    @Test
    fun `unknown id returns null`() = runTest {
        val repo = repoWith("a", "b")
        assertNull(MoveSegmentUseCase(repo)("zzz", 0))
    }
}
