package com.dubcast.shared.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dubcast.shared.data.local.db.entity.EditProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EditProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: EditProjectEntity)

    @Update
    suspend fun update(project: EditProjectEntity)

    @Query("SELECT * FROM edit_projects WHERE projectId = :projectId")
    suspend fun getById(projectId: String): EditProjectEntity?

    @Query("SELECT * FROM edit_projects WHERE projectId = :projectId")
    fun observeById(projectId: String): Flow<EditProjectEntity?>

    /** 메인 화면 "이어서 작업" 카드용 — 최근 편집 순. */
    @Query("SELECT * FROM edit_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<EditProjectEntity>>

    /** 7일 만료 cleanup 용 — threshold 미만 updatedAt 의 projectId. */
    @Query("SELECT projectId FROM edit_projects WHERE updatedAt < :threshold")
    suspend fun getExpiredIds(threshold: Long): List<String>

    @Query("DELETE FROM edit_projects WHERE projectId = :projectId")
    suspend fun deleteById(projectId: String)
}
