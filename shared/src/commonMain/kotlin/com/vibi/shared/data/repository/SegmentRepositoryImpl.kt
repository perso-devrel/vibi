package com.vibi.shared.data.repository

import com.vibi.shared.data.local.db.dao.SegmentDao
import com.vibi.shared.data.local.db.entity.SegmentEntity
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SegmentRepositoryImpl constructor(
    private val dao: SegmentDao
) : SegmentRepository {

    // distinctUntilChanged — Room invalidation 이 무관한 컬럼 변경에도 emit 트리거하므로
    // domain 값 비교로 dedup. 다운스트림 _uiState.update fan-out 막음.
    override fun observeByProjectId(projectId: String): Flow<List<Segment>> =
        dao.observeByProjectId(projectId).map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun getByProjectId(projectId: String): List<Segment> =
        dao.getByProjectId(projectId).map { it.toDomain() }

    override suspend fun getSegment(id: String): Segment? =
        dao.getById(id)?.toDomain()

    override suspend fun addSegment(segment: Segment) {
        dao.insert(segment.toEntity())
    }

    override suspend fun addSegments(segments: List<Segment>) {
        if (segments.isEmpty()) return
        dao.insertAll(segments.map { it.toEntity() })
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

    override suspend fun getFirstSourceUri(projectId: String): String? =
        dao.getFirstSourceUri(projectId)

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
