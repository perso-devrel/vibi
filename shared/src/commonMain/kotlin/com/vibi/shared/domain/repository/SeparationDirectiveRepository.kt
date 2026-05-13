package com.vibi.shared.domain.repository

import com.vibi.shared.domain.model.SeparationDirective
import kotlinx.coroutines.flow.Flow

interface SeparationDirectiveRepository {
    suspend fun add(directive: SeparationDirective)
    fun observe(projectId: String): Flow<List<SeparationDirective>>
    suspend fun getByProject(projectId: String): List<SeparationDirective>
    suspend fun delete(id: String)
    suspend fun deleteByProject(projectId: String)
}
