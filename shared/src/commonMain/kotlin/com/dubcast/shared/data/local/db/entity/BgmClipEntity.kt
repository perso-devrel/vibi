package com.dubcast.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bgm_clips")
data class BgmClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val startMs: Long,
    val volumeScale: Float = 1.0f,
    val speedScale: Float = 1.0f,
)
