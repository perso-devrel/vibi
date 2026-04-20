package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.EditProjectDao
import com.example.dubcast.data.local.db.entity.EditProjectEntity
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.repository.EditProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EditProjectRepositoryImpl @Inject constructor(
    private val dao: EditProjectDao
) : EditProjectRepository {

    override suspend fun createProject(project: EditProject) {
        dao.insert(project.toEntity())
    }

    override fun observeProject(projectId: String): Flow<EditProject?> =
        dao.observeById(projectId).map { it?.toDomain() }

    override suspend fun getProject(projectId: String): EditProject? {
        return dao.getById(projectId)?.toDomain()
    }

    override suspend fun updateProject(project: EditProject) {
        dao.update(project.toEntity())
    }

    override suspend fun deleteProject(projectId: String) {
        dao.deleteById(projectId)
    }

    private fun EditProjectEntity.toDomain() = EditProject(
        projectId = projectId,
        videoUri = videoUri,
        videoDurationMs = videoDurationMs,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun EditProject.toEntity() = EditProjectEntity(
        projectId = projectId,
        videoUri = videoUri,
        videoDurationMs = videoDurationMs,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
