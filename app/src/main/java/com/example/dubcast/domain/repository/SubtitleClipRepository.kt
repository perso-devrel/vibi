package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.SubtitleClip
import kotlinx.coroutines.flow.Flow

interface SubtitleClipRepository {
    fun observeClips(projectId: String): Flow<List<SubtitleClip>>
    suspend fun addClip(clip: SubtitleClip)
    suspend fun updateClip(clip: SubtitleClip)
    suspend fun deleteClip(clipId: String)
    suspend fun deleteAllClips(projectId: String)
    suspend fun getClip(clipId: String): SubtitleClip?
}
