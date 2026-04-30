package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BinaryPart
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

class IosMediaJobUploader {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    suspend fun loadAsBinaryPart(
        sourceUri: String,
        mediaType: String,
        prefix: String
    ): BinaryPart = withContext(Dispatchers.Default) {
        val (ext, contentType) = resolveMediaType(mediaType)
        // file:// scheme 면 URLWithString, 절대 경로면 fileURLWithPath. URLWithString 에 절대
        // 경로 넣으면 invalid URL 객체가 만들어지고 fallback 도 안 발동돼 dataWithContentsOfURL nil.
        val url = if (sourceUri.startsWith("file://")) {
            NSURL.URLWithString(sourceUri) ?: NSURL.fileURLWithPath(sourceUri.removePrefix("file://"))
        } else {
            NSURL.fileURLWithPath(sourceUri)
        }
        val data: NSData = NSData.dataWithContentsOfURL(url)
            ?: throw RuntimeException("Cannot read source media: $sourceUri")
        val length = data.length.toInt()
        val bytes = ByteArray(length)
        // NSData.bytes → ByteArray 복사. 이전 코드의 `allocArrayOf(bytes)` 는 새 native 메모리를
        // 만들어 그쪽으로 memcpy 했고, 우리 ByteArray 는 0 그대로 남는 버그 있었음. usePinned 로
        // ByteArray 자체의 주소를 얻어서 dest 로 사용해야 실제로 채워짐.
        if (length > 0) {
            data.bytes?.let { src ->
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), src, length.toULong())
                }
            }
        }
        BinaryPart(
            fieldName = "file",
            filename = "$prefix.$ext",
            bytes = bytes,
            contentType = contentType
        )
    }

    private fun resolveMediaType(mediaType: String): Pair<String, String> = when (mediaType) {
        "VIDEO" -> "mp4" to "video/mp4"
        "AUDIO" -> "mp3" to "audio/mpeg"
        else -> throw IllegalArgumentException("Unsupported media type")
    }
}
