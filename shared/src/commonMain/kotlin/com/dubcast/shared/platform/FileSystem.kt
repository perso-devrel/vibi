@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dubcast.shared.platform

import kotlin.time.Clock

expect fun deleteLocalFile(path: String): Boolean
expect fun fileExists(path: String): Boolean
expect fun generateId(): String
expect fun writeTextToFile(path: String, content: String)

/**
 * Persist [bytes] as a file in the app's cache/documents directory and return
 * its absolute path. [fileName] is used as-is (no directory component).
 */
expect fun saveBytesToCache(fileName: String, bytes: ByteArray): String

/** Read the raw bytes of a local file referenced by an absolute path or content URI. */
expect suspend fun readFileBytes(uriOrPath: String): ByteArray

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
