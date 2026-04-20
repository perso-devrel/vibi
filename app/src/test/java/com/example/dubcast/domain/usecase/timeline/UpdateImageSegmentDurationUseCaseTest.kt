package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateImageSegmentDurationUseCaseTest {

    private lateinit var repo: FakeSegmentRepository
    private lateinit var useCase: UpdateImageSegmentDurationUseCase

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
        useCase = UpdateImageSegmentDurationUseCase(repo)
    }

    @Test
    fun `updates duration`() = runTest {
        useCase("img", 5_000L)
        assertEquals(5_000L, repo.getSegment("img")!!.durationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duration below minimum`() = runTest {
        useCase("img", 100L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects updating video segment`() = runTest {
        val videoSegment = imageSegment.copy(id = "vid", type = SegmentType.VIDEO)
        repo.addSegment(videoSegment)
        useCase("vid", 2_000L)
    }
}
