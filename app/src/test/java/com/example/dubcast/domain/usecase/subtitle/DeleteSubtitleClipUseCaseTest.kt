package com.example.dubcast.domain.usecase.subtitle

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.fake.FakeSubtitleClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeleteSubtitleClipUseCaseTest {

    private lateinit var repository: FakeSubtitleClipRepository
    private lateinit var useCase: DeleteSubtitleClipUseCase

    @Before
    fun setup() {
        repository = FakeSubtitleClipRepository()
        useCase = DeleteSubtitleClipUseCase(repository)
    }

    @Test
    fun `deletes clip from repository`() = runTest {
        val clip = SubtitleClip(
            id = "sub-1",
            projectId = "project-1",
            text = "Hello",
            startMs = 1000L,
            endMs = 5000L,
            position = SubtitlePosition(Anchor.BOTTOM, 90f)
        )
        repository.addClip(clip)

        useCase("sub-1")

        assertNull(repository.getClip("sub-1"))
    }
}
