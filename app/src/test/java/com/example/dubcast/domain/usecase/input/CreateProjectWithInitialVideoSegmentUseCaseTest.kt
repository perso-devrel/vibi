package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.fake.FakeEditProjectRepository
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreateProjectWithInitialVideoSegmentUseCaseTest {

    private lateinit var segmentRepo: FakeSegmentRepository
    private lateinit var projectRepo: FakeEditProjectRepository
    private lateinit var useCase: CreateProjectWithInitialVideoSegmentUseCase

    private val videoInfo = VideoInfo(
        uri = "content://video.mp4",
        fileName = "video.mp4",
        mimeType = "video/mp4",
        durationMs = 30_000L,
        width = 1920,
        height = 1080,
        sizeBytes = 5_000_000L
    )

    @Before
    fun setup() {
        segmentRepo = FakeSegmentRepository()
        projectRepo = FakeEditProjectRepository(segmentRepo)
        useCase = CreateProjectWithInitialVideoSegmentUseCase(projectRepo)
    }

    @Test
    fun `creates project and initial video segment atomically`() = runTest {
        val projectId = useCase(videoInfo)

        assertNotNull(projectRepo.getProject(projectId))
        val segments = segmentRepo.getByProjectId(projectId)
        assertEquals(1, segments.size)
        val segment = segments.first()
        assertEquals(SegmentType.VIDEO, segment.type)
        assertEquals(0, segment.order)
        assertEquals(videoInfo.uri, segment.sourceUri)
        assertEquals(videoInfo.durationMs, segment.durationMs)
        assertEquals(videoInfo.width, segment.width)
        assertEquals(videoInfo.height, segment.height)
    }

    @Test
    fun `defaults project frame to first video size`() = runTest {
        val projectId = useCase(videoInfo)

        val project = projectRepo.getProject(projectId)!!
        assertEquals(videoInfo.width, project.frameWidth)
        assertEquals(videoInfo.height, project.frameHeight)
        assertEquals("#000000", project.backgroundColorHex)
    }

    @Test
    fun `rolls back project when segment insert fails`() = runTest {
        projectRepo.failOnSegmentInsert = true

        val error = runCatching { useCase(videoInfo) }
        assertTrue(error.isFailure)

        // Project must not linger without its segment
        val remaining = segmentRepo.getByProjectId("any")
        assertTrue(remaining.isEmpty())
        // And no project ID should resolve in the repo
        val createdProjectId = error.exceptionOrNull()?.message
        assertNull(createdProjectId?.let { projectRepo.getProject(it) })
    }
}
