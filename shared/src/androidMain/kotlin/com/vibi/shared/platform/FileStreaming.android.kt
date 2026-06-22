package com.vibi.shared.platform

import android.net.Uri
import com.vibi.shared.data.local.db.applicationContext
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

private const val STREAM_CHUNK = 64 * 1024

actual fun cacheDirPath(): String = applicationContext.cacheDir.absolutePath

actual fun persistentStemsDirPath(): String =
    File(applicationContext.filesDir, "stems").apply { mkdirs() }.absolutePath

actual suspend fun fileUploadBody(path: String, contentType: String): OutgoingContent {
    val ct = ContentType.parse(contentType)
    val size = statFile(path).sizeBytes
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ct
        override val contentLength: Long = size
        override suspend fun writeTo(channel: ByteWriteChannel) {
            openInput(path).use { ins ->
                val buf = ByteArray(STREAM_CHUNK)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    if (n > 0) channel.writeFully(buf, 0, n)
                }
            }
        }
    }
}

actual suspend fun writeChannelToFile(channel: ByteReadChannel, destPath: String): Unit =
    withContext(Dispatchers.IO) {
        File(destPath).outputStream().use { out ->
            val buf = ByteArray(STREAM_CHUNK)
            while (true) {
                val n = channel.readAvailable(buf, 0, buf.size)
                if (n == -1) break
                if (n > 0) out.write(buf, 0, n)
            }
        }
    }

private fun openInput(path: String): InputStream =
    if (path.startsWith("content://") || path.startsWith("file://")) {
        requireNotNull(applicationContext.contentResolver.openInputStream(Uri.parse(path))) {
            "fileUploadBody: cannot open input stream for $path"
        }
    } else {
        File(path).inputStream()
    }
