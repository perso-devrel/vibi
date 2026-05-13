@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vibi.cmp.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIScene
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelAlert
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAIFF
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeMP3
import platform.UniformTypeIdentifiers.UTTypeMPEG4Audio
import platform.darwin.NSObject

/**
 * iOS: UIDocumentPickerViewController(forOpeningContentTypes = UTTypeAudio).
 * picker 종료 시 임시 URL 을 NSDocumentDirectory/picked_audio 로 복사 → 영구 path 반환.
 *
 * Compose Multiplatform 의 rootVC 는 `ComposeLayersViewController` 인데 그 위에
 * `presentViewController` 가 silently 거부됨 (completion 도 발화 안 함) — DropdownMenu 같은
 * Compose popup 이 같은 VC 안에 overlay 로 떠있을 때 자식 modal presentation 이 막힘.
 *
 * 해결: 별도 `UIWindow` 를 같은 UIWindowScene 위에 띄워 빈 rootVC 에 picker 를 present. picker
 * 종료 시 window 를 hidden + rootVC=null 로 해체. 이 방식은 Compose VC 체인을 완전 우회하므로
 * popup 상태에 영향 안 받음.
 *
 * 구현이 [IosAudioPickerLauncher] 클래스로 분리된 이유: iOS K/N 의 LocalDeclarationsLowering 가
 * @Composable 안 익명 object + nested local fn + mutable state 캡처 조합에서 깨짐 — ios-kn-patterns
 * skill 참조.
 */
@Composable
actual fun rememberAudioPicker(
    onPicked: (uri: String) -> Unit,
): AudioPickerLauncher {
    val scope = rememberCoroutineScope()
    return remember(scope, onPicked) { IosAudioPickerLauncher(scope, onPicked) }
}

private class IosAudioPickerLauncher(
    private val scope: CoroutineScope,
    private val onPicked: (uri: String) -> Unit,
) : AudioPickerLauncher {

    /** picker 의 delegate ref — GC 방지용 강참조. */
    private var delegateRef: UIDocumentPickerDelegateProtocol? = null
    /** picker 를 띄우려고 만든 임시 UIWindow. picker dismiss 시 teardown. */
    private var pickerWindow: UIWindow? = null

    private fun teardownPickerWindow() {
        pickerWindow?.let {
            it.hidden = true
            it.rootViewController = null
        }
        pickerWindow = null
    }

    override fun launch() {
        val types = listOfNotNull<UTType>(
            UTTypeAudio, UTTypeMP3, UTTypeMPEG4Audio, UTTypeAIFF,
        )
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = types,
            asCopy = true,
        )
        picker.modalPresentationStyle = UIModalPresentationFullScreen
        val pickerDelegate = buildDelegate()
        delegateRef = pickerDelegate
        picker.delegate = pickerDelegate

        val scenes = UIApplication.sharedApplication.connectedScenes
        val scene: UIWindowScene? = scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        if (scene == null) {
            println("[AudioPicker] no UIWindowScene")
            return
        }
        // 기존 picker window 가 남아있으면 teardown — 중복 launch 케이스.
        teardownPickerWindow()
        val window = UIWindow(windowScene = scene)
        val container = UIViewController()
        window.rootViewController = container
        window.backgroundColor = UIColor.clearColor
        // alert level 위 — Compose host window 보다 무조건 위.
        window.windowLevel = UIWindowLevelAlert + 1.0
        window.makeKeyAndVisible()
        pickerWindow = window

        scope.launch {
            // window 가 hierarchy 에 attach 될 시간 양보 — 같은 tick 에 present 하면 silently fail
            // 가능 (Compose 마지막 frame 이 정리되는 동안 main loop 양보).
            delay(50)
            println("[AudioPicker] presenting on dedicated window container=$container")
            container.presentViewController(picker, true, completion = {
                println("[AudioPicker] after present, container.presentedVC=${container.presentedViewController}")
            })
        }
    }

    private fun buildDelegate(): UIDocumentPickerDelegateProtocol =
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                controller.dismissViewControllerAnimated(true, completion = {
                    teardownPickerWindow()
                })
                val first = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                val accessing = first.startAccessingSecurityScopedResource()
                try {
                    val path = copyToDocumentsRelative(first, relDir = "picked_audio", fallbackExt = "m4a")
                    if (path != null) onPicked(path)
                } finally {
                    if (accessing) first.stopAccessingSecurityScopedResource()
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                controller.dismissViewControllerAnimated(true, completion = {
                    teardownPickerWindow()
                })
            }
        }
}
