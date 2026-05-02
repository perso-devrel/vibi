package com.dubcast.shared.platform

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * MediaMetadataRetriever.getFrameAtTime 으로 프레임 1장 → JPEG. cacheDir/thumbs/<hash>.jpg 에 저장.
 * 동일 uri 의 두 번째 호출은 file existence check 만으로 path 반환.
 */
class AndroidVideoThumbnailExtractor constructor(
    private val context: Context,
) : VideoThumbnailExtractor {

    override suspend fun extractThumbnail(uri: String, atMs: Long): String? = withContext(Dispatchers.IO) {
        val cacheFile = cacheFileFor(uri, atMs)
        if (cacheFile.exists() && cacheFile.length() > 0) return@withContext cacheFile.absolutePath
        cacheFile.parentFile?.mkdirs()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            // CLOSEST_SYNC 가 OPTION_CLOSEST 보다 빠름 — keyframe 만 디코드.
            val bitmap: Bitmap = retriever.getFrameAtTime(
                atMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext null

            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            cacheFile.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun cacheFileFor(uri: String, atMs: Long): File {
        val dir = File(context.cacheDir, "thumbs")
        // uri 가 길거나 특수문자 포함 가능 — hashCode 로 안전 파일명.
        val key = "${uri.hashCode().toUInt()}_${atMs}.jpg"
        return File(dir, key)
    }
}
