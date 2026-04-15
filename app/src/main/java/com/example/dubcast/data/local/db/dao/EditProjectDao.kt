package com.example.dubcast.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dubcast.data.local.db.entity.EditProjectEntity
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

    @Query("SELECT * FROM edit_projects ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<EditProjectEntity>>

    @Query("DELETE FROM edit_projects WHERE projectId = :projectId")
    suspend fun deleteById(projectId: String)
}
