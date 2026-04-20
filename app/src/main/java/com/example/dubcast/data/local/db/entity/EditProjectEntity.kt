package com.example.dubcast.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "edit_projects")
data class EditProjectEntity(
    @PrimaryKey val projectId: String,
    val createdAt: Long,
    val updatedAt: Long
)
