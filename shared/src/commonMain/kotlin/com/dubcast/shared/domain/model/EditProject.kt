package com.dubcast.shared.domain.model

data class EditProject(
    val projectId: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 사용자 입력 제목. null 이면 UI 가 createdAt 포맷팅으로 fallback. */
    val title: String? = null,
    /** STT 검토 대기 중인 target 언어들 (CSV). null/"" = 검토 대기 없음. */
    val pendingReviewTargetLangsCsv: String? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f,
    val targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
    /**
     * my_plan 의 "여러 언어 선택 가능" 대응. 비어있으면 [targetLanguageCode] 단일 값으로 폴백 (구 데이터 호환).
     */
    val targetLanguageCodes: List<String> = emptyList(),
    val enableAutoDubbing: Boolean = false,
    val enableAutoSubtitles: Boolean = false,
    /** my_plan 의 "편집 화면에 뭐 띄울지" — 자막 표시 토글. */
    val showSubtitlesOnPreview: Boolean = true,
    /** my_plan 의 "편집 화면에 뭐 띄울지" — 더빙 표시 토글. */
    val showDubbingOnPreview: Boolean = true,
    val numberOfSpeakers: Int = 1,
    /** legacy 단일 더빙 경로 — 호환용. 신규 코드는 [dubbedAudioPaths] 사용. */
    val dubbedAudioPath: String? = null,
    /**
     * my_plan: 언어별 자동 더빙 결과 mp3 경로 맵.
     * 키 = 언어 코드 (en/jp 등), 값 = 로컬 mp3 절대 경로.
     * 사용자가 선택한 [targetLanguageCodes] 중 자동 더빙이 완료된 언어들.
     */
    val dubbedAudioPaths: Map<String, String> = emptyMap(),
    /**
     * 언어별 자동 더빙 결과 mp4 경로 맵 (BFF 가 video+dubAudio 를 ffmpeg mux 한 결과).
     * 미리보기에서 cinterop audio mute 우회 용도 — 단일 player 로 깨끗하게 swap 가능.
     */
    val dubbedVideoPaths: Map<String, String> = emptyMap(),
    /** 언어별 자동 더빙 진행 상태. */
    val autoDubStatusByLang: Map<String, AutoJobStatus> = emptyMap(),
    /** 언어별 자동 더빙 잡 ID (폴링용). */
    val autoDubJobIdByLang: Map<String, String> = emptyMap(),
    val autoSubtitleStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val autoDubStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val autoSubtitleJobId: String? = null,
    val autoDubJobId: String? = null,
    val autoSubtitleError: String? = null,
    val autoDubError: String? = null,
    /** 음성분리 영속화 — 진행 중/완료된 잡 영속화로 백그라운드 실행 + 화면 재진입 시 자동 재개. */
    val separationJobId: String? = null,
    val separationSegmentId: String? = null,
    val separationNumberOfSpeakers: Int = 2,
    val separationMuteOriginal: Boolean = true,
    val separationStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val separationError: String? = null,
) {
    val isAutoLocalizationEnabled: Boolean
        get() = enableAutoSubtitles && enableAutoDubbing

    /**
     * 다중 언어 출력 효과 — 비어있으면 [targetLanguageCode] 단일 값.
     */
    val effectiveTargetLanguages: List<String>
        get() = targetLanguageCodes.ifEmpty { listOf(targetLanguageCode) }

    companion object {
        const val DEFAULT_BACKGROUND_COLOR_HEX = "#000000"
        const val DEFAULT_VIDEO_SCALE = 1f
        const val MIN_VIDEO_SCALE = 0.25f
        const val MAX_VIDEO_SCALE = 4f
        const val MAX_VIDEO_OFFSET_PCT = 100f
    }
}

enum class AutoJobStatus { IDLE, RUNNING, READY, FAILED }
