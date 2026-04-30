package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.Segment
import kotlinx.coroutines.flow.Flow

interface EditProjectRepository {
    suspend fun createProject(project: EditProject)
    suspend fun createProjectWithSegment(project: EditProject, segment: Segment)
    fun observeProject(projectId: String): Flow<EditProject?>
    suspend fun getProject(projectId: String): EditProject?
    suspend fun updateProject(project: EditProject)
    suspend fun deleteProject(projectId: String)

    /** 메인 화면 "이어서 작업" 섹션 source — 최근 updatedAt desc. */
    fun observeAllProjects(): Flow<List<EditProject>>

    /**
     * 7일 만료 cleanup. updatedAt < [thresholdMs] 인 모든 project + 자식 row cascade 삭제.
     * 호출자는 보통 InputScreen 진입 시점.
     */
    suspend fun expireOldDrafts(thresholdMs: Long)
}
