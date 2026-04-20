package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.Segment
import kotlinx.coroutines.flow.Flow

interface EditProjectRepository {
    suspend fun createProject(project: EditProject)
    suspend fun createProjectWithSegment(project: EditProject, segment: Segment)
    fun observeProject(projectId: String): Flow<EditProject?>
    suspend fun getProject(projectId: String): EditProject?
    suspend fun updateProject(project: EditProject)
    suspend fun deleteProject(projectId: String)
}
