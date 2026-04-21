package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.fake.FakeEditProjectRepository
import com.example.dubcast.fake.FakeSegmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SetProjectFrameUseCaseTest {

    private lateinit var projectRepo: FakeEditProjectRepository
    private lateinit var useCase: SetProjectFrameUseCase

    private val baseProject = EditProject(
        projectId = "p1",
        createdAt = 0L,
        updatedAt = 0L,
        frameWidth = 1920,
        frameHeight = 1080
    )

    @Before
    fun setup() {
        projectRepo = FakeEditProjectRepository(FakeSegmentRepository())
        useCase = SetProjectFrameUseCase(projectRepo)
    }

    @Test
    fun `updates frame width height and background color`() = runTest {
        projectRepo.createProject(baseProject)

        useCase("p1", width = 1080, height = 1920, backgroundColorHex = "#112233")

        val updated = projectRepo.getProject("p1")!!
        assertEquals(1080, updated.frameWidth)
        assertEquals(1920, updated.frameHeight)
        assertEquals("#112233", updated.backgroundColorHex)
    }

    @Test
    fun `keeps existing background color when null is passed`() = runTest {
        projectRepo.createProject(baseProject.copy(backgroundColorHex = "#FF0000"))

        useCase("p1", width = 720, height = 1280, backgroundColorHex = null)

        val updated = projectRepo.getProject("p1")!!
        assertEquals(720, updated.frameWidth)
        assertEquals(1280, updated.frameHeight)
        assertEquals("#FF0000", updated.backgroundColorHex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive width`() = runTest {
        projectRepo.createProject(baseProject)
        useCase("p1", width = 0, height = 1080, backgroundColorHex = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive height`() = runTest {
        projectRepo.createProject(baseProject)
        useCase("p1", width = 1920, height = -1, backgroundColorHex = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed background color`() = runTest {
        projectRepo.createProject(baseProject)
        useCase("p1", width = 1920, height = 1080, backgroundColorHex = "not-a-color")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown projectId`() = runTest {
        useCase("missing", width = 1920, height = 1080, backgroundColorHex = null)
    }
}
