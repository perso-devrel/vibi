package com.dubcast.shared.domain.usecase.share

interface GallerySaver {
    suspend fun saveVideo(sourcePath: String, displayName: String): Result<Unit>
}
