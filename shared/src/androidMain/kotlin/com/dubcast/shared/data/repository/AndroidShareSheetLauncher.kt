package com.dubcast.shared.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dubcast.shared.domain.usecase.share.ShareSheetLauncher
import java.io.File
import java.util.ArrayList

/**
 * Android share sheet — `Intent.ACTION_SEND` (단일) / `Intent.ACTION_SEND_MULTIPLE` (다중)
 * + `Intent.createChooser`.
 *
 * 시스템 chooser 가 설치된 인스타/유튜브/카톡/메시지 등을 자동 표시. 인스타 탭 시
 * 인스타 앱이 영상 첨부 상태로 게시 화면 진입.
 *
 * cacheDir 의 mp4 를 외부 앱이 읽을 수 있도록 FileProvider 로 content:// URI 발급.
 * AndroidManifest 에 `${applicationId}.fileprovider` authority + file_paths.xml 필요.
 *
 * 다중 영상 공유는 ACTION_SEND_MULTIPLE + ArrayList<Uri> 의 EXTRA_STREAM 으로. 모든 URI 에
 * FLAG_GRANT_READ_URI_PERMISSION 을 부여해 외부 앱이 읽을 수 있도록.
 */
class AndroidShareSheetLauncher(
    private val context: Context,
) : ShareSheetLauncher {

    override suspend fun shareVideo(
        sourcePath: String,
        mimeType: String,
        title: String?,
    ): Result<Unit> = shareVideos(
        sourcePaths = listOf(sourcePath),
        mimeType = mimeType,
        title = title,
    )

    override suspend fun shareVideos(
        sourcePaths: List<String>,
        mimeType: String,
        title: String?,
    ): Result<Unit> = runCatching {
        require(sourcePaths.isNotEmpty()) { "sourcePaths must not be empty" }

        val authority = "${context.packageName}.fileprovider"
        val uris: ArrayList<Uri> = ArrayList(sourcePaths.size)
        for (path in sourcePaths) {
            val file = File(path)
            if (!file.exists()) error("Source file not found: $path")
            uris.add(FileProvider.getUriForFile(context, authority, file))
        }

        val sendIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris[0])
                title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                title?.let { putExtra(Intent.EXTRA_TITLE, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // chooser 자체에도 ClipData 로 multi-uri grant 를 명시적으로 — chooser intent 가
            // resolver 활동에 ACTION_SEND_MULTIPLE 의 EXTRA_STREAM 을 그대로 전파할 때 일부
            // OEM/앱이 grant 를 인식하지 못하는 케이스 회피.
            if (uris.size > 1) {
                val clip = android.content.ClipData.newUri(context.contentResolver, "videos", uris[0])
                for (i in 1 until uris.size) {
                    clip.addItem(android.content.ClipData.Item(uris[i]))
                }
                clipData = clip
            }
        }

        context.startActivity(chooser)
    }
}
