package com.vibi.cmp.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject

private val SystemBlue = Color(0xFF007AFF)

/**
 * iOS MediaPicker — `PHPickerViewController` (iOS 14+) 통합.
 *
 * **PHPicker 의 임시 file URL 은 picker dismiss 후 만료**되므로 loadFileRepresentation
 * 콜백 안에서 즉시 NSDocumentDirectory 로 복사하고 영구 경로를 [onPicked] 로 전달.
 *
 * **상대경로 반환**: app container UUID 가 재설치/build version 변경 시 바뀌므로 절대경로
 * (`/Users/.../Application/<UUID>/Documents/picker_media/foo.mov`) 를 그대로 저장하면 UUID
 * 변경 후 invalid path 가 됨. `picker_media/<filename>` 같은 Documents-relative path 만 저장하고,
 * 재생/업로드 시점에 `IosFilePathResolver` 가 현재 Documents 기준으로 resolve.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun MediaPicker(
    label: String,
    onPicked: (uri: String) -> Unit
) {
    val launch = rememberMediaPickerLauncher(onPicked)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { launch() }
    ) {
        Text(text = label, style = TextStyle(fontSize = 17.sp, color = SystemBlue))
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberMediaPickerLauncher(
    onPicked: (uri: String) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val delegateHolder = remember { mutableStateOf<PHPickerViewControllerDelegateProtocol?>(null) }
    return remember(scope, onPicked) {
        {
            presentPhPicker(scope, delegateHolder, onPicked)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun presentPhPicker(
    scope: CoroutineScope,
    delegateHolder: androidx.compose.runtime.MutableState<PHPickerViewControllerDelegateProtocol?>,
    onPicked: (uri: String) -> Unit,
) {
    val config = PHPickerConfiguration().apply {
        selectionLimit = 1L
        filter = PHPickerFilter.videosFilter
    }
    val picker = PHPickerViewController(configuration = config)

    val pickerDelegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(
            picker: PHPickerViewController,
            didFinishPicking: List<*>
        ) {
            picker.dismissViewControllerAnimated(flag = true, completion = null)

            val first = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
            val provider: NSItemProvider = first.itemProvider

            provider.loadFileRepresentationForTypeIdentifier(
                typeIdentifier = UTTypeMovie.identifier
            ) { tempUrl, error ->
                val temp = tempUrl as? NSURL ?: run {
                    if (error != null) println("[Picker] loadFileRepresentation error=$error")
                    return@loadFileRepresentationForTypeIdentifier
                }
                // PHPicker file URL 은 콜백 종료 후 삭제됨 — 동기 이동 필수. 시스템이 곧 지울
                // 임시 파일이라 우리가 소유권을 가져오는 move 가 안전하고, 같은 볼륨이면 rename(O(1))
                // 이라 대용량 영상 전체 복사 비용이 사라져 편집 화면 진입이 빨라짐(실패 시 copy 폴백).
                val permanentPath =
                    copyToDocumentsRelative(temp, relDir = "picker_media", fallbackExt = "mov", move = true)
                if (permanentPath != null) {
                    scope.launch { onPicked(permanentPath) }
                } else {
                    println("[Picker] copy to documents failed for ${temp.path}")
                }
            }
        }
    }
    delegateHolder.value = pickerDelegate
    picker.delegate = pickerDelegate

    topViewController()?.presentViewController(
        viewControllerToPresent = picker,
        animated = true,
        completion = null
    )
}

