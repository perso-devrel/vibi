package com.example.dubcast.fake

import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.repository.EditProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeEditProjectRepository(
    private val segmentRepository: FakeSegmentRepository? = null
) : EditProjectRepository {

    private val projects = MutableStateFlow<List<EditProject>>(emptyList())

    var failOnSegmentInsert: Boolean = false

    override suspend fun createProject(project: EditProject) {
        projects.value = projects.value + project
    }

    override suspend fun createProjectWithSegment(project: EditProject, segment: Segment) {
        require(project.projectId == segment.projectId)
        val segmentRepo = segmentRepository
            ?: throw IllegalStateException("FakeEditProjectRepository was constructed without a segment repo")
        val existing = projects.value
        projects.value = existing + project
        if (failOnSegmentInsert) {
            // Simulate transaction rollback
            projects.value = existing
            throw RuntimeException("segment insert failed")
        }
        segmentRepo.addSegment(segment)
    }

    override fun observeProject(projectId: String): Flow<EditProject?> =
        projects.map { list -> list.find { it.projectId == projectId } }

    override suspend fun getProject(projectId: String): EditProject? {
        return projects.value.find { it.projectId == projectId }
    }

    override suspend fun updateProject(project: EditProject) {
        projects.value = projects.value.map { if (it.projectId == project.projectId) project else it }
    }

    override suspend fun deleteProject(projectId: String) {
        projects.value = projects.value.filter { it.projectId != projectId }
    }
}
