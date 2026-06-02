package com.vibi.shared.domain.usecase.save

import com.vibi.shared.data.remote.AssetUploader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PrewarmAssetUploadUseCaseTest {

    /** ensureUploaded 호출 (path, ext, contentType) 을 기록하는 fake. throwOn 경로는 예외. */
    private class FakeUploader(
        private val throwOn: Set<String> = emptySet(),
    ) : AssetUploader {
        val calls = mutableListOf<Triple<String, String, String>>()

        override suspend fun ensureUploaded(localPath: String, ext: String, contentType: String): String {
            calls += Triple(localPath, ext, contentType)
            if (localPath in throwOn) error("boom: $localPath")
            return "key:$localPath"
        }
    }

    @Test
    fun `영상 - distinct 경로마다 mp4 로 1회 호출`() = runTest {
        val uploader = FakeUploader()
        PrewarmAssetUploadUseCase(uploader).prewarmVideos(listOf("a.mp4", "b.mp4", "a.mp4"))

        assertEquals(
            listOf(
                Triple("a.mp4", "mp4", "video/mp4"),
                Triple("b.mp4", "mp4", "video/mp4"),
            ),
            uploader.calls,
        )
    }

    @Test
    fun `BGM - 확장자별 ext content-type 추론`() = runTest {
        val uploader = FakeUploader()
        PrewarmAssetUploadUseCase(uploader).prewarmAudio(listOf("song.mp3", "voice.m4a", "weird.xyz"))

        assertEquals(
            listOf(
                Triple("song.mp3", "mp3", "audio/mpeg"),
                Triple("voice.m4a", "m4a", "audio/mp4"),
                Triple("weird.xyz", "m4a", "audio/mp4"), // 미지원 확장자 → m4a 폴백
            ),
            uploader.calls,
        )
    }

    @Test
    fun `중간 실패해도 나머지 진행하고 throw 안 함 - best-effort`() = runTest {
        val uploader = FakeUploader(throwOn = setOf("a.mp4"))

        // 예외가 전파되면 이 줄에서 테스트 실패.
        PrewarmAssetUploadUseCase(uploader).prewarmVideos(listOf("a.mp4", "b.mp4"))

        assertEquals(listOf("a.mp4", "b.mp4"), uploader.calls.map { it.first })
    }

    @Test
    fun `빈 경로는 건너뛴다`() = runTest {
        val uploader = FakeUploader()
        PrewarmAssetUploadUseCase(uploader).prewarmVideos(listOf("", "  ".trim(), "a.mp4"))

        assertEquals(listOf("a.mp4"), uploader.calls.map { it.first })
    }

    @Test
    fun `빈 리스트면 호출 0회`() = runTest {
        val uploader = FakeUploader()
        PrewarmAssetUploadUseCase(uploader).prewarmVideos(emptyList())

        assertEquals(emptyList(), uploader.calls)
    }
}
