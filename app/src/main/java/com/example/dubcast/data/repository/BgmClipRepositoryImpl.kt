package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.BgmClipDao
import com.example.dubcast.data.local.db.entity.BgmClipEntity
import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.repository.BgmClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BgmClipRepositoryImpl @Inject constructor(
    private val dao: BgmClipDao
) : BgmClipRepository {

    override fun observeClips(projectId: String): Flow<List<BgmClip>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun getClip(clipId: String): BgmClip? {
        return dao.getById(clipId)?.toDomain()
    }

    override suspend fun addClip(clip: BgmClip) {
        dao.insert(clip.toEntity())
    }

    override suspend fun updateClip(clip: BgmClip) {
        dao.update(clip.toEntity())
    }

    override suspend fun deleteClip(clipId: String) {
        dao.deleteById(clipId)
    }

    override suspend fun deleteAllByProjectId(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    private fun BgmClipEntity.toDomain() = BgmClip(
        id = id,
        projectId = projectId,
        sourceUri = sourceUri,
        sourceDurationMs = sourceDurationMs,
        startMs = startMs,
        volumeScale = volumeScale
    )

    private fun BgmClip.toEntity() = BgmClipEntity(
        id = id,
        projectId = projectId,
        sourceUri = sourceUri,
        sourceDurationMs = sourceDurationMs,
        startMs = startMs,
        volumeScale = volumeScale
    )
}
