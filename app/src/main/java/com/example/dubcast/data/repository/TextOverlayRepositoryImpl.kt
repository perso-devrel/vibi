package com.example.dubcast.data.repository

import com.example.dubcast.data.local.db.dao.TextOverlayDao
import com.example.dubcast.data.local.db.entity.TextOverlayEntity
import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.domain.repository.TextOverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TextOverlayRepositoryImpl @Inject constructor(
    private val dao: TextOverlayDao
) : TextOverlayRepository {

    override fun observeOverlays(projectId: String): Flow<List<TextOverlay>> =
        dao.getByProjectId(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOverlay(overlayId: String): TextOverlay? {
        return dao.getById(overlayId)?.toDomain()
    }

    override suspend fun addOverlay(overlay: TextOverlay) {
        dao.insert(overlay.toEntity())
    }

    override suspend fun updateOverlay(overlay: TextOverlay) {
        dao.update(overlay.toEntity())
    }

    override suspend fun deleteOverlay(overlayId: String) {
        dao.deleteById(overlayId)
    }

    override suspend fun deleteAllByProjectId(projectId: String) {
        dao.deleteByProjectId(projectId)
    }

    private fun TextOverlayEntity.toDomain() = TextOverlay(
        id = id,
        projectId = projectId,
        text = text,
        fontFamily = fontFamily,
        fontSizeSp = fontSizeSp,
        colorHex = colorHex,
        startMs = startMs,
        endMs = endMs,
        xPct = xPct,
        yPct = yPct,
        lane = lane
    )

    private fun TextOverlay.toEntity() = TextOverlayEntity(
        id = id,
        projectId = projectId,
        text = text,
        fontFamily = fontFamily,
        fontSizeSp = fontSizeSp,
        colorHex = colorHex,
        startMs = startMs,
        endMs = endMs,
        xPct = xPct,
        yPct = yPct,
        lane = lane
    )
}
