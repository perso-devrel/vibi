package com.example.dubcast.fake

import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.repository.EditProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeEditProjectRepository : EditProjectRepository {

    private val projects = MutableStateFlow<List<EditProject>>(emptyList())

    override suspend fun createProject(project: EditProject) {
        projects.value = projects.value + project
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
