package com.example.dubcast.domain.usecase.tts

import com.example.dubcast.domain.repository.TtsResult
import com.example.dubcast.fake.FakeDubClipRepository
import com.example.dubcast.fake.FakeTtsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SynthesizeDubClipUseCaseTest {

    private lateinit var ttsRepository: FakeTtsRepository
    private lateinit var dubClipRepository: FakeDubClipRepository
    private lateinit var useCase: SynthesizeDubClipUseCase

    @Before
    fun setup() {
        ttsRepository = FakeTtsRepository()
        dubClipRepository = FakeDubClipRepository()
        useCase = SynthesizeDubClipUseCase(ttsRepository, dubClipRepository)
    }

    @Test
    fun `synthesize creates clip and saves to repository`() = runTest {
        ttsRepository.synthesizeResult = Result.success(TtsResult("/audio/test.mp3", 5000L))

        val result = useCase("project-1", "Hello world", "voice-1", "Rachel", 1000L)

        assertTrue(result.isSuccess)
        val clip = result.getOrThrow()
        assertEquals("project-1", clip.projectId)
        assertEquals("Hello world", clip.text)
        assertEquals("voice-1", clip.voiceId)
        assertEquals("Rachel", clip.voiceName)
        assertEquals("/audio/test.mp3", clip.audioFilePath)
        assertEquals(1000L, clip.startMs)
        assertEquals(5000L, clip.durationMs)

        val saved = dubClipRepository.observeClips("project-1").first()
        assertEquals(1, saved.size)
        assertEquals(clip.id, saved[0].id)
    }

    @Test
    fun `synthesize returns failure when tts fails`() = runTest {
        ttsRepository.synthesizeResult = Result.failure(RuntimeException("TTS error"))

        val result = useCase("project-1", "Hello", "voice-1", "Rachel", 0L)

        assertTrue(result.isFailure)
        val saved = dubClipRepository.observeClips("project-1").first()
        assertEquals(0, saved.size)
    }
}
