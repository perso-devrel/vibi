package com.vibi.cmp.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
actual fun rememberMediaPickerLauncher(
    onPicked: (uri: String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // photo picker 의 content:// URI 는 프로세스 재시작 시 만료된다 → 그대로 저장하면 앱을
            // 나갔다 들어왔을 때 드래프트 영상/파형/분리/export 가 전부 못 읽힌다. filesDir 로 복사해
            // 영속 file:// 경로를 넘긴다 (iOS picker 가 Documents 로 복사하는 것과 동등).
            // 복사는 수십~수백 MB 가능 → 반드시 IO 디스패처(메인 스레드면 ANR).
            // 복사 실패 시 원본 content:// 로 폴백하지 않는다 — 그 URI 는 프로세스 종료 후 죽어
            // 깨진 드래프트가 되므로, iOS picker 와 동일하게 성공(영속 경로 non-null)일 때만 onPicked.
            scope.launch {
                val persistent = withContext(Dispatchers.IO) { copyPickedToFiles(appContext, uri) }
                if (persistent != null) onPicked(persistent)
            }
        }
    }
    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
    }
}

/**
 * content:// 영상을 `filesDir/picker_media/<uuid>/<원본 파일명>` 으로 복사하고 인코딩된 file:// URI 반환.
 * - uuid 서브디렉터리: 같은 이름 영상의 충돌 방지 + 원본 파일명 보존(프로젝트 타이틀에 사용).
 * - [Uri.fromFile]: 공백 등 특수문자를 %-인코딩 → MediaMetadataRetriever / MediaExtractor / ExoPlayer /
 *   readFileBytes 모두 안전하게 파싱. (file:// 인 이유는 메타추출기가 ContentResolver 오버로드라
 *   scheme 없는 순수 경로를 못 받기 때문.)
 *
 * 주의: cascadeDeleteProject(commonMain)가 sourceUri 파일을 지우지 않아(iOS 도 동일) picker_media 가
 *   누적된다 — orphan sweep 정리는 후속 작업. filesDir 는 OS 가 비우지 않음.
 */
private fun copyPickedToFiles(context: Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val name = queryDisplayName(resolver, uri)?.takeIf { it.isNotBlank() } ?: "video.mp4"
    val dir = File(File(context.filesDir, "picker_media"), UUID.randomUUID().toString())
    if (!dir.mkdirs() && !dir.isDirectory) return@runCatching null
    val dest = File(dir, name)
    val copied = resolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false
    if (copied) Uri.fromFile(dest).toString() else null
}.getOrNull()
