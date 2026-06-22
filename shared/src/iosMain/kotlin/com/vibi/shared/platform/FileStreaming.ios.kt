@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.shared.platform

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.writeData
import platform.posix.memcpy

// 64KB — NSFileHandle 청크 read/write 단위. 피크 메모리는 청크 1개 + 엔진 버퍼로 제한된다.
private const val STREAM_CHUNK = 64 * 1024

actual fun cacheDirPath(): String = cacheDirectory()

actual fun persistentStemsDirPath(): String = persistentStemsDirectory()

actual suspend fun fileUploadBody(path: String, contentType: String): OutgoingContent {
    val size = statFile(path).sizeBytes
    val ct = ContentType.parse(contentType)
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ct
        override val contentLength: Long = size
        override suspend fun writeTo(channel: ByteWriteChannel) {
            val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return
            try {
                while (true) {
                    val data = handle.readDataOfLength(STREAM_CHUNK.convert())
                    if (data.length.toInt() == 0) break
                    channel.writeFully(data.toByteArray())
                }
            } finally {
                handle.closeFile()
            }
        }
    }
}

actual suspend fun writeChannelToFile(channel: ByteReadChannel, destPath: String) {
    // 빈 파일로 생성(기존 내용 truncate) 후 쓰기 핸들로 순차 append.
    NSFileManager.defaultManager.createFileAtPath(destPath, null, null)
    val handle = NSFileHandle.fileHandleForWritingAtPath(destPath)
        ?: error("writeChannelToFile: cannot open $destPath for writing")
    try {
        val buf = ByteArray(STREAM_CHUNK)
        while (true) {
            val n = channel.readAvailable(buf, 0, buf.size)
            if (n == -1) break
            if (n == 0) continue
            buf.usePinned { pinned ->
                val data = NSData.create(bytes = pinned.addressOf(0), length = n.toULong())
                handle.writeData(data)
            }
        }
    } finally {
        handle.closeFile()
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { memcpy(it.addressOf(0), bytes, size.toULong()) }
    return out
}
