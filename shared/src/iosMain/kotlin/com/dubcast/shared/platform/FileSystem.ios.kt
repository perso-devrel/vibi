@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.dubcast.shared.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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

private fun cacheDirectory(): String {
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

actual suspend fun readFileBytes(uriOrPath: String): ByteArray {
    val data = requireNotNull(NSData.dataWithContentsOfFile(uriOrPath)) {
        "Cannot read bytes from $uriOrPath"
    }
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, size.toULong())
    }
    return bytes
}
