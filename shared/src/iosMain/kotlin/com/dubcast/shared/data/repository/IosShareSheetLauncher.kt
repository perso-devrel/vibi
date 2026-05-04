package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.usecase.share.ShareSheetLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import kotlin.coroutines.resume

/**
 * iOS share sheet — `UIActivityViewController`.
 *
 * 시스템이 설치된 인스타/카톡/메시지/AirDrop/메일 등을 자동 표시. iPad 호환은
 * popoverPresentationController 가 K/N cinterop 미노출이라 별도 Swift bridge 필요 (후속).
 * iPhone 에선 unmodified presentation 으로 정상 동작.
 */
class IosShareSheetLauncher : ShareSheetLauncher {

    override suspend fun shareVideo(
        sourcePath: String,
        mimeType: String,
        title: String?
    ): Result<Unit> = runCatching {
        // PHPicker 절대 경로 / file:// 모두 처리. NSURL.URLWithString 의 absolute path bug 회피.
        val url = if (sourcePath.startsWith("file://")) {
            NSURL.URLWithString(sourcePath) ?: NSURL.fileURLWithPath(sourcePath.removePrefix("file://"))
        } else {
            NSURL.fileURLWithPath(sourcePath)
        }

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val activityItems = buildList<Any> {
                    add(url)
                    title?.let { add(it) }
                }

                val activityVc = UIActivityViewController(
                    activityItems = activityItems,
                    applicationActivities = null
                )

                activityVc.completionWithItemsHandler = { _, _, _, _ ->
                    cont.resume(Unit)
                }

                val presenter = topViewController()
                    ?: error("No root view controller to present share sheet")

                presenter.presentViewController(activityVc, animated = true, completion = null)
            }
        }
    }

    private fun keyWindow(): UIWindow? {
        val app = UIApplication.sharedApplication
        @Suppress("DEPRECATION")
        return app.keyWindow ?: app.windows.firstOrNull() as? UIWindow
    }

    private fun topViewController(): UIViewController? {
        var top = keyWindow()?.rootViewController ?: return null
        while (true) {
            val presented = top.presentedViewController ?: break
            top = presented
        }
        return top
    }
}
