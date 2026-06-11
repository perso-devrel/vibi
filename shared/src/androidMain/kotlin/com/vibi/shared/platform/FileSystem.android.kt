package com.vibi.shared.platform

import android.content.Context
import android.net.Uri
import com.vibi.shared.data.local.db.applicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

actual fun deleteLocalFile(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

actual fun fileExists(path: String): Boolean = runCatching { File(path).exists() }.getOrDefault(false)

actual fun generateId(): String = UUID.randomUUID().toString()

actual fun writeTextToFile(path: String, content: String) {
    File(path).writeText(content)
}

actual fun saveBytesToCache(fileName: String, bytes: ByteArray): String {
    val cacheDir = applicationContext.cacheDir
    val file = File(cacheDir, fileName)
    file.writeBytes(bytes)
    return file.absolutePath
}

actual fun saveBytesToPersistentFile(fileName: String, bytes: ByteArray): String {
    // filesDir 는 cacheDir 와 달리 OS 가 임의로 비우지 않음 — 오프라인 stem 재생 보장.
    val dir = File(applicationContext.filesDir, "stems").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeBytes(bytes)
    return file.absolutePath
}

actual suspend fun readFileBytes(uriOrPath: String): ByteArray = withContext(Dispatchers.IO) {
    if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
        val uri = Uri.parse(uriOrPath)
        requireNotNull(applicationContext.contentResolver.openInputStream(uri)) {
            "Cannot open input stream for $uriOrPath"
        }.use { it.readBytes() }
    } else {
        File(uriOrPath).readBytes()
    }
}
