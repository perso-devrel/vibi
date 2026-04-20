package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AddVideoSegmentUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: AddVideoSegmentUseCase

    private fun info(duration: Long = 10_000L) = VideoInfo(
        uri = "content://v.mp4",
        fileName = "v.mp4",
        mimeType = "video/mp4",
        durationMs = duration,
        width = 1280,
        height = 720,
        sizeBytes = 1024L
    )

    @Before
    fun setup() {
        repo = FakeSegmentRepository()
        useCase = AddVideoSegmentUseCase(repo)
    }

    @Test
    fun `first video segment gets order 0`() = runTest {
        val segment = useCase("p1", info())
        assertEquals(0, segment.order)
        assertEquals(SegmentType.VIDEO, segment.type)
    }

    @Test
    fun `subsequent segments increment order`() = runTest {
        useCase("p1", info())
        val second = useCase("p1", info())
        val third = useCase("p1", info())
        assertEquals(1, second.order)
        assertEquals(2, third.order)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero duration`() = runTest {
        useCase("p1", info(duration = 0L))
    }
}
