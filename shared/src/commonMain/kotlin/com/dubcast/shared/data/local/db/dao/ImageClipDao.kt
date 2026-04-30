package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dubcast.shared.data.local.db.entity.ImageClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: ImageClipEntity)

    @Update
    suspend fun update(clip: ImageClipEntity)

    @Query("SELECT * FROM image_clips WHERE projectId = :projectId ORDER BY startMs ASC")
    fun getByProjectId(projectId: String): Flow<List<ImageClipEntity>>

    @Query("SELECT * FROM image_clips WHERE id = :id")
    suspend fun getById(id: String): ImageClipEntity?

    @Query("DELETE FROM image_clips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM image_clips WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
