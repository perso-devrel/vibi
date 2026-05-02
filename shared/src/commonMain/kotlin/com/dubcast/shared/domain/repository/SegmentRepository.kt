package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.Segment
import kotlinx.coroutines.flow.Flow

interface SegmentRepository {
    fun observeByProjectId(projectId: String): Flow<List<Segment>>
    suspend fun getByProjectId(projectId: String): List<Segment>
    suspend fun getSegment(id: String): Segment?
    suspend fun addSegment(segment: Segment)
    suspend fun updateSegment(segment: Segment)
    suspend fun deleteSegment(id: String)
    suspend fun deleteAllByProjectId(projectId: String)
    suspend fun getMaxOrder(projectId: String): Int

    /**
     * Lightweight 조회 — 썸네일 추출 등 첫 segment 의 sourceUri 만 필요한 경우. 모든 컬럼 hydrate
     * 비용을 피하기 위해 SELECT 1컬럼 LIMIT 1 만 수행.
     */
    suspend fun getFirstSourceUri(projectId: String): String?
}
