package com.dubcast.shared.data.repository

import com.dubcast.shared.data.local.db.dao.SeparationDirectiveDao
import com.dubcast.shared.data.local.db.entity.SeparationDirectiveEntity
import com.dubcast.shared.domain.model.SeparationDirective
import com.dubcast.shared.domain.repository.SeparationDirectiveRepository
import com.dubcast.shared.domain.repository.StemSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SeparationDirectiveRepositoryImpl(
    private val dao: SeparationDirectiveDao
) : SeparationDirectiveRepository {

    override suspend fun add(directive: SeparationDirective) {
        dao.upsert(directive.toEntity())
    }

    override fun observe(projectId: String): Flow<List<SeparationDirective>> =
        dao.observeByProject(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getByProject(projectId: String): List<SeparationDirective> =
        dao.getByProject(projectId).map { it.toDomain() }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
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
            selections.map { StemSelectionDto(it.stemId, it.volume, it.audioUrl, it.selected) }
        ),
        createdAt = createdAt
    )

    private fun SeparationDirectiveEntity.toDomain(): SeparationDirective {
        val selections: List<StemSelection> = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(StemSelectionDto.serializer()),
                selectionsJson.ifBlank { "[]" }
            ).map { StemSelection(it.stemId, it.volume, it.audioUrl, it.selected) }
        }.getOrDefault(emptyList())
        return SeparationDirective(
            id = id,
            projectId = projectId,
            rangeStartMs = rangeStartMs,
            rangeEndMs = rangeEndMs,
            numberOfSpeakers = numberOfSpeakers,
            muteOriginalSegmentAudio = muteOriginalSegmentAudio,
            selections = selections,
            createdAt = createdAt
        )
    }

    @Serializable
    private data class StemSelectionDto(
        val stemId: String,
        val volume: Float,
        val audioUrl: String? = null,
        // legacy 데이터(이 필드 없는 row)는 default true — 기존 동작 유지.
        val selected: Boolean = true
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
