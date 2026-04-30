package com.dubcast.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dubcast.shared.domain.model.TextOverlay

@Entity(tableName = "text_overlays")
data class TextOverlayEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val fontFamily: String = TextOverlay.DEFAULT_FONT_FAMILY,
    val fontSizeSp: Float = TextOverlay.DEFAULT_FONT_SIZE_SP,
    val colorHex: String = TextOverlay.DEFAULT_COLOR_HEX,
    val startMs: Long,
    val endMs: Long,
    val xPct: Float = 50f,
    val yPct: Float = 50f,
    val lane: Int = 0
)
