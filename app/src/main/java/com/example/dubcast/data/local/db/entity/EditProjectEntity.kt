package com.example.dubcast.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.dubcast.domain.model.EditProject
import com.example.dubcast.domain.model.TargetLanguage

@Entity(tableName = "edit_projects")
data class EditProjectEntity(
    @PrimaryKey val projectId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = EditProject.DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f,
    val targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
    val enableAutoDubbing: Boolean = false,
    val enableAutoSubtitles: Boolean = false,
    val numberOfSpeakers: Int = 1,
    val dubbedAudioPath: String? = null,
    val autoSubtitleStatus: String = "IDLE",
    val autoDubStatus: String = "IDLE",
    val autoSubtitleJobId: String? = null,
    val autoDubJobId: String? = null,
    val autoSubtitleError: String? = null,
    val autoDubError: String? = null
)
