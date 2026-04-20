package com.example.dubcast.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "edit_projects")
data class EditProjectEntity(
    @PrimaryKey val projectId: String,
    val videoUri: String,
    val videoDurationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long
)
