package com.example.dubcast.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dubcast.data.local.db.entity.SubtitleClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: SubtitleClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clips: List<SubtitleClipEntity>)

    @Update
    suspend fun update(clip: SubtitleClipEntity)

    @Query("SELECT * FROM subtitle_clips WHERE projectId = :projectId ORDER BY startMs ASC")
    fun getByProjectId(projectId: String): Flow<List<SubtitleClipEntity>>

    @Query("SELECT * FROM subtitle_clips WHERE id = :id")
    suspend fun getById(id: String): SubtitleClipEntity?

    @Query("DELETE FROM subtitle_clips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subtitle_clips WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
