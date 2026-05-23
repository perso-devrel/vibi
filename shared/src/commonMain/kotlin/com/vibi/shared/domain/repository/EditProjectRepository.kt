package com.vibi.shared.domain.repository

import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.Segment
import kotlinx.coroutines.flow.Flow

interface EditProjectRepository {
    suspend fun createProject(project: EditProject)
    suspend fun createProjectWithSegment(project: EditProject, segment: Segment)
    fun observeProject(projectId: String): Flow<EditProject?>
    suspend fun getProject(projectId: String): EditProject?
    /**
     * @param touchActivity true 면 updatedAt 을 현재시각으로 bump (InputScreen "이어서 작업" 정렬에
     * 반영). false 면 updatedAt 유지 — separation status 변경 등 internal
     * bookkeeping 에 사용. distinctUntilChanged 와 합쳐 무관 emission 차단.
     */
    suspend fun updateProject(project: EditProject, touchActivity: Boolean = true)
    suspend fun deleteProject(projectId: String)

    /** 메인 화면 "이어서 작업" 섹션 source — 최근 updatedAt desc. */
    fun observeAllProjects(): Flow<List<EditProject>>

    /**
     * 7일 만료 cleanup. updatedAt < [thresholdMs] 인 모든 project + 자식 row cascade 삭제.
     * 호출자는 보통 InputScreen 진입 시점. 현재 로그인 계정 한정.
     */
    suspend fun expireOldDrafts(thresholdMs: Long)
}
