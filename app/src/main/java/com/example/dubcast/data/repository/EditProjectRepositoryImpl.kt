package com.example.dubcast.data.repository

import androidx.room.withTransaction
import com.example.dubcast.data.local.db.DubCastDatabase
import com.example.dubcast.data.local.db.dao.EditProjectDao
import com.example.dubcast.data.local.db.dao.SegmentDao
import com.example.dubcast.data.local.db.entity.EditProjectEntity
import com.example.dubcast.data.local.db.entity.SegmentEntity
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.repository.EditProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EditProjectRepositoryImpl @Inject constructor(
    private val database: DubCastDatabase,
    private val dao: EditProjectDao,
    private val segmentDao: SegmentDao
) : EditProjectRepository {

    override suspend fun createProject(project: EditProject) {
        dao.insert(project.toEntity())
    }

    override suspend fun createProjectWithSegment(project: EditProject, segment: Segment) {
        require(segment.projectId == project.projectId) {
            "Segment.projectId must match EditProject.projectId"
        }
        database.withTransaction {
            dao.insert(project.toEntity())
            segmentDao.insert(segment.toEntity())
        }
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
        createdAt = createdAt,
        updatedAt = updatedAt,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct,
        videoOffsetYPct = videoOffsetYPct
    )

    private fun EditProject.toEntity() = EditProjectEntity(
        projectId = projectId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct,
        videoOffsetYPct = videoOffsetYPct
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
        imageHeightPct = imageHeightPct
    )
}
