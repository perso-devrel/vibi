package com.vibi.shared.data.remote

import com.russhwolf.settings.Settings
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.dto.AssetUploadUrlRequest
import com.vibi.shared.platform.FileStat
import com.vibi.shared.platform.fileUploadBody
import com.vibi.shared.platform.sha256HexOfFile
import com.vibi.shared.platform.statFile

/**
 * 로컬 파일 → R2 자산 키 매핑 캐시. `(path, size, mtime)` 가 같으면 같은 파일로 간주하고
 * sha256 재계산을 건너뛴다. server-side dedup 확인은 [AssetUploadManager] 가 매번 BFF 호출로
 * 안전망 — 캐시는 sha256 작업만 절약.
 */
class AssetKeyCache(private val settings: Settings) {
    fun get(path: String, meta: FileStat): String? =
        settings.getStringOrNull(keyFor(path, meta))

    fun put(path: String, meta: FileStat, assetKey: String) {
        settings.putString(keyFor(path, meta), assetKey)
    }

    private fun keyFor(path: String, meta: FileStat): String =
        "asset_v1:$path|${meta.sizeBytes}|${meta.lastModifiedMs}"
}

/**
 * 로컬 파일을 R2 에 업로드 보장하고 BFF 가 RenderConfigV3 에 넣을 assetKey 를 반환.
 *
 * 멱등 — 같은 파일을 여러 번 호출해도 캐시/dedup 으로 PUT 은 한 번만 일어난다. 덕분에 편집 진입
 * 시점의 선업로드(prewarm)와 저장 시점의 렌더 업로드가 같은 키를 공유하고, 후자는 캐시 히트로
 * 즉시 반환된다. [com.vibi.shared.data.repository.V3RenderExecutor] 와 prewarm UseCase 가 공유.
 */
interface AssetUploader {
    suspend fun ensureUploaded(localPath: String, ext: String, contentType: String): String
}

/**
 * [AssetUploader] 의 R2 구현.
 *
 * 흐름:
 *   1) 로컬 [AssetKeyCache] hit → 즉시 반환 (sha256/네트워크 호출 모두 skip)
 *   2) sha256 + size + ext + contentType 으로 BFF 에 upload-url 요청
 *   3) `alreadyExists=true` 면 모바일은 PUT skip — 그 assetKey 만 캐시 박고 반환
 *   4) 그렇지 않으면 presigned URL 로 R2 직접 PUT → 성공 시 캐시 저장
 *
 * 주의: 로컬 [AssetKeyCache] hit(1)은 BFF 를 호출하지 않고 캐시된 assetKey 를 즉시 반환한다 —
 * prewarm→저장 시점의 instant 히트를 위한 의도된 최적화. 따라서 R2 가 lifecycle 만료 등으로
 * 객체를 지웠는데 로컬 캐시가 남아 있으면, 이후 렌더가 그 키로 404 날 수 있다. R2 만료 정책을
 * 둘 경우 캐시 TTL 을 그보다 짧게 두거나, hit 시에도 requestAssetUploadUrl 로 존재 확인을
 * 추가해야 한다 (sha256 만 캐시에 함께 저장하면 네트워크만 추가, 해시 재계산은 계속 skip).
 */
class AssetUploadManager(
    private val api: BffApi,
    private val cache: AssetKeyCache,
) : AssetUploader {
    override suspend fun ensureUploaded(
        localPath: String,
        ext: String,
        contentType: String,
    ): String {
        val meta = statFile(localPath)
        cache.get(localPath, meta)?.let { return it }

        val sha = sha256HexOfFile(localPath)
        val resp = api.requestAssetUploadUrl(
            AssetUploadUrlRequest(
                sha256Hex = sha,
                sizeBytes = meta.sizeBytes,
                ext = ext,
                contentType = contentType,
            )
        )
        if (!resp.alreadyExists) {
            val url = requireNotNull(resp.uploadUrl) {
                "BFF returned alreadyExists=false but uploadUrl=null"
            }
            // 스트리밍 업로드 — 영상 전체를 ByteArray 로 적재하지 않고 청크 전송(OOM 방지).
            val ok = api.putAssetToR2(url, fileUploadBody(localPath, contentType))
            require(ok) { "R2 PUT failed for $localPath (key=${resp.assetKey})" }
        }
        cache.put(localPath, meta, resp.assetKey)
        return resp.assetKey
    }
}

private val ALLOWED_AUDIO_EXTS = setOf("m4a", "mp3", "wav", "aac")

/** 파일 경로 확장자로 R2 업로드용 audio ext 추론 — 미지원/누락 시 m4a 폴백. 업로드 경로([com.vibi.shared.data.repository.V3RenderExecutor])와 prewarm 이 공유. */
internal fun inferAudioExt(path: String): String {
    val raw = path.substringAfterLast('.', "").lowercase()
    return if (raw in ALLOWED_AUDIO_EXTS) raw else "m4a"
}

/** [inferAudioExt] 결과 → R2 PUT content-type. */
internal fun inferAudioContentType(ext: String): String = when (ext) {
    "m4a", "aac" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    else -> "audio/mp4"
}
