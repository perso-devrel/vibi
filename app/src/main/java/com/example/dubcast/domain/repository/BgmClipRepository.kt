package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.BgmClip
import kotlinx.coroutines.flow.Flow

interface BgmClipRepository {
    fun observeClips(projectId: String): Flow<List<BgmClip>>
    suspend fun getClip(clipId: String): BgmClip?
    suspend fun addClip(clip: BgmClip)
    suspend fun updateClip(clip: BgmClip)
    suspend fun deleteClip(clipId: String)
    suspend fun deleteAllByProjectId(projectId: String)
}
