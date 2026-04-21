package com.example.dubcast.fake

import com.example.dubcast.domain.model.TextOverlay
import com.example.dubcast.domain.repository.TextOverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTextOverlayRepository : TextOverlayRepository {

    private val overlays = MutableStateFlow<List<TextOverlay>>(emptyList())

    override fun observeOverlays(projectId: String): Flow<List<TextOverlay>> =
        overlays.map { list -> list.filter { it.projectId == projectId }.sortedBy { it.startMs } }

    override suspend fun getOverlay(overlayId: String): TextOverlay? {
        return overlays.value.find { it.id == overlayId }
    }

    override suspend fun addOverlay(overlay: TextOverlay) {
        overlays.value = overlays.value + overlay
    }

    override suspend fun updateOverlay(overlay: TextOverlay) {
        overlays.value = overlays.value.map { if (it.id == overlay.id) overlay else it }
    }

    override suspend fun deleteOverlay(overlayId: String) {
        overlays.value = overlays.value.filter { it.id != overlayId }
    }

    override suspend fun deleteAllByProjectId(projectId: String) {
        overlays.value = overlays.value.filter { it.projectId != projectId }
    }

    fun all(): List<TextOverlay> = overlays.value
}
