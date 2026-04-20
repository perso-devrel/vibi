package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.ImageClipDao
import com.example.dubcast.data.local.db.entity.ImageClipEntity
import com.example.dubcast.domain.model.ImageClip
import com.example.dubcast.domain.repository.ImageClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ImageClipRepositoryImpl @Inject constructor(
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
        heightPct = heightPct
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
        heightPct = heightPct
    )
}
