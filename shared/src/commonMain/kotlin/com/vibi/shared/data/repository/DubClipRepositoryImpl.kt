package com.vibi.shared.data.repository

import com.vibi.shared.data.local.db.dao.DubClipDao
import com.vibi.shared.data.local.db.entity.DubClipEntity
import com.vibi.shared.domain.model.DubClip
import com.vibi.shared.domain.repository.DubClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DubClipRepositoryImpl constructor(
    private val dao: DubClipDao
) : DubClipRepository {

    override fun observeClips(projectId: String): Flow<List<DubClip>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()

    override suspend fun addClip(clip: DubClip) {
        dao.insert(clip.toEntity())
    }

    override suspend fun addClips(clips: List<DubClip>) {
        if (clips.isEmpty()) return
        dao.insertAll(clips.map { it.toEntity() })
    }

    override suspend fun updateClip(clip: DubClip) {
        dao.update(clip.toEntity())
    }

    override suspend fun deleteClip(clipId: String) {
        dao.deleteById(clipId)
    }

    override suspend fun deleteAllClips(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    override suspend fun getClip(clipId: String): DubClip? {
        return dao.getById(clipId)?.toDomain()
    }

    private fun DubClipEntity.toDomain() = DubClip(
        id = id,
        projectId = projectId,
        text = text,
        voiceId = voiceId,
        voiceName = voiceName,
        audioFilePath = audioFilePath,
        startMs = startMs,
        durationMs = durationMs,
        volume = volume
    )

    private fun DubClip.toEntity() = DubClipEntity(
        id = id,
        projectId = projectId,
        text = text,
        voiceId = voiceId,
        voiceName = voiceName,
        audioFilePath = audioFilePath,
        startMs = startMs,
        durationMs = durationMs,
        volume = volume
    )
}
