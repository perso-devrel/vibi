@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.dubcast.shared.platform.currentTimeMillis
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject

/**
 * iOS: UIDocumentPickerViewController(forOpeningContentTypes = UTTypeAudio).
 * picker 종료 시 임시 URL 을 NSDocumentDirectory/picked_audio 로 복사 → 영구 path 반환.
 */
@Composable
actual fun rememberAudioPicker(
    onPicked: (uri: String) -> Unit,
): AudioPickerLauncher {
    val delegate = remember { mutableStateOf<UIDocumentPickerDelegateProtocol?>(null) }
    return remember {
        object : AudioPickerLauncher {
            override fun launch() {
                val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio))
                val pickerDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>,
                    ) {
                        controller.dismissViewControllerAnimated(true, null)
                        val first = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                        val accessing = first.startAccessingSecurityScopedResource()
                        try {
                            val path = copyAudioToDocuments(first)
                            if (path != null) onPicked(path)
                        } finally {
                            if (accessing) first.stopAccessingSecurityScopedResource()
                        }
                    }

                    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                        controller.dismissViewControllerAnimated(true, null)
                    }
                }
                delegate.value = pickerDelegate
                picker.delegate = pickerDelegate
                topViewControllerForAudioPicker()?.presentViewController(picker, true, null)
            }
        }
    }
}

private fun copyAudioToDocuments(srcUrl: NSURL): String? {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: return null
    val dir = "$docs/picked_audio"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    val name = srcUrl.lastPathComponent ?: "audio_${currentTimeMillis()}.m4a"
    val destPath = "$dir/$name"
    NSFileManager.defaultManager.removeItemAtPath(destPath, null)
    val ok = NSFileManager.defaultManager.copyItemAtURL(
        srcURL = srcUrl,
        toURL = NSURL.fileURLWithPath(destPath),
        error = null,
    )
    return if (ok) destPath else null
}

private fun topViewControllerForAudioPicker(): UIViewController? {
    val window = UIApplication.sharedApplication.keyWindow ?: return null
    var top: UIViewController? = window.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
