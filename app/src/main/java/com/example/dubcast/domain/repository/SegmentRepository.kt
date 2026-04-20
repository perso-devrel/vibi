package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.Segment
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
}
