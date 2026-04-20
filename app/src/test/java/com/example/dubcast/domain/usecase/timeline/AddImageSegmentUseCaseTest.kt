package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.ImageInfo
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AddImageSegmentUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: AddImageSegmentUseCase

    private val info = ImageInfo(uri = "content://img.jpg", width = 1024, height = 768)

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        useCase = AddImageSegmentUseCase(repo)
    }

    @Test
    fun `default duration is 3 seconds and default coords are centered at 50 percent`() = runTest {
        val seg = useCase("p1", info)
        assertEquals(SegmentType.IMAGE, seg.type)
        assertEquals(3_000L, seg.durationMs)
        assertEquals(50f, seg.imageXPct)
        assertEquals(50f, seg.imageYPct)
        assertEquals(50f, seg.imageWidthPct)
        assertEquals(50f, seg.imageHeightPct)
    }

    @Test
    fun `order increments past existing segments`() = runTest {
        useCase("p1", info)
        useCase("p1", info)
        val third = useCase("p1", info, durationMs = 2_000L)
        assertEquals(2, third.order)
        assertEquals(2_000L, third.durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duration below minimum`() = runTest {
        useCase("p1", info, durationMs = 100L)
    }
}
