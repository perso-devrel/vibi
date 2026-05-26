@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.shared.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSInputStream
import platform.Foundation.inputStreamWithFileAtPath
import platform.posix.stat
import platform.posix.uint8_tVar

private const val CHUNK_BYTES = 64 * 1024

actual suspend fun sha256HexOfFile(path: String): String = withContext(Dispatchers.Default) {
    val resolved = resolveStoredUriToPath(path) ?: path
    val stream = NSInputStream.inputStreamWithFileAtPath(resolved)
        ?: error("sha256HexOfFile: cannot open $path (resolved=$resolved)")
    stream.open()
    try {
        val hasher = Sha256()
        val buf = ByteArray(CHUNK_BYTES)
        buf.usePinned { pinned ->
            val ptr = pinned.addressOf(0).reinterpret<uint8_tVar>()
            while (true) {
                val n = stream.read(ptr, CHUNK_BYTES.toULong()).toInt()
                if (n <= 0) break
                hasher.update(buf, 0, n)
            }
        }
        hasher.digestHex()
    } finally {
        stream.close()
    }
}

actual suspend fun statFile(path: String): FileStat = withContext(Dispatchers.Default) {
    val resolved = resolveStoredUriToPath(path) ?: path
    // Kotlin/Native 2.2.x 의 platform.Foundation.NSDate 가 `timeIntervalSince1970` property 를
    // 해석 못 하는 회귀가 있어 NSFileManager.attributesOfItemAtPath 우회. POSIX stat(2) 는
    // Darwin (iOS/macOS) 에 안정적으로 노출되고 sandbox 내 path 에 자유롭게 동작.
    memScoped {
        val st = alloc<stat>()
        val rc = stat(resolved, st.ptr)
        if (rc != 0) error("statFile: stat(2) failed for $path (resolved=$resolved)")
        // darwin 의 mod-time field 는 st_mtimespec (timespec). Linux 의 st_mtim 과 다름.
        val ts = st.st_mtimespec
        FileStat(
            sizeBytes = st.st_size.toLong(),
            lastModifiedMs = ts.tv_sec * 1_000L + (ts.tv_nsec / 1_000_000L),
        )
    }
}
