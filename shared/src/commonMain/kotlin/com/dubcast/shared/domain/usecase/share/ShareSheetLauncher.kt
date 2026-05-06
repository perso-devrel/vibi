package com.dubcast.shared.domain.usecase.share

interface ShareSheetLauncher {
    suspend fun shareVideo(
        sourcePath: String,
        mimeType: String = "video/mp4",
        title: String? = null,
    ): Result<Unit>

    /**
     * 여러 영상을 한 번의 share sheet 으로. Android: ACTION_SEND_MULTIPLE.
     * iOS: UIActivityViewController activityItems 배열.
     *
     * 일부 외부 앱 (인스타 등) 은 1개만 받을 수 있어 완벽 호환 보장 X — 사용자가 chooser
     * 에서 선택한 앱이 다중 첨부 미지원이면 첫 번째만 또는 에러.
     */
    suspend fun shareVideos(
        sourcePaths: List<String>,
        mimeType: String = "video/mp4",
        title: String? = null,
    ): Result<Unit>
}
