package com.example.dubcast.fake

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSegmentRepository : SegmentRepository {

    private val segments = MutableStateFlow<List<Segment>>(emptyList())

    override fun observeByProjectId(projectId: String): Flow<List<Segment>> =
        segments.map { list ->
            list.filter { it.projectId == projectId }.sortedBy { it.order }
        }

    override suspend fun getByProjectId(projectId: String): List<Segment> =
        segments.value.filter { it.projectId == projectId }.sortedBy { it.order }

    override suspend fun getSegment(id: String): Segment? =
        segments.value.find { it.id == id }

    override suspend fun addSegment(segment: Segment) {
        segments.value = segments.value + segment
    }

    override suspend fun updateSegment(segment: Segment) {
        segments.value = segments.value.map { if (it.id == segment.id) segment else it }
    }

    override suspend fun deleteSegment(id: String) {
        segments.value = segments.value.filter { it.id != id }
    }

    override suspend fun deleteAllByProjectId(projectId: String) {
        segments.value = segments.value.filter { it.projectId != projectId }
    }

    override suspend fun getMaxOrder(projectId: String): Int =
        segments.value.filter { it.projectId == projectId }.maxOfOrNull { it.order } ?: -1
}
