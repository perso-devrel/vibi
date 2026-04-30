package com.dubcast.shared.data.repository

import com.dubcast.shared.data.local.db.dao.ImageClipDao
import com.dubcast.shared.data.local.db.entity.ImageClipEntity
import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.repository.ImageClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ImageClipRepositoryImpl constructor(
    private val dao: ImageClipDao
) : ImageClipRepository {

    override fun observeClips(projectId: String): Flow<List<ImageClip>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun addClip(clip: ImageClip) {
        dao.insert(clip.toEntity())
    }

    override suspend fun updateClip(clip: ImageClip) {
        dao.update(clip.toEntity())
    }

    override suspend fun deleteClip(clipId: String) {
        dao.deleteById(clipId)
    }

    override suspend fun deleteAllClips(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    override suspend fun getClip(clipId: String): ImageClip? {
        return dao.getById(clipId)?.toDomain()
    }

    private fun ImageClipEntity.toDomain() = ImageClip(
        id = id,
        projectId = projectId,
        imageUri = imageUri,
        startMs = startMs,
        endMs = endMs,
        xPct = xPct,
        yPct = yPct,
        widthPct = widthPct,
        heightPct = heightPct,
        lane = lane
    )

    private fun ImageClip.toEntity() = ImageClipEntity(
        id = id,
        projectId = projectId,
        imageUri = imageUri,
        startMs = startMs,
        endMs = endMs,
        xPct = xPct,
        yPct = yPct,
        widthPct = widthPct,
        heightPct = heightPct,
        lane = lane
    )
}
