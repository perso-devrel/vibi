package com.example.dubcast.fake

import android.net.Uri
import com.example.dubcast.domain.usecase.share.GallerySaver
import io.mockk.mockk

class FakeGallerySaver : GallerySaver {
    var result: Result<Uri> = Result.success(mockk<Uri>())

    override suspend fun saveVideo(sourcePath: String, displayName: String): Result<Uri> = result
}
