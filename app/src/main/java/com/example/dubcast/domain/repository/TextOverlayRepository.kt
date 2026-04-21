package com.example.dubcast.domain.repository

import com.example.dubcast.domain.model.TextOverlay
import kotlinx.coroutines.flow.Flow

interface TextOverlayRepository {
    fun observeOverlays(projectId: String): Flow<List<TextOverlay>>
    suspend fun getOverlay(overlayId: String): TextOverlay?
    suspend fun addOverlay(overlay: TextOverlay)
    suspend fun updateOverlay(overlay: TextOverlay)
    suspend fun deleteOverlay(overlayId: String)
    suspend fun deleteAllByProjectId(projectId: String)
}
