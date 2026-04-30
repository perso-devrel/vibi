package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dubcast.shared.data.local.db.entity.SeparationDirectiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeparationDirectiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(directive: SeparationDirectiveEntity)

    @Query("SELECT * FROM separation_directives WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun observeByProject(projectId: String): Flow<List<SeparationDirectiveEntity>>

    @Query("SELECT * FROM separation_directives WHERE projectId = :projectId ORDER BY createdAt ASC")
    suspend fun getByProject(projectId: String): List<SeparationDirectiveEntity>

    @Query("DELETE FROM separation_directives WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM separation_directives WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}
