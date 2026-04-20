package com.example.dubcast.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "segments")
data class SegmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val type: String,
    @ColumnInfo(name = "order") val order: Int,
    val sourceUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val imageXPct: Float = 50f,
    val imageYPct: Float = 50f,
    val imageWidthPct: Float = 50f,
    val imageHeightPct: Float = 50f
)
