package com.dubcast.cmp.platform

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
import com.dubcast.shared.platform.currentTimeMillis
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject

private val SystemBlue = Color(0xFF007AFF)

/**
 * iOS MediaPicker — `PHPickerViewController` (iOS 14+) 통합.
 *
 * **PHPicker 의 임시 file URL 은 picker dismiss 후 만료**되므로 loadFileRepresentation
 * 콜백 안에서 즉시 NSDocumentDirectory 로 복사하고 영구 경로를 [onPicked] 로 전달.
 *
 * UI: SectionRow 내부에서 자연스러운 iOS 스타일 — body 17pt, system blue, 행 전체 클릭 가능.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun MediaPicker(
    label: String,
    onPicked: (uri: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val delegate = remember { mutableStateOf<PHPickerViewControllerDelegateProtocol?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
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

                        println("[Picker] loadFileRepresentation requested")
                        provider.loadFileRepresentationForTypeIdentifier(
                            typeIdentifier = UTTypeMovie.identifier
                        ) { tempUrl, error ->
                            println("[Picker] callback tempUrl=$tempUrl error=$error")
                            val temp = tempUrl as? NSURL ?: return@loadFileRepresentationForTypeIdentifier
                            // PHPicker file URL 은 콜백 종료 후 삭제됨 — 동기 복사 필수.
                            val permanentPath = copyToDocuments(temp)
                            println("[Picker] copied to=$permanentPath")
                            if (permanentPath != null) {
                                scope.launch { onPicked(permanentPath) }
                            }
                        }
                    }
                }
                delegate.value = pickerDelegate
                picker.delegate = pickerDelegate

                topViewController()?.presentViewController(
                    viewControllerToPresent = picker,
                    animated = true,
                    completion = null
                )
            }
    ) {
        Text(text = label, style = TextStyle(fontSize = 17.sp, color = SystemBlue))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun copyToDocuments(tempUrl: NSURL): String? {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: return null
    val mediaDir = "$docs/picker_media"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = mediaDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    val fileName = tempUrl.lastPathComponent
        ?: "video_${currentTimeMillis()}.mov"
    val destPath = "$mediaDir/$fileName"

    // 같은 이름이 이미 있으면 덮어쓰기.
    NSFileManager.defaultManager.removeItemAtPath(destPath, error = null)

    val destUrl = NSURL.fileURLWithPath(destPath)
    val ok = NSFileManager.defaultManager.copyItemAtURL(
        srcURL = tempUrl,
        toURL = destUrl,
        error = null
    )
    return if (ok) destPath else null
}

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.keyWindow ?: return null
    var top: UIViewController? = window.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    return top
}
