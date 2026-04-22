package com.example.dubcast.domain.usecase.separation

import com.example.dubcast.domain.usecase.bgm.AddBgmClipUseCase
import com.example.dubcast.domain.usecase.input.AudioInfo
import com.example.dubcast.fake.FakeAudioMetadataExtractor
import com.example.dubcast.fake.FakeAudioSeparationRepository
import com.example.dubcast.fake.FakeBgmClipRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplyMixAsBgmUseCaseTest {

    private lateinit var separationRepo: FakeAudioSeparationRepository
    private lateinit var bgmRepo: FakeBgmClipRepository
    private lateinit var extractor: FakeAudioMetadataExtractor
    private lateinit var useCase: ApplyMixAsBgmUseCase

    @Before
    fun setup() {
        separationRepo = FakeAudioSeparationRepository()
        bgmRepo = FakeBgmClipRepository()
        extractor = FakeAudioMetadataExtractor()
        useCase = ApplyMixAsBgmUseCase(
            separationRepository = separationRepo,
            audioMetadataExtractor = extractor,
            addBgmClipUseCase = AddBgmClipUseCase(bgmRepo)
        )
    }

    @Test
    fun `creates bgm clip from downloaded mix`() = runTest {
        separationRepo.mixDownloadResult = Result.success("/cache/mix_mix-1.mp3")
        extractor.nextInfo = AudioInfo(uri = "placeholder", durationMs = 45_000L)

        val result = useCase(
            projectId = "p-1",
            mixJobId = "mix-1",
            downloadUrl = "/api/v2/separate/mix/mix-1/download?token=abc",
            startMs = 2_000L,
            volumeScale = 0.8f
        )

        assertTrue(result.isSuccess)
        val clip = result.getOrThrow()
        assertEquals("/cache/mix_mix-1.mp3", clip.sourceUri)
        assertEquals(45_000L, clip.sourceDurationMs)
        assertEquals(2_000L, clip.startMs)
        assertEquals(0.8f, clip.volumeScale, 0.0001f)
        assertEquals(1, bgmRepo.all().size)
    }

    @Test
    fun `fails when download fails`() = runTest {
        separationRepo.mixDownloadResult = Result.failure(RuntimeException("403 token expired"))

        val result = useCase("p-1", "mix-1", "/url")

        assertTrue(result.isFailure)
        assertTrue(bgmRepo.all().isEmpty())
    }

    @Test
    fun `fails when metadata extraction returns null`() = runTest {
        separationRepo.mixDownloadResult = Result.success("/cache/mix.mp3")
        extractor.nextInfo = null

        val result = useCase("p-1", "mix-1", "/url")

        assertFalse(result.isSuccess)
        assertTrue(bgmRepo.all().isEmpty())
    }
}
