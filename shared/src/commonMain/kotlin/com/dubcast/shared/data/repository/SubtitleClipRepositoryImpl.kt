package com.dubcast.shared.data.repository

import com.dubcast.shared.data.local.db.dao.SubtitleClipDao
import com.dubcast.shared.data.local.db.entity.SubtitleClipEntity
import com.dubcast.shared.domain.model.Anchor
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.SubtitlePosition
import com.dubcast.shared.domain.model.SubtitleSource
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubtitleClipRepositoryImpl constructor(
    private val dao: SubtitleClipDao
) : SubtitleClipRepository {

    override fun observeClips(projectId: String): Flow<List<SubtitleClip>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun addClip(clip: SubtitleClip) {
        dao.insert(clip.toEntity())
    }

    override suspend fun addClips(clips: List<SubtitleClip>) {
        dao.insertAll(clips.map { it.toEntity() })
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

    override suspend fun deleteClipsBySourceDubClipId(dubClipId: String) {
        dao.deleteBySourceDubClipId(dubClipId)
    }

    override suspend fun deleteAutoSubtitles(projectId: String) {
        dao.deleteAutoSubtitles(projectId)
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
        ),
        sourceDubClipId = sourceDubClipId,
        xPct = xPct,
        yPct = yPct,
        widthPct = widthPct,
        heightPct = heightPct,
        source = runCatching { SubtitleSource.valueOf(source) }.getOrDefault(SubtitleSource.MANUAL),
        languageCode = languageCode,
        fontFamily = fontFamily,
        fontSizeSp = fontSizeSp,
        colorHex = colorHex,
        backgroundColorHex = backgroundColorHex,
    )

    private fun SubtitleClip.toEntity() = SubtitleClipEntity(
        id = id,
        projectId = projectId,
        text = text,
        startMs = startMs,
        endMs = endMs,
        anchor = position.anchor.name.lowercase(),
        yOffsetPct = position.yOffsetPct,
        sourceDubClipId = sourceDubClipId,
        xPct = xPct,
        yPct = yPct,
        widthPct = widthPct,
        heightPct = heightPct,
        source = source.name,
        languageCode = languageCode,
        fontFamily = fontFamily,
        fontSizeSp = fontSizeSp,
        colorHex = colorHex,
        backgroundColorHex = backgroundColorHex,
    )
}
