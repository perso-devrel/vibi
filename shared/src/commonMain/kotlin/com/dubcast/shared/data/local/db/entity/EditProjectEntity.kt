package com.dubcast.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.TargetLanguage

@Entity(tableName = "edit_projects")
data class EditProjectEntity(
    @PrimaryKey val projectId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String? = null,
    val pendingReviewTargetLangsCsv: String? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = EditProject.DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f,
    val targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
    /** JSON array string of language codes ("[\"ko\",\"en\"]"). 빈 문자열이면 targetLanguageCode 단일. */
    val targetLanguageCodesJson: String = "",
    val enableAutoDubbing: Boolean = false,
    val enableAutoSubtitles: Boolean = false,
    val showSubtitlesOnPreview: Boolean = true,
    val showDubbingOnPreview: Boolean = true,
    val numberOfSpeakers: Int = 1,
    val dubbedAudioPath: String? = null,
    /** JSON map ("{\"en\":\"/.../en.mp3\",\"jp\":\"/.../jp.mp3\"}"). 빈 문자열이면 비어있음. */
    val dubbedAudioPathsJson: String = "",
    /** JSON map ("{\"en\":\"/.../en.mp4\",\"jp\":\"/.../jp.mp4\"}"). 미리보기 video swap 용. */
    val dubbedVideoPathsJson: String = "",
    /** JSON map ("{\"en\":\"RUNNING\",\"jp\":\"READY\"}"). */
    val autoDubStatusByLangJson: String = "",
    /** JSON map ("{\"en\":\"job-...\",\"jp\":\"job-...\"}"). */
    val autoDubJobIdByLangJson: String = "",
    val autoSubtitleStatus: String = "IDLE",
    val autoDubStatus: String = "IDLE",
    val autoSubtitleJobId: String? = null,
    val autoDubJobId: String? = null,
    val autoSubtitleError: String? = null,
    val autoDubError: String? = null,
    val separationJobId: String? = null,
    val separationSegmentId: String? = null,
    val separationNumberOfSpeakers: Int = 2,
    val separationMuteOriginal: Boolean = true,
    val separationStatus: String = "IDLE",
    val separationError: String? = null,
)
