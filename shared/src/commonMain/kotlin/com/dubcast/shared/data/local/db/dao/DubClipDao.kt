package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dubcast.shared.data.local.db.entity.DubClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DubClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: DubClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clips: List<DubClipEntity>)

    @Update
    suspend fun update(clip: DubClipEntity)

    @Query("SELECT * FROM dub_clips WHERE projectId = :projectId ORDER BY startMs ASC")
    fun getByProjectId(projectId: String): Flow<List<DubClipEntity>>

    @Query("SELECT * FROM dub_clips WHERE id = :id")
    suspend fun getById(id: String): DubClipEntity?

    @Query("DELETE FROM dub_clips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM dub_clips WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
