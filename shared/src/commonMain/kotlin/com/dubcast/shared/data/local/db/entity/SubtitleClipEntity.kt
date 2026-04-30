package com.dubcast.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subtitle_clips")
data class SubtitleClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val anchor: String = "bottom",
    val yOffsetPct: Float = 90f,
    val sourceDubClipId: String? = null,
    val xPct: Float? = null,
    val yPct: Float? = null,
    val widthPct: Float? = null,
    val heightPct: Float? = null,
    val source: String = "MANUAL",
    val languageCode: String = "",
    val fontFamily: String = "Noto Sans KR",
    val fontSizeSp: Float = 16f,
    val colorHex: String = "#FFFFFFFF",
    val backgroundColorHex: String = "#80000000",
)
