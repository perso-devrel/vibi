package com.example.dubcast.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dub_clips")
data class DubClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val voiceId: String,
    val voiceName: String,
    val audioFilePath: String,
    val startMs: Long,
    val durationMs: Long,
    val volume: Float = 1.0f
)
