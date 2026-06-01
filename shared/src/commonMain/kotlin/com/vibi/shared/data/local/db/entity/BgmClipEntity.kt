package com.vibi.shared.data.local.db.entity

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
    val sourceTrimStartMs: Long = 0L,
    val sourceTrimEndMs: Long = 0L,
    val createdAt: Long = 0L,
    val originalSourceUri: String? = null,
    val voiceOnlyUri: String? = null,
    /** 사용자가 지정한 표시 이름. null/blank 이면 UI 가 sourceUri 파일명으로 자동 라벨링. */
    val customName: String? = null,
)
