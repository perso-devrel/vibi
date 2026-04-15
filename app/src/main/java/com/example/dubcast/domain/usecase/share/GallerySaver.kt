package com.example.dubcast.domain.usecase.share

import android.net.Uri

interface GallerySaver {
    suspend fun saveVideo(sourcePath: String, displayName: String): Result<Uri>
}
