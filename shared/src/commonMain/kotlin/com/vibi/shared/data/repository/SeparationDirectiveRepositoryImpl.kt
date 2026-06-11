package com.vibi.shared.data.repository

import com.vibi.shared.data.local.db.dao.SeparationDirectiveDao
import com.vibi.shared.data.local.db.entity.SeparationDirectiveEntity
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.repository.StemSelection
import com.vibi.shared.platform.deleteLocalFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SeparationDirectiveRepositoryImpl(
    private val dao: SeparationDirectiveDao
) : SeparationDirectiveRepository {

    override suspend fun add(directive: SeparationDirective) {
        dao.upsert(directive.toEntity())
    }

    override suspend fun addAll(directives: List<SeparationDirective>) {
        if (directives.isEmpty()) return
        dao.upsertAll(directives.map { it.toEntity() })
    }

    override fun observe(projectId: String): Flow<List<SeparationDirective>> =
        dao.observeByProject(projectId).map { rows -> rows.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun getByProject(projectId: String): List<SeparationDirective> =
        dao.getByProject(projectId).map { it.toDomain() }

    override suspend fun delete(id: String) {
        dao.getById(id)?.let { deleteStemFiles(listOf(it)) }
        dao.deleteById(id)
    }

    override suspend fun deleteByProject(projectId: String) {
        deleteStemFiles(dao.getByProject(projectId))
        dao.deleteByProject(projectId)
    }

    /** directive 들에 영구 캐시된 stem 로컬 파일을 디스크에서 제거 — 누적 방지. URL/미캐시는 무시. */
    private fun deleteStemFiles(rows: List<SeparationDirectiveEntity>) {
        rows.forEach { row ->
            row.toDomain().selections.forEach { sel ->
                sel.localPath?.takeIf { it.isNotBlank() }?.let { deleteLocalFile(it) }
            }
        }
    }

    private fun SeparationDirective.toEntity() = SeparationDirectiveEntity(
        id = id,
        projectId = projectId,
        rangeStartMs = rangeStartMs,
        rangeEndMs = rangeEndMs,
        numberOfSpeakers = numberOfSpeakers,
        muteOriginalSegmentAudio = muteOriginalSegmentAudio,
        selectionsJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(StemSelectionDto.serializer()),
            selections.map { StemSelectionDto(it.stemId, it.volume, it.audioUrl, it.selected, it.localPath) }
        ),
        createdAt = createdAt,
        jobId = jobId,
        sourceOffsetMs = sourceOffsetMs,
        segmentId = segmentId,
        localStartMs = localStartMs,
        localEndMs = localEndMs,
    )

    private fun SeparationDirectiveEntity.toDomain(): SeparationDirective {
        val selections: List<StemSelection> = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(StemSelectionDto.serializer()),
                selectionsJson.ifBlank { "[]" }
            ).map { StemSelection(it.stemId, it.volume, it.audioUrl, it.selected, it.localPath) }
        }.getOrDefault(emptyList())
        return SeparationDirective(
            id = id,
            projectId = projectId,
            rangeStartMs = rangeStartMs,
            rangeEndMs = rangeEndMs,
            numberOfSpeakers = numberOfSpeakers,
            muteOriginalSegmentAudio = muteOriginalSegmentAudio,
            selections = selections,
            createdAt = createdAt,
            jobId = jobId,
            sourceOffsetMs = sourceOffsetMs,
            segmentId = segmentId,
            localStartMs = localStartMs,
            localEndMs = localEndMs,
        )
    }

    @Serializable
    private data class StemSelectionDto(
        val stemId: String,
        val volume: Float,
        val audioUrl: String? = null,
        // legacy 데이터(이 필드 없는 row)는 default true — 기존 동작 유지.
        val selected: Boolean = true,
        // 영구 캐시된 stem 로컬 경로. legacy/미캐시 row 는 null.
        val localPath: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
