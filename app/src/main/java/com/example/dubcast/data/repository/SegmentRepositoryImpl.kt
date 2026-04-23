package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.SegmentDao
import com.example.dubcast.data.local.db.entity.SegmentEntity
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SegmentRepositoryImpl @Inject constructor(
    private val dao: SegmentDao
) : SegmentRepository {

    override fun observeByProjectId(projectId: String): Flow<List<Segment>> =
        dao.observeByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun getByProjectId(projectId: String): List<Segment> =
        dao.getByProjectId(projectId).map { it.toDomain() }

    override suspend fun getSegment(id: String): Segment? =
        dao.getById(id)?.toDomain()

    override suspend fun addSegment(segment: Segment) {
        dao.insert(segment.toEntity())
    }

    override suspend fun updateSegment(segment: Segment) {
        dao.update(segment.toEntity())
    }

    override suspend fun deleteSegment(id: String) {
        dao.deleteById(id)
    }

    override suspend fun deleteAllByProjectId(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    override suspend fun getMaxOrder(projectId: String): Int =
        dao.getMaxOrder(projectId) ?: -1

    private fun SegmentEntity.toDomain() = Segment(
        id = id,
        projectId = projectId,
        type = runCatching { SegmentType.valueOf(type) }.getOrDefault(SegmentType.VIDEO),
        order = order,
        sourceUri = sourceUri,
        durationMs = durationMs,
        width = width,
        height = height,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        imageXPct = imageXPct,
        imageYPct = imageYPct,
        imageWidthPct = imageWidthPct,
        imageHeightPct = imageHeightPct,
        volumeScale = volumeScale,
        speedScale = speedScale,
        duplicatedFromId = duplicatedFromId
    )

    private fun Segment.toEntity() = SegmentEntity(
        id = id,
        projectId = projectId,
        type = type.name,
        order = order,
        sourceUri = sourceUri,
        durationMs = durationMs,
        width = width,
        height = height,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        imageXPct = imageXPct,
        imageYPct = imageYPct,
        imageWidthPct = imageWidthPct,
        imageHeightPct = imageHeightPct,
        volumeScale = volumeScale,
        speedScale = speedScale,
        duplicatedFromId = duplicatedFromId
    )
}
