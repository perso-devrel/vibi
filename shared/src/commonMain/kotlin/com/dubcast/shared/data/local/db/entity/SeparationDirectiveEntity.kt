package com.dubcast.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "separation_directives")
data class SeparationDirectiveEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    /** JSON encoded `List<{stemId, volume, audioUrl?}>`. */
    val selectionsJson: String,
    val createdAt: Long
)
