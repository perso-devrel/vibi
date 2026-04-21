package com.example.dubcast.domain.usecase.text

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.fake.FakeTextOverlayRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeleteTextOverlayUseCaseTest {

    private lateinit var repo: FakeTextOverlayRepository
    private lateinit var useCase: DeleteTextOverlayUseCase

    @Before
    fun setup() = runTest {
        repo = FakeTextOverlayRepository()
        repo.addOverlay(
            TextOverlay(id = "o1", projectId = "p1", text = "x", startMs = 0L, endMs = 1000L)
        )
        useCase = DeleteTextOverlayUseCase(repo)
    }

    @Test
    fun `removes overlay by id`() = runTest {
        useCase("o1")
        assertNull(repo.getOverlay("o1"))
    }

    @Test
    fun `silent on unknown id`() = runTest {
        useCase("missing") // should not throw
    }
}
