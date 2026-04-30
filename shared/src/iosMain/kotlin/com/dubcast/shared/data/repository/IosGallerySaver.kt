package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.usecase.share.GallerySaver
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS GallerySaver — `PHPhotoLibrary.performChanges` 로 mp4 를 사진 라이브러리에 추가.
 *
 * iOS 14+ 의 limited photo access (NSPhotoLibraryAddUsageDescription) 가 Info.plist 에 필요.
 * 권한 요청 자체는 본 클래스가 다루지 않음 — 호출 측에서 사전 권한 처리.
 */
class IosGallerySaver : GallerySaver {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun saveVideo(sourcePath: String, displayName: String): Result<Unit> = runCatching {
        val url = NSURL.fileURLWithPath(sourcePath)

        suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(url)
                },
                completionHandler = { success, error ->
                    if (success) {
                        cont.resume(Unit)
                    } else {
                        cont.resumeWithException(
                            RuntimeException(
                                "PHPhotoLibrary save failed: ${error?.localizedDescription ?: "unknown"}"
                            )
                        )
                    }
                }
            )
        }
    }
}
