package com.dubcast.shared.data.repository

import com.dubcast.shared.data.local.db.dao.BgmClipDao
import com.dubcast.shared.data.local.db.entity.BgmClipEntity
import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.repository.BgmClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BgmClipRepositoryImpl constructor(
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
