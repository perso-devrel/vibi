package com.vibi.shared.domain.usecase.timeline

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** timeline use-case 테스트 공용 in-memory fake — 패키지 내 테스트들이 공유. */
class FakeSegmentRepository : SegmentRepository {
    val store = mutableMapOf<String, Segment>()
    override fun observeByProjectId(projectId: String): Flow<List<Segment>> =
        flowOf(store.values.filter { it.projectId == projectId }.sortedBy { it.order })
    override suspend fun getByProjectId(projectId: String): List<Segment> =
        store.values.filter { it.projectId == projectId }.sortedBy { it.order }
    override suspend fun getSegment(id: String): Segment? = store[id]
    override suspend fun addSegment(segment: Segment) { store[segment.id] = segment }
    override suspend fun addSegments(segments: List<Segment>) { segments.forEach { store[it.id] = it } }
    override suspend fun updateSegment(segment: Segment) { store[segment.id] = segment }
    override suspend fun deleteSegment(id: String) { store.remove(id) }
    override suspend fun deleteAllByProjectId(projectId: String) {
        store.values.filter { it.projectId == projectId }.forEach { store.remove(it.id) }
    }
    override suspend fun getMaxOrder(projectId: String): Int =
        store.values.filter { it.projectId == projectId }.maxOfOrNull { it.order } ?: -1
    override suspend fun getFirstSourceUri(projectId: String): String? =
        store.values.filter { it.projectId == projectId }.minByOrNull { it.order }?.sourceUri
}

class FakeSeparationDirectiveRepository : SeparationDirectiveRepository {
    val store = mutableMapOf<String, SeparationDirective>()
    override suspend fun add(directive: SeparationDirective) { store[directive.id] = directive }
    override suspend fun addAll(directives: List<SeparationDirective>) {
        directives.forEach { store[it.id] = it }
    }
    override fun observe(projectId: String): Flow<List<SeparationDirective>> =
        flowOf(store.values.filter { it.projectId == projectId })
    override suspend fun getByProject(projectId: String): List<SeparationDirective> =
        store.values.filter { it.projectId == projectId }
    override suspend fun delete(id: String) { store.remove(id) }
    override suspend fun deleteByProject(projectId: String) {
        store.values.filter { it.projectId == projectId }.forEach { store.remove(it.id) }
    }
}
