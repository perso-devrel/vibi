package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.ImageClip
import kotlinx.coroutines.flow.Flow

interface ImageClipRepository {
    fun observeClips(projectId: String): Flow<List<ImageClip>>
    suspend fun addClip(clip: ImageClip)
    suspend fun updateClip(clip: ImageClip)
    suspend fun deleteClip(clipId: String)
    suspend fun deleteAllClips(projectId: String)
    suspend fun getClip(clipId: String): ImageClip?
}
