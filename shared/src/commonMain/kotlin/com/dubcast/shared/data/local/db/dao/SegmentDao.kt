package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dubcast.shared.data.local.db.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: SegmentEntity)

    @Update
    suspend fun update(segment: SegmentEntity)

    @Query("SELECT * FROM segments WHERE projectId = :projectId ORDER BY `order` ASC")
    fun observeByProjectId(projectId: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE projectId = :projectId ORDER BY `order` ASC")
    suspend fun getByProjectId(projectId: String): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun getById(id: String): SegmentEntity?

    @Query("SELECT MAX(`order`) FROM segments WHERE projectId = :projectId")
    suspend fun getMaxOrder(projectId: String): Int?

    @Query("DELETE FROM segments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM segments WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
