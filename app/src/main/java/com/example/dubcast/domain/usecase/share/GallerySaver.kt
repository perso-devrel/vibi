package com.example.dubcast.domain.usecase.share

interface GallerySaver {
    suspend fun saveVideo(sourcePath: String, displayName: String): Result<Unit>
}
