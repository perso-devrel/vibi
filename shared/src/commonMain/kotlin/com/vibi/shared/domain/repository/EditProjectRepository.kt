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

    /**
     * [block] 안의 여러 repository/DAO 쓰기를 단일 writer 트랜잭션으로 묶어 all-or-nothing 보장.
     * undo/redo 복원처럼 segment/bgm/directive 를 통째 delete+insert 할 때, 중간 취소·프로세스
     * 종료로 일부만 반영돼 프로젝트가 부분 손상(클립 소실 등)되는 것을 방지. 같은 [VibiDatabase]
     * 의 DAO 호출은 트랜잭션에 합류한다.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T

    /** 메인 화면 "이어서 작업" 섹션 source — 최근 updatedAt desc. */
    fun observeAllProjects(): Flow<List<EditProject>>

    /**
     * 7일 만료 cleanup. updatedAt < [thresholdMs] 인 모든 project + 자식 row cascade 삭제.
     * 호출자는 보통 InputScreen 진입 시점. 현재 로그인 계정 한정.
     */
    suspend fun expireOldDrafts(thresholdMs: Long)
}
