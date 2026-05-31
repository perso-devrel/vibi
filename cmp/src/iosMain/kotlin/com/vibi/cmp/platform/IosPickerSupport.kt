@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vibi.cmp.platform

import com.vibi.shared.platform.currentTimeMillis
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * 현재 화면 최상단 UIViewController. picker / alert presentation source.
 *
 * SceneDelegate 기반 앱에서 `UIApplication.sharedApplication.keyWindow` 는 iOS 13+ 부터
 * deprecated 이고 nil 반환 — `UIWindow(windowScene:)` 로 만든 window 는 거기 안 잡힘.
 * connectedScenes 를 훑어 UIWindowScene 의 window 를 찾는다. activationState enum 은
 * Kotlin/Native 바인딩에 따라 Long/enum 매칭이 흔들리는 사례가 있어 사용하지 않고,
 * `isKeyWindow` 와 windows 비어있지 않음을 기준으로 선택.
 */
internal fun topViewController(): UIViewController? {
    val window = activeKeyWindow()
    if (window == null) {
        println("[Picker] topViewController: no window found (connectedScenes empty?)")
        return null
    }
    var top: UIViewController? = window.rootViewController
    while (top?.presentedViewController != null) {
        top = top.presentedViewController
    }
    if (top == null) {
        println("[Picker] topViewController: window has no rootViewController")
    }
    return top
}

private fun activeKeyWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes
    val windowScenes = scenes.mapNotNull { it as? UIWindowScene }
    println("[Picker] connectedScenes=${scenes.size}, windowScenes=${windowScenes.size}")
    // 1순위: 어떤 UIWindowScene 든 isKeyWindow 인 window
    for (scene in windowScenes) {
        val key = scene.windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        if (key != null) return key
    }
    // 2순위: window 가 있는 첫 UIWindowScene 의 첫 window
    for (scene in windowScenes) {
        val first = scene.windows.firstOrNull() as? UIWindow
        if (first != null) return first
    }
    return null
}

/**
 * SceneDelegate 의 `window.rootViewController` 직접 반환.
 *
 * [topViewController] 와 달리 `presentedViewController` 체인을 따라가지 **않는다**. CMP iOS 에서
 * `ComposeLayersViewController` 는 popup overlay 의 container 로 항상 rootVC 위에 stack 으로 쌓이고,
 * present 시도해도 picker 가 layer 안에 갇혀 화면에 안 나옴. picker / alert 처럼 사용자 화면 전체를
 * 가리는 modal 은 이 host rootVC 에 직접 present 해야 안정적 — 위의 popup chain 은 dismiss 후 다시
 * 띄울지 호출자가 결정.
 */
internal fun rootHostViewController(): UIViewController? =
    activeKeyWindow()?.rootViewController

/**
 * picker 가 넘긴 임시 NSURL 파일을 Documents/[relDir] 로 복사하고 Documents-relative 경로를 반환.
 *
 * 절대 경로 (`/Users/.../Application/<UUID>/Documents/...`) 가 아닌 상대 경로 (`relDir/filename`)
 * 를 반환하는 이유: app container UUID 는 재설치/build version 변경 시 바뀌어 invalid path 가
 * 됨. resolver 가 재생/업로드 시점에 현재 Documents 와 join.
 *
 * @param srcUrl picker 가 넘긴 임시 URL (loadFileRepresentation/documentPicker 콜백 인자)
 * @param relDir Documents 아래 서브디렉터리 ("picker_media" / "picked_audio" 등)
 * @param fallbackExt srcUrl.lastPathComponent 가 null 일 때 생성할 파일명의 확장자 ("mov"/"m4a")
 * @param move true 면 복사 대신 이동(rename). **우리가 소유한 임시 파일에만 사용** —
 *   PHPicker `loadFileRepresentation` 의 임시 파일은 콜백 종료 후 시스템이 삭제하므로 move 가
 *   안전하고, 같은 볼륨이면 rename(O(1)) 이라 대용량 영상 전체 바이트 복사(O(파일크기)) 비용을 없앤다.
 *   cross-volume 면 move 가 내부적으로 copy+unlink 라 copy 와 동일 — 절대 더 느리지 않다. move 실패 시
 *   copy 로 폴백. **security-scoped 사용자 파일(documentPicker)** 은 소유권이 없어 move 금지(기본 false).
 */
internal fun copyToDocumentsRelative(
    srcUrl: NSURL,
    relDir: String,
    fallbackExt: String,
    move: Boolean = false,
): String? {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String ?: return null
    val dir = "$docs/$relDir"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val name = srcUrl.lastPathComponent
        ?: "media_${currentTimeMillis()}.$fallbackExt"
    val destPath = "$dir/$name"

    NSFileManager.defaultManager.removeItemAtPath(destPath, error = null)

    val fm = NSFileManager.defaultManager
    val destUrl = NSURL.fileURLWithPath(destPath)
    val ok = if (move) {
        // move 우선 — 실패하면(예: cross-volume edge case) copy 로 폴백해 정확성 보장.
        fm.moveItemAtURL(srcURL = srcUrl, toURL = destUrl, error = null) ||
            fm.copyItemAtURL(srcURL = srcUrl, toURL = destUrl, error = null)
    } else {
        fm.copyItemAtURL(srcURL = srcUrl, toURL = destUrl, error = null)
    }
    return if (ok) "$relDir/$name" else null
}
