package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.DubClipDao
import com.example.dubcast.data.local.db.entity.DubClipEntity
import com.example.dubcast.domain.model.DubClip
import com.example.dubcast.domain.repository.DubClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DubClipRepositoryImpl @Inject constructor(
    private val dao: DubClipDao
) : DubClipRepository {

    override fun observeClips(projectId: String): Flow<List<DubClip>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun addClip(clip: DubClip) {
        dao.insert(clip.toEntity())
    }

    override suspend fun updateClip(clip: DubClip) {
        dao.update(clip.toEntity())
    }

    override suspend fun deleteClip(clipId: String) {
        dao.deleteById(clipId)
    }

    override suspend fun deleteAllClips(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    override suspend fun getClip(clipId: String): DubClip? {
        return dao.getById(clipId)?.toDomain()
    }

    private fun DubClipEntity.toDomain() = DubClip(
        id = id,
        projectId = projectId,
        text = text,
        voiceId = voiceId,
        voiceName = voiceName,
        audioFilePath = audioFilePath,
        startMs = startMs,
        durationMs = durationMs,
        volume = volume
    )

    private fun DubClip.toEntity() = DubClipEntity(
        id = id,
        projectId = projectId,
        text = text,
        voiceId = voiceId,
        voiceName = voiceName,
        audioFilePath = audioFilePath,
        startMs = startMs,
        durationMs = durationMs,
        volume = volume
    )
}
