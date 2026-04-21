package com.example.dubcast.domain.usecase.bgm

import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.fake.FakeBgmClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeleteBgmClipUseCaseTest {

    private lateinit var repo: FakeBgmClipRepository
    private lateinit var useCase: DeleteBgmClipUseCase

    @Before
    fun setup() = runTest {
        repo = FakeBgmClipRepository()
        repo.addClip(BgmClip("b1", "p1", "content://x", 1000L, 0L))
        useCase = DeleteBgmClipUseCase(repo)
    }

    @Test
    fun `removes clip`() = runTest {
        useCase("b1")
        assertNull(repo.getClip("b1"))
    }

    @Test
    fun `silent on unknown id`() = runTest {
        useCase("missing")
    }
}
