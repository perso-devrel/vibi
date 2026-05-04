package com.dubcast.shared.domain.usecase.share

interface ShareSheetLauncher {
    suspend fun shareVideo(
        sourcePath: String,
        mimeType: String = "video/mp4",
        title: String? = null
    ): Result<Unit>
}
