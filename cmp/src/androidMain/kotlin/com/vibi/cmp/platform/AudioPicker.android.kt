package com.vibi.cmp.platform

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vibi.shared.platform.currentTimeMillis
import java.io.File

/**
 * Android: SAF OpenDocument 로 audio 선택. GetContent 로 audio mime wildcard 를 쓰면 일부
 * 디바이스/Android 14 에서 picker 가 빈 화면으로 뜨거나 audio 파일을 노출 못 하는 사례가 있어
 * OpenDocument 로 교체. content URI 는 영속성이 보장 안 되므로 cacheDir 로 즉시 복사.
 */
@Composable
actual fun rememberAudioPicker(
    onPicked: (uri: String) -> Unit,
): AudioPickerLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val copied = copyAudioToCache(context, uri)
        if (copied != null) onPicked(copied)
    }
    return remember(launcher) {
        object : AudioPickerLauncher {
            override fun launch() {
                // 다양한 audio mime 명시 — 일부 파일 매니저가 wildcard 를 인식 못 하는 경우 대비.
                launcher.launch(
                    arrayOf(
                        "audio/*",
                        "audio/mpeg",
                        "audio/mp4",
                        "audio/aac",
                        "audio/wav",
                        "audio/x-wav",
                        "audio/ogg",
                        "audio/flac",
                    )
                )
            }
        }
    }
}

private fun copyAudioToCache(context: Context, uri: Uri): String? {
    val resolver: ContentResolver = context.contentResolver
    // 원본 DISPLAY_NAME 을 보존 — SoundDeck BGM 카드가 sourceUri 의 마지막 path segment 를
    // label 로 표시하므로 사용자가 삽입한 파일 이름 그대로 노출된다. 디렉터리 트래버설 방지를
    // 위해 path separator 는 제거.
    val displayName = queryDisplayName(resolver, uri)
    val ext = inferExtension(resolver, uri, displayName)
    val safeBase = displayName
        ?.substringBeforeLast('.', missingDelimiterValue = displayName)
        ?.replace(Regex("[\\\\/]"), "_")
        ?.trim()
        ?.ifBlank { null }
        ?: "audio_${currentTimeMillis()}"
    // 같은 이름 재선택 시 충돌 방지 — 디스크 파일명은 timestamp 접미사로 unique 보장하되,
    // base 이름은 그대로 두어 label 에 의미 있는 이름이 그대로 노출되도록 한다.
    val name = "${safeBase}_${currentTimeMillis()}.$ext"
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

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

private fun inferExtension(resolver: ContentResolver, uri: Uri, displayName: String?): String {
    val mime = resolver.getType(uri)
    return when {
        mime == null -> displayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() } ?: "m4a"
        mime.endsWith("/mpeg") -> "mp3"
        mime.endsWith("/aac") || mime.endsWith("/mp4") -> "m4a"
        mime.endsWith("/wav") || mime.endsWith("/x-wav") -> "wav"
        mime.endsWith("/ogg") -> "ogg"
        mime.endsWith("/flac") -> "flac"
        else -> displayName?.substringAfterLast('.', "m4a") ?: "m4a"
    }
}
