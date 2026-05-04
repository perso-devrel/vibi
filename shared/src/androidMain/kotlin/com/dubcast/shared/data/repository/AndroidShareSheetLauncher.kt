package com.dubcast.shared.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dubcast.shared.domain.usecase.share.ShareSheetLauncher
import java.io.File

/**
 * Android share sheet — `Intent.ACTION_SEND` + `Intent.createChooser`.
 *
 * 시스템 chooser 가 설치된 인스타/유튜브/카톡/메시지 등을 자동 표시. 인스타 탭 시
 * 인스타 앱이 영상 첨부 상태로 게시 화면 진입.
 *
 * cacheDir 의 mp4 를 외부 앱이 읽을 수 있도록 FileProvider 로 content:// URI 발급.
 * AndroidManifest 에 `${applicationId}.fileprovider` authority + file_paths.xml 필요.
 */
class AndroidShareSheetLauncher(
    private val context: Context
) : ShareSheetLauncher {

    override suspend fun shareVideo(
        sourcePath: String,
        mimeType: String,
        title: String?
    ): Result<Unit> = runCatching {
        val file = File(sourcePath)
        if (!file.exists()) error("Source file not found: $sourcePath")

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(chooser)
    }
}
