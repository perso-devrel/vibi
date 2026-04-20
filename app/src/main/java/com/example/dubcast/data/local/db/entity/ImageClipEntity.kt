package com.example.dubcast.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_clips")
data class ImageClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val imageUri: String,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    val widthPct: Float = 30f,
    val heightPct: Float = 30f
)
