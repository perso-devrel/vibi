package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.SubtitleClipDao
import com.example.dubcast.data.local.db.entity.SubtitleClipEntity
import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import com.example.dubcast.domain.repository.SubtitleClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SubtitleClipRepositoryImpl @Inject constructor(
    private val dao: SubtitleClipDao
) : SubtitleClipRepository {

    override fun observeClips(projectId: String): Flow<List<SubtitleClip>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun addClip(clip: SubtitleClip) {
        dao.insert(clip.toEntity())
    }

    override suspend fun updateClip(clip: SubtitleClip) {
        dao.update(clip.toEntity())
    }

    override suspend fun deleteClip(clipId: String) {
        dao.deleteById(clipId)
    }

    override suspend fun deleteAllClips(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    override suspend fun getClip(clipId: String): SubtitleClip? {
        return dao.getById(clipId)?.toDomain()
    }

    private fun SubtitleClipEntity.toDomain() = SubtitleClip(
        id = id,
        projectId = projectId,
        text = text,
        startMs = startMs,
        endMs = endMs,
        position = SubtitlePosition(
            anchor = Anchor.fromString(anchor),
            yOffsetPct = yOffsetPct
        )
    )

    private fun SubtitleClip.toEntity() = SubtitleClipEntity(
        id = id,
        projectId = projectId,
        text = text,
        startMs = startMs,
        endMs = endMs,
        anchor = position.anchor.name.lowercase(),
        yOffsetPct = position.yOffsetPct
    )
}
