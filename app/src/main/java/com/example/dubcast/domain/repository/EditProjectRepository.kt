package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.EditProject
import kotlinx.coroutines.flow.Flow

interface EditProjectRepository {
    suspend fun createProject(project: EditProject)
    fun observeProject(projectId: String): Flow<EditProject?>
    suspend fun getProject(projectId: String): EditProject?
    suspend fun updateProject(project: EditProject)
    suspend fun deleteProject(projectId: String)
}
