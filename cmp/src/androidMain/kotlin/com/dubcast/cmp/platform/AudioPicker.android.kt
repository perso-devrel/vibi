package com.dubcast.cmp.platform

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dubcast.shared.platform.currentTimeMillis
import java.io.File

/**
 * Android: SAF GetContent with audio mime wildcard. content URI 는 영속성이 보장 안 되므로 cacheDir 로 즉시 복사.
 */
@Composable
actual fun rememberAudioPicker(
    onPicked: (uri: String) -> Unit,
): AudioPickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copied = copyAudioToCache(context, uri)
        if (copied != null) onPicked(copied)
    }
    return remember(launcher) {
        object : AudioPickerLauncher {
            override fun launch() {
                launcher.launch("audio/*")
            }
        }
    }
}

private fun copyAudioToCache(context: Context, uri: Uri): String? {
    val resolver: ContentResolver = context.contentResolver
    val ext = inferExtension(resolver, uri)
    val name = "audio_${currentTimeMillis()}.$ext"
    val dest = File(context.cacheDir, "picked_audio").apply { mkdirs() }.resolve(name)
    return try {
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun inferExtension(resolver: ContentResolver, uri: Uri): String {
    val mime = resolver.getType(uri)
    return when {
        mime == null -> "m4a"
        mime.endsWith("/mpeg") -> "mp3"
        mime.endsWith("/aac") || mime.endsWith("/mp4") -> "m4a"
        mime.endsWith("/wav") || mime.endsWith("/x-wav") -> "wav"
        mime.endsWith("/ogg") -> "ogg"
        mime.endsWith("/flac") -> "flac"
        else -> {
            // fallback: DISPLAY_NAME 의 확장자 사용
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.substringAfterLast('.', "m4a") else null
            } ?: "m4a"
        }
    }
}
