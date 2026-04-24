package com.example.dubcast.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Shared multipart uploader for the BFF media-job endpoints (autodub /
 * subtitles). Resolves a content URI to a cache-dir temp file, builds the
 * `file` + `spec` parts, runs [block], and guarantees the temp file is
 * cleaned up afterwards. Error messages intentionally omit the source URI
 * because it can leak user-visible content paths into logs / failure rows.
 */
class MediaJobUploader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun <T> upload(
        sourceUri: String,
        mediaType: String,
        prefix: String,
        specJson: String,
        block: suspend (filePart: MultipartBody.Part, specBody: RequestBody) -> T
    ): T {
        val (ext, contentType) = resolveMediaType(mediaType)
        val tempFile = File(context.cacheDir, "${prefix}_${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot open source media")

        return try {
            val filePart = MultipartBody.Part.createFormData(
                "file",
                tempFile.name,
                tempFile.asRequestBody(contentType.toMediaType())
            )
            val specBody = specJson.toRequestBody(JSON.toMediaType())
            block(filePart, specBody)
        } finally {
            tempFile.delete()
        }
    }

    private fun resolveMediaType(mediaType: String): Pair<String, String> = when (mediaType) {
        "VIDEO" -> "mp4" to "video/mp4"
        "AUDIO" -> "mp3" to "audio/mpeg"
        else -> throw IllegalArgumentException("Unsupported media type")
    }

    private companion object {
        const val JSON = "application/json"
    }
}
