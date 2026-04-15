package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.fake.FakeFfmpegExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExportWithDubbingUseCaseTest {

    private lateinit var ffmpegExecutor: FakeFfmpegExecutor
    private lateinit var assGenerator: AssGenerator
    private lateinit var useCase: ExportWithDubbingUseCase

    @Before
    fun setup() {
        ffmpegExecutor = FakeFfmpegExecutor()
        assGenerator = AssGenerator()
        useCase = ExportWithDubbingUseCase(assGenerator, ffmpegExecutor)
    }

    @Test
    fun `exports with dub clips and no subtitles`() = runTest {
        val clips = listOf(
            DubClip("c1", "p1", "Hello", "v1", "Rachel", "/audio1.mp3", 1000L, 3000L),
            DubClip("c2", "p1", "World", "v1", "Rachel", "/audio2.mp3", 5000L, 2000L)
        )

        val result = useCase.execute(
            inputVideoPath = "/input.mp4",
            dubClips = clips,
            subtitleClips = emptyList(),
            videoWidth = 1920,
            videoHeight = 1080,
            videoDurationMs = 30000L,
            outputPath = "/output.mp4",
            assFilePath = null,
            fontDir = null,
            onProgress = {}
        )

        assertTrue(result.isSuccess)
        assertNotNull(ffmpegExecutor.lastMixInputs)
        assertEquals(2, ffmpegExecutor.lastMixInputs!!.size)
        assertEquals(1000L, ffmpegExecutor.lastMixInputs!![0].startMs)
        assertEquals(5000L, ffmpegExecutor.lastMixInputs!![1].startMs)
    }

    @Test
    fun `exports with subtitles only`() = runTest {
        val subtitles = listOf(
            SubtitleClip("s1", "p1", "자막", 0L, 3000L, SubtitlePosition(Anchor.BOTTOM, 90f))
        )

        val result = useCase.execute(
            inputVideoPath = "/input.mp4",
            dubClips = emptyList(),
            subtitleClips = subtitles,
            videoWidth = 1920,
            videoHeight = 1080,
            videoDurationMs = 30000L,
            outputPath = "/output.mp4",
            assFilePath = System.getProperty("java.io.tmpdir") + "/test.ass",
            fontDir = "/fonts",
            onProgress = {}
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `returns failure when ffmpeg fails`() = runTest {
        ffmpegExecutor.mixResult = Result.failure(RuntimeException("FFmpeg error"))

        val clips = listOf(
            DubClip("c1", "p1", "Hello", "v1", "Rachel", "/audio1.mp3", 1000L, 3000L)
        )

        val result = useCase.execute(
            inputVideoPath = "/input.mp4",
            dubClips = clips,
            subtitleClips = emptyList(),
            videoWidth = 1920,
            videoHeight = 1080,
            videoDurationMs = 30000L,
            outputPath = "/output.mp4",
            assFilePath = null,
            fontDir = null,
            onProgress = {}
        )

        assertTrue(result.isFailure)
    }
}
