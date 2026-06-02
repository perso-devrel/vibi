package com.vibi.shared.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.vibi.shared.data.local.UserSession
import com.vibi.shared.data.local.db.VibiDatabase
import com.vibi.shared.data.local.db.dao.BgmClipDao
import com.vibi.shared.data.local.db.dao.EditProjectDao
import com.vibi.shared.data.local.db.dao.SegmentDao
import com.vibi.shared.data.local.db.dao.SeparationDirectiveDao
import com.vibi.shared.data.local.db.entity.EditProjectEntity
import com.vibi.shared.data.local.db.entity.SegmentEntity
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.PersistedSeparationJob
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.platform.currentTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class EditProjectRepositoryImpl constructor(
    private val database: VibiDatabase,
    private val dao: EditProjectDao,
    private val segmentDao: SegmentDao,
    private val bgmClipDao: BgmClipDao,
    private val separationDirectiveDao: SeparationDirectiveDao,
    private val userSession: UserSession,
) : EditProjectRepository {

    override suspend fun createProject(project: EditProject) {
        dao.insert(project.toEntity())
    }

    override suspend fun createProjectWithSegment(project: EditProject, segment: Segment) {
        require(segment.projectId == project.projectId) {
            "Segment.projectId must match EditProject.projectId"
        }
        database.useWriterConnection { conn ->
            conn.immediateTransaction {
                dao.insert(project.toEntity())
                segmentDao.insert(segment.toEntity())
            }
        }
    }

    override fun observeProject(projectId: String): Flow<EditProject?> =
        dao.observeById(projectId).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getProject(projectId: String): EditProject? {
        return dao.getById(projectId)?.toDomain()
    }

    override suspend fun updateProject(project: EditProject, touchActivity: Boolean) {
        // touchActivity=true: InputScreen "이어서 작업" 카드 정렬을 위해 updatedAt bump (default).
        // touchActivity=false: internal job bookkeeping (separation status 등) —
        // updatedAt 유지하면 observeProject 의 distinctUntilChanged 가 무변화 update 를 dedup.
        val toPersist = if (touchActivity) project.copy(updatedAt = currentTimeMillis()) else project
        dao.update(toPersist.toEntity())
    }

    override suspend fun deleteProject(projectId: String) {
        cascadeDeleteProject(projectId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllProjects(): Flow<List<EditProject>> =
        userSession.userId.flatMapLatest { uid ->
            dao.observeAllForUser(uid).map { list -> list.map { it.toDomain() } }
        }.distinctUntilChanged()

    override suspend fun expireOldDrafts(thresholdMs: Long) {
        // updatedAt < threshold 인 projectId 들 fetch 후 각각 cascade.
        // 자식 cascade 삭제도 runCatching 로 감싸 일부 실패 시 다음 project 진행.
        val uid = userSession.current()
        val expiredIds = runCatching { dao.getExpiredIdsForUser(uid, thresholdMs) }
            .getOrDefault(emptyList())
        expiredIds.forEach { id ->
            runCatching { cascadeDeleteProject(id) }
        }
    }

    private suspend fun cascadeDeleteProject(projectId: String) {
        // 자식 row 들 — Room FK ON DELETE CASCADE 미설정 환경에서 명시 cleanup.
        runCatching { segmentDao.deleteByProjectId(projectId) }
        runCatching { bgmClipDao.deleteByProjectId(projectId) }
        runCatching { separationDirectiveDao.deleteByProject(projectId) }
        dao.deleteById(projectId)
    }

    private fun EditProjectEntity.toDomain() = EditProject(
        projectId = projectId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct,
        videoOffsetYPct = videoOffsetYPct,
        separationJobId = separationJobId,
        separationSegmentId = separationSegmentId,
        separationNumberOfSpeakers = separationNumberOfSpeakers,
        separationMuteOriginal = separationMuteOriginal,
        separationStatus = parseStatus(separationStatus),
        separationError = separationError,
        processingSeparations = decodeProcessingSeparations(processingSeparationsJson),
    )

    private fun EditProject.toEntity() = EditProjectEntity(
        projectId = projectId,
        // 도메인 모델에는 userId 가 없음 (multi-account 는 클라이언트 로컬 관심사).
        // 항상 현재 로그인 계정으로 stamp — 다른 계정의 row 는 update 경로로 흘러올 수 없음.
        userId = userSession.current(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        backgroundColorHex = backgroundColorHex,
        videoScale = videoScale,
        videoOffsetXPct = videoOffsetXPct,
        videoOffsetYPct = videoOffsetYPct,
        separationJobId = separationJobId,
        separationSegmentId = separationSegmentId,
        separationNumberOfSpeakers = separationNumberOfSpeakers,
        separationMuteOriginal = separationMuteOriginal,
        separationStatus = separationStatus.name,
        separationError = separationError,
        processingSeparationsJson = encodeProcessingSeparations(processingSeparations),
    )

    private fun parseStatus(name: String): AutoJobStatus =
        runCatching { AutoJobStatus.valueOf(name) }.getOrDefault(AutoJobStatus.IDLE)

    @Serializable
    private data class PersistedSeparationJobDto(
        val jobId: String,
        val segmentId: String,
        val rangeStartMs: Long? = null,
        val rangeEndMs: Long? = null,
        val numberOfSpeakers: Int = 2,
        val muteOriginalSegmentAudio: Boolean = true,
    )

    private val processingSeparationsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val processingSeparationsSerializer =
        ListSerializer(PersistedSeparationJobDto.serializer())

    private fun encodeProcessingSeparations(list: List<PersistedSeparationJob>): String {
        if (list.isEmpty()) return ""
        return processingSeparationsJson.encodeToString(
            processingSeparationsSerializer,
            list.map {
                PersistedSeparationJobDto(
                    jobId = it.jobId,
                    segmentId = it.segmentId,
                    rangeStartMs = it.rangeStartMs,
                    rangeEndMs = it.rangeEndMs,
                    numberOfSpeakers = it.numberOfSpeakers,
                    muteOriginalSegmentAudio = it.muteOriginalSegmentAudio,
                )
            }
        )
    }

    private fun decodeProcessingSeparations(json: String): List<PersistedSeparationJob> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            processingSeparationsJson.decodeFromString(processingSeparationsSerializer, json)
                .map {
                    PersistedSeparationJob(
                        jobId = it.jobId,
                        segmentId = it.segmentId,
                        rangeStartMs = it.rangeStartMs,
                        rangeEndMs = it.rangeEndMs,
                        numberOfSpeakers = it.numberOfSpeakers,
                        muteOriginalSegmentAudio = it.muteOriginalSegmentAudio,
                    )
                }
        }.getOrDefault(emptyList())
    }

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
        trimEndMs = trimEndMs
    )
}
