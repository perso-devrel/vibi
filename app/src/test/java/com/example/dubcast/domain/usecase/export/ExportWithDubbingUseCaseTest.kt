package com.example.dubcast.domain.usecase.export

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.model.SegmentType
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

    private val videoSegment = SegmentInput(
        sourceFilePath = "/input.mp4",
        type = SegmentType.VIDEO,
        order = 0,
        durationMs = 30_000L,
        trimStartMs = 0L,
        trimEndMs = 0L,
        width = 1920,
        height = 1080
    )

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
            segments = listOf(videoSegment),
            dubClips = clips,
            subtitleClips = emptyList(),
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
        assertEquals(1, ffmpegExecutor.lastSegments!!.size)
        assertEquals(SegmentType.VIDEO, ffmpegExecutor.lastSegments!![0].type)
    }

    @Test
    fun `exports with subtitles only`() = runTest {
        val subtitles = listOf(
            SubtitleClip("s1", "p1", "자막", 0L, 3000L, SubtitlePosition(Anchor.BOTTOM, 90f))
        )

        val result = useCase.execute(
            segments = listOf(videoSegment),
            dubClips = emptyList(),
            subtitleClips = subtitles,
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
            segments = listOf(videoSegment),
            dubClips = clips,
            subtitleClips = emptyList(),
            outputPath = "/output.mp4",
            assFilePath = null,
            fontDir = null,
            onProgress = {}
        )

        assertTrue(result.isFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty segments`() = runTest {
        useCase.execute(
            segments = emptyList(),
            dubClips = emptyList(),
            subtitleClips = emptyList(),
            outputPath = "/output.mp4",
            assFilePath = null,
            fontDir = null,
            onProgress = {}
        )
    }

    @Test
    fun `forwards bgm clips with resolved local paths and volume to executor`() = runTest {
        val bgms = listOf(
            BgmClip("b1", "p1", "content://song.mp3", sourceDurationMs = 60_000L, startMs = 2000L, volumeScale = 0.5f),
            BgmClip("b2", "p1", "content://intro.mp3", sourceDurationMs = 30_000L, startMs = 0L, volumeScale = 1.0f)
        )
        val resolved = mapOf(
            "content://song.mp3" to "/local/song.mp3",
            "content://intro.mp3" to "/local/intro.mp3"
        )

        useCase.execute(
            segments = listOf(videoSegment),
            dubClips = emptyList(),
            subtitleClips = emptyList(),
            outputPath = "/output.mp4",
            assFilePath = null,
            fontDir = null,
            bgmClips = bgms,
            resolveAudioPath = { uri -> resolved[uri] },
            onProgress = {}
        )

        val passed = ffmpegExecutor.lastBgmInputs!!
        assertEquals(2, passed.size)
        assertEquals("/local/song.mp3", passed[0].audioFilePath)
        assertEquals(2000L, passed[0].startMs)
        assertEquals(0.5f, passed[0].volume)
        assertEquals("/local/intro.mp3", passed[1].audioFilePath)
    }

    @Test
    fun `drops bgm clip when audio path cannot be resolved`() = runTest {
        val bgms = listOf(
            BgmClip("b1", "p1", "content://missing.mp3", sourceDurationMs = 1000L, startMs = 0L)
        )

        useCase.execute(
            segments = listOf(videoSegment),
            dubClips = emptyList(),
            subtitleClips = emptyList(),
            outputPath = "/output.mp4",
            assFilePath = null,
            fontDir = null,
            bgmClips = bgms,
            resolveAudioPath = { null },
            onProgress = {}
        )

        assertTrue(ffmpegExecutor.lastBgmInputs!!.isEmpty())
    }
}
