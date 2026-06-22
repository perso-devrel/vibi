@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.shared.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual fun deleteLocalFile(path: String): Boolean {
    val fm = NSFileManager.defaultManager
    return if (fm.fileExistsAtPath(path)) {
        fm.removeItemAtPath(path, null)
    } else {
        false
    }
}

actual fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun generateId(): String = NSUUID().UUIDString()

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun writeTextToFile(path: String, content: String) {
    (content as NSString).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null
    )
}

internal fun cacheDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return requireNotNull(paths.firstOrNull() as? String) { "Could not resolve iOS cache dir." }
}

actual fun saveBytesToCache(fileName: String, bytes: ByteArray): String {
    val path = "${cacheDirectory()}/$fileName"
    bytes.usePinned { pinned ->
        val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        data.writeToFile(path, atomically = true)
    }
    return path
}

/** Application Support/stems — OS 가 storage 압박에도 evict 하지 않음 (Caches 와 대비). */
internal fun persistentStemsDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
    val base = requireNotNull(paths.firstOrNull() as? String) { "Could not resolve iOS Application Support dir." }
    val dir = "$base/stems"
    // Application Support 는 기본 생성 안 돼 있을 수 있어 명시적으로 만든다.
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

actual fun saveBytesToPersistentFile(fileName: String, bytes: ByteArray): String {
    val path = "${persistentStemsDirectory()}/$fileName"
    bytes.usePinned { pinned ->
        val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        data.writeToFile(path, atomically = true)
    }
    return path
}

actual suspend fun readFileBytes(uriOrPath: String): ByteArray = withContext(Dispatchers.Default) {
    // NSData.dataWithContentsOfFile + memcpy 가 대용량 영상(100MB+) 에서 수십~수백ms blocking.
    // caller (보통 viewModelScope 의 Main) 에 머물면 UI freeze. Default 로 분리.
    // (Android 쪽 readFileBytes 는 이미 IO dispatcher 안에서 동작 — 대칭 확보.)
    val resolved = resolveStoredUriToPath(uriOrPath) ?: uriOrPath
    val data = requireNotNull(NSData.dataWithContentsOfFile(resolved)) {
        "Cannot read bytes from $uriOrPath (resolved=$resolved)"
    }
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, size.toULong())
    }
    bytes
}
