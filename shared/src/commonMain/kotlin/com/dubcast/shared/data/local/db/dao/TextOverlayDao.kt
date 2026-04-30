package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dubcast.shared.data.local.db.entity.TextOverlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextOverlayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(overlay: TextOverlayEntity)

    @Update
    suspend fun update(overlay: TextOverlayEntity)

    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY startMs ASC")
    fun getByProjectId(projectId: String): Flow<List<TextOverlayEntity>>

    @Query("SELECT * FROM text_overlays WHERE id = :id")
    suspend fun getById(id: String): TextOverlayEntity?

    @Query("DELETE FROM text_overlays WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM text_overlays WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
