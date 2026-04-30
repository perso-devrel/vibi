package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dubcast.shared.data.local.db.entity.BgmClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BgmClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: BgmClipEntity)

    @Update
    suspend fun update(clip: BgmClipEntity)

    @Query("SELECT * FROM bgm_clips WHERE projectId = :projectId ORDER BY startMs ASC")
    fun getByProjectId(projectId: String): Flow<List<BgmClipEntity>>

    @Query("SELECT * FROM bgm_clips WHERE id = :id")
    suspend fun getById(id: String): BgmClipEntity?

    @Query("DELETE FROM bgm_clips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bgm_clips WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
