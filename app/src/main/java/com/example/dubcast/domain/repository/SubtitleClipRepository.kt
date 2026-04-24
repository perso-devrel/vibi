package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.SubtitleClip
import kotlinx.coroutines.flow.Flow

interface SubtitleClipRepository {
    fun observeClips(projectId: String): Flow<List<SubtitleClip>>
    suspend fun addClip(clip: SubtitleClip)
    /** Batch insert in a single DAO call to avoid N+1 writes when seeding cues. */
    suspend fun addClips(clips: List<SubtitleClip>)
    suspend fun updateClip(clip: SubtitleClip)
    suspend fun deleteClip(clipId: String)
    suspend fun deleteAllClips(projectId: String)
    suspend fun getClip(clipId: String): SubtitleClip?
    suspend fun deleteClipsBySourceDubClipId(dubClipId: String)
    /** Removes only AUTO-source cues so a regeneration starts from a clean slate. */
    suspend fun deleteAutoSubtitles(projectId: String)
}
