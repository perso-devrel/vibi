package com.vibi.shared.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.TargetLanguage

@Entity(tableName = "edit_projects")
data class EditProjectEntity(
    @PrimaryKey val projectId: String,
    /** JWT sub claim. 같은 기기에 다른 계정이 로그인했을 때 이전 작업 노출 방지용 스코핑 키. */
    val userId: String,
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
    /**
     * JSON array — 동시 진행 중 음원분리 잡들. 빈 문자열이면 비어있음 (legacy 데이터 호환).
     * 항목 스키마: PersistedSeparationJobDto (jobId, segmentId, rangeStartMs?, rangeEndMs?,
     * numberOfSpeakers, muteOriginalSegmentAudio).
     */
    val processingSeparationsJson: String = "",
    /** 가장 최근 BFF audio-only render jobId (RenderKind.AUDIO). 자막/STT/분리 가 사용. */
    val currentAudioRenderJobId: String? = null,
    /** 가장 최근 BFF video render jobId (RenderKind.VIDEO). 자동 더빙이 사용. */
    val currentVideoRenderJobId: String? = null,
    /** 1 = render 후 timeline mutation 있음 (다시 render 필요). 0 = 최신 render 와 동기화. 기본 1. */
    val isRenderStale: Boolean = true,
)
