package com.vibi.shared.domain.usecase.export

import com.vibi.shared.domain.model.SegmentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HybridRenderExecutorTest {

    private fun seg(path: String, order: Int = 0) = SegmentInput(
        sourceFilePath = path,
        type = SegmentType.VIDEO,
        order = order,
        durationMs = 5000,
        trimStartMs = 0,
        trimEndMs = 5000,
        width = 1920,
        height = 1080,
    )

    private class FakeOnDevice(
        private val result: Result<String> = Result.success("/tmp/ondevice.mp4"),
        private val throwCancellation: Boolean = false,
    ) : OnDeviceVideoEncoder {
        var called = false
        override suspend fun export(
            segments: List<SegmentInput>,
            outputPath: String,
            onProgress: (percent: Int) -> Unit,
        ): Result<String> {
            called = true
            if (throwCancellation) throw CancellationException("cancelled")
            return result
        }
    }

    private class FakeFallback(
        private val result: Result<String> = Result.success("/tmp/server.mp4"),
    ) : FfmpegExecutor {
        var called = false
        override suspend fun renderProject(
            segments: List<SegmentInput>,
            outputPath: String,
            frame: FrameInput?,
            bgmClips: List<BgmClipMixInput>,
            separationDirectives: List<SeparationDirectiveInput>,
            onProgress: (percent: Int) -> Unit,
        ): Result<String> {
            called = true
            return result
        }
    }

    @Test
    fun `단일 소스 영상전용 편집 - 온디바이스 사용, 서버 미호출`() = runTest {
        val onDevice = FakeOnDevice(Result.success("/tmp/ondevice.mp4"))
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        val result = executor.renderProject(
            segments = listOf(seg("a.mp4", 0), seg("a.mp4", 1)),
            outputPath = "/tmp/out.mp4",
            onProgress = {},
        )

        assertEquals("/tmp/ondevice.mp4", result.getOrNull())
        assertTrue(onDevice.called)
        assertFalse(fallback.called)
    }

    @Test
    fun `BGM 있으면 서버 fallback`() = runTest {
        val onDevice = FakeOnDevice()
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        executor.renderProject(
            segments = listOf(seg("a.mp4")),
            outputPath = "/tmp/out.mp4",
            bgmClips = listOf(BgmClipMixInput(audioFilePath = "bgm.m4a", startMs = 0)),
            onProgress = {},
        )

        assertFalse(onDevice.called)
        assertTrue(fallback.called)
    }

    @Test
    fun `음원분리 있으면 서버 fallback`() = runTest {
        val onDevice = FakeOnDevice()
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        executor.renderProject(
            segments = listOf(seg("a.mp4")),
            outputPath = "/tmp/out.mp4",
            separationDirectives = listOf(
                SeparationDirectiveInput(
                    id = "d1", rangeStartMs = 0, rangeEndMs = 1000, numberOfSpeakers = 2,
                    muteOriginalSegmentAudio = true,
                    selections = listOf(SeparationStemInput("s1", "http://x", 1.0f)),
                ),
            ),
            onProgress = {},
        )

        assertFalse(onDevice.called)
        assertTrue(fallback.called)
    }

    @Test
    fun `실제 reframe(소스와 다른 치수)은 서버 fallback`() = runTest {
        val onDevice = FakeOnDevice()
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        executor.renderProject(
            // seg 는 1920x1080, frame 은 1080x1080 → 실제 리사이즈/letterbox 필요.
            segments = listOf(seg("a.mp4")),
            outputPath = "/tmp/out.mp4",
            frame = FrameInput(width = 1080, height = 1080),
            onProgress = {},
        )

        assertFalse(onDevice.called)
        assertTrue(fallback.called)
    }

    @Test
    fun `frame 이 소스 치수 그대로(passthrough)면 온디바이스`() = runTest {
        val onDevice = FakeOnDevice(Result.success("/tmp/ondevice.mp4"))
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        val result = executor.renderProject(
            // 프로젝트 생성 시 frame 은 항상 소스 치수(videoInfo)로 설정됨 — no-op 이라 온디바이스 가능.
            segments = listOf(seg("a.mp4")),
            outputPath = "/tmp/out.mp4",
            frame = FrameInput(width = 1920, height = 1080),
            onProgress = {},
        )

        assertEquals("/tmp/ondevice.mp4", result.getOrNull())
        assertTrue(onDevice.called)
        assertFalse(fallback.called)
    }

    @Test
    fun `다중 소스 concat 은 서버 fallback`() = runTest {
        val onDevice = FakeOnDevice()
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        executor.renderProject(
            segments = listOf(seg("a.mp4", 0), seg("b.mp4", 1)),
            outputPath = "/tmp/out.mp4",
            onProgress = {},
        )

        assertFalse(onDevice.called)
        assertTrue(fallback.called)
    }

    @Test
    fun `온디바이스 인코더 없으면(Android) 항상 서버`() = runTest {
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice = null, fallback = fallback)

        executor.renderProject(
            segments = listOf(seg("a.mp4")),
            outputPath = "/tmp/out.mp4",
            onProgress = {},
        )

        assertTrue(fallback.called)
    }

    @Test
    fun `온디바이스 실패하면 서버로 fallback`() = runTest {
        val onDevice = FakeOnDevice(Result.failure(RuntimeException("codec error")))
        val fallback = FakeFallback(Result.success("/tmp/server.mp4"))
        val executor = HybridRenderExecutor(onDevice, fallback)

        val result = executor.renderProject(
            segments = listOf(seg("a.mp4")),
            outputPath = "/tmp/out.mp4",
            onProgress = {},
        )

        assertTrue(onDevice.called)
        assertTrue(fallback.called)
        assertEquals("/tmp/server.mp4", result.getOrNull())
    }

    @Test
    fun `온디바이스 취소는 전파되고 서버 fallback 안 함`() = runTest {
        val onDevice = FakeOnDevice(throwCancellation = true)
        val fallback = FakeFallback()
        val executor = HybridRenderExecutor(onDevice, fallback)

        assertFailsWith<CancellationException> {
            executor.renderProject(
                segments = listOf(seg("a.mp4")),
                outputPath = "/tmp/out.mp4",
                onProgress = {},
            )
        }
        assertFalse(fallback.called)
    }
}
