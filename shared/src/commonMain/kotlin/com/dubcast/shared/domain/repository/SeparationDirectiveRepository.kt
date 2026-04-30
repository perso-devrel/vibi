package com.dubcast.shared.domain.repository

import com.dubcast.shared.domain.model.SeparationDirective
import kotlinx.coroutines.flow.Flow

interface SeparationDirectiveRepository {
    suspend fun add(directive: SeparationDirective)
    fun observe(projectId: String): Flow<List<SeparationDirective>>
    suspend fun getByProject(projectId: String): List<SeparationDirective>
    suspend fun delete(id: String)
}
