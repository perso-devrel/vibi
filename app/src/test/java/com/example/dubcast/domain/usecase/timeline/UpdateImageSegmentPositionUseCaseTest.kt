package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateImageSegmentPositionUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: UpdateImageSegmentPositionUseCase

    private val imageSegment = Segment(
        id = "img",
        projectId = "p1",
        type = SegmentType.IMAGE,
        order = 0,
        sourceUri = "content://img",
        durationMs = 3000L,
        width = 1024,
        height = 768
    )

    @Before
    fun setup() = runTest {
        repo = FakeSegmentRepository()
        repo.addSegment(imageSegment)
        useCase = UpdateImageSegmentPositionUseCase(repo)
    }

    @Test
    fun `clamps out-of-range values`() = runTest {
        useCase("img", xPct = 150f, yPct = -5f, widthPct = 200f, heightPct = 1f)
        val updated = repo.getSegment("img")!!
        assertEquals(100f, updated.imageXPct)
        assertEquals(0f, updated.imageYPct)
        assertEquals(100f, updated.imageWidthPct)
        assertEquals(5f, updated.imageHeightPct)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects video segment`() = runTest {
        val vid = imageSegment.copy(id = "vid", type = SegmentType.VIDEO)
        repo.addSegment(vid)
        useCase("vid", 50f, 50f, 50f, 50f)
    }
}
