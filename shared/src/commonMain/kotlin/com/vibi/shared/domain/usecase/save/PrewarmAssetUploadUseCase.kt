package com.vibi.shared.domain.usecase.save

import com.vibi.shared.data.remote.AssetUploader
import com.vibi.shared.data.remote.inferAudioContentType
import com.vibi.shared.data.remote.inferAudioExt

/**
 * 편집 중 사용하는 원본(영상 세그먼트·BGM)을 R2 에 미리 업로드(prewarm)해, 저장/공유 시점의 렌더
 * 업로드 대기를 없앤다.
 *
 * [AssetUploader.ensureUploaded] 는 파일 해시 기반 멱등 연산이라, 여기서 미리 호출해도 산출물에는
 * 영향이 없다 — R2 와 로컬 [com.vibi.shared.data.remote.AssetKeyCache] 만 채워둘 뿐이다. 이후
 * 저장 경로([com.vibi.shared.data.repository.V3RenderExecutor])가 같은 파일을 다시 올리려 하면
 * 서버 sha256 dedup 으로 대용량 R2 PUT 을 건너뛴다. 따라서 prewarm 은 순수하게 대기 시간만 줄인다.
 *
 * **Best-effort** — 네트워크 실패·파일 누락(예: 원격 URL BGM 은 로컬 stat 불가) 등 어떤 이유로
 * 실패해도 throw 하지 않는다. 실패하면 저장 시점에 평소대로 업로드되므로 회귀가 없다. 호출자는 별도
 * 코루틴에서 fire-and-forget 하면 된다.
 */
class PrewarmAssetUploadUseCase(
    private val uploader: AssetUploader,
) {
    /** 영상 세그먼트 원본 prewarm — ext 는 mp4 고정. */
    suspend fun prewarmVideos(videoPaths: List<String>) {
        prewarm(videoPaths) { path -> AssetSpec(path, ext = "mp4", contentType = "video/mp4") }
    }

    /** BGM 원본 prewarm — 확장자별 ext/content-type 추론. 렌더 경로와 동일 추론을 공유. */
    suspend fun prewarmAudio(audioPaths: List<String>) {
        prewarm(audioPaths) { path ->
            val ext = inferAudioExt(path)
            AssetSpec(path, ext = ext, contentType = inferAudioContentType(ext))
        }
    }

    private data class AssetSpec(val path: String, val ext: String, val contentType: String)

    private suspend fun prewarm(paths: List<String>, spec: (String) -> AssetSpec) {
        for (path in paths.filter { it.isNotBlank() }.distinct()) {
            val s = spec(path)
            runCatching {
                uploader.ensureUploaded(localPath = s.path, ext = s.ext, contentType = s.contentType)
            }.onFailure { e ->
                // best-effort — 저장 시점에 자연 재시도되므로 삼킨다.
                println("[PrewarmAssetUpload] skip ${s.path}: ${e.message}")
            }
        }
    }
}
