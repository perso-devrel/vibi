package com.example.dubcast.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.dubcast.domain.usecase.share.GallerySaver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AndroidGallerySaver @Inject constructor(
    @ApplicationContext private val context: Context
) : GallerySaver {

    override suspend fun saveVideo(sourcePath: String, displayName: String): Result<Unit> = runCatching {
        val sourceFile = File(sourcePath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DubCast")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: error("Failed to create MediaStore entry")

            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { it.copyTo(outputStream) }
                } ?: error("Failed to open output stream for MediaStore entry")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val destDir = File(moviesDir, "DubCast")
            destDir.mkdirs()
            val destFile = File(destDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)
        }
    }
}
