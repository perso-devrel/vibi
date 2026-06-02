package com.vibi.shared.domain.usecase.save

import com.vibi.shared.data.remote.AssetUploader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PrewarmAssetUploadUseCaseTest {

    /** ensureUploaded 호출 경로를 기록하는 fake. throwOn 에 든 경로는 예외를 던진다. */
    private class FakeUploader(
        private val throwOn: Set<String> = emptySet(),
    ) : AssetUploader {
        val calls = mutableListOf<String>()

        override suspend fun ensureUploaded(localPath: String, ext: String, contentType: String): String {
            calls += localPath
            if (localPath in throwOn) error("boom: $localPath")
            return "key:$localPath"
        }
    }

    @Test
    fun `distinct 경로마다 ensureUploaded 1회 호출`() = runTest {
        val uploader = FakeUploader()
        val useCase = PrewarmAssetUploadUseCase(uploader)

        useCase(listOf("a.mp4", "b.mp4", "a.mp4"))

        assertEquals(listOf("a.mp4", "b.mp4"), uploader.calls)
    }

    @Test
    fun `중간 실패해도 나머지 진행하고 throw 안 함 - best-effort`() = runTest {
        val uploader = FakeUploader(throwOn = setOf("a.mp4"))
        val useCase = PrewarmAssetUploadUseCase(uploader)

        // 예외가 전파되면 이 줄에서 테스트 실패.
        useCase(listOf("a.mp4", "b.mp4"))

        assertEquals(listOf("a.mp4", "b.mp4"), uploader.calls)
    }

    @Test
    fun `빈 경로는 건너뛴다`() = runTest {
        val uploader = FakeUploader()
        val useCase = PrewarmAssetUploadUseCase(uploader)

        useCase(listOf("", "  ".trim(), "a.mp4"))

        assertEquals(listOf("a.mp4"), uploader.calls)
    }

    @Test
    fun `빈 리스트면 호출 0회`() = runTest {
        val uploader = FakeUploader()
        val useCase = PrewarmAssetUploadUseCase(uploader)

        useCase(emptyList())

        assertEquals(emptyList(), uploader.calls)
    }
}
