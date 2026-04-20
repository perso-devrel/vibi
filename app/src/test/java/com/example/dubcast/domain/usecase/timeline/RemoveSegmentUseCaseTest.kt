package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RemoveSegmentUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: RemoveSegmentUseCase

    private fun seg(id: String, order: Int) = Segment(
        id = id,
        projectId = "p1",
        type = SegmentType.VIDEO,
        order = order,
        sourceUri = "content://$id",
        durationMs = 1000L,
        width = 1920,
        height = 1080
    )

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        useCase = RemoveSegmentUseCase(repo)
    }

    @Test
    fun `removes segment and compacts orders`() = runTest {
        repo.addSegment(seg("a", 0))
        repo.addSegment(seg("b", 1))
        repo.addSegment(seg("c", 2))

        useCase("b")

        val remaining = repo.getByProjectId("p1")
        assertEquals(2, remaining.size)
        assertEquals("a", remaining[0].id)
        assertEquals(0, remaining[0].order)
        assertEquals("c", remaining[1].id)
        assertEquals(1, remaining[1].order)
    }

    @Test
    fun `does nothing when segment not found`() = runTest {
        repo.addSegment(seg("a", 0))
        useCase("missing")
        assertEquals(1, repo.getByProjectId("p1").size)
    }
}
