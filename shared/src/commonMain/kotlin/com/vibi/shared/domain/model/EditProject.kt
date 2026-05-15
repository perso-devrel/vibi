package com.vibi.shared.domain.model

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
    /**
     * 동시 진행 중인 음원분리 잡들 — 사용자가 여러 구간을 동시에 분리할 수 있어 리스트.
     * 각 entry 는 BFF jobId + range + 화자 수를 포함. Ready/Failed/Consumed 가 되면 entry 제거.
     * 화면 재진입 / 앱 재실행 시 본 리스트의 모든 entry 가 다시 폴링 (Resume) 된다.
     */
    val processingSeparations: List<PersistedSeparationJob> = emptyList(),
    /**
     * BFF 에 가장 최근에 제출한 audio-only render jobId (RenderKind.AUDIO).
     * 자막/STT/음성분리 가 편집 영상을 source 로 쓸 때 `editedRenderJobId` 로 전송하면 BFF 가
     * 캐시된 audio m4a 를 재사용한다. 이전 jobId 는 보존하지 않음 (최신 1개만).
     */
    val currentAudioRenderJobId: String? = null,
    /**
     * BFF 에 가장 최근에 제출한 video render jobId (RenderKind.VIDEO).
     * 자동 더빙이 편집 영상을 source 로 쓸 때 `editedRenderJobId` 로 전송. AUDIO 와 별도 슬롯 —
     * 한 종류 캐시 hit 이 다른 종류로 cross-contaminate 하지 않도록.
     */
    val currentVideoRenderJobId: String? = null,
    /**
     * 마지막 render 후 timeline mutation (segment add/remove/trim/speed/volume/reorder 등) 가
     * 발생했는지. true 면 [currentAudioRenderJobId] / [currentVideoRenderJobId] 모두 신뢰할 수 없어
     * EnsureLatestRenderUseCase 가 요청된 kind 로 새로 render. 신규 프로젝트는 true.
     *
     * jobId 자체는 stale 마킹 시에도 비우지 않음 — 다음 ensureLatestRender 호출이 그 kind 슬롯만
     * 갱신하고 isRenderStale=false 로 떨어뜨리는 방식이 단순. 다른 kind 는 다음 호출에서 새로 render.
     */
    val isRenderStale: Boolean = true,
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

/**
 * 영속화된 음원분리 잡 1건. UI ViewModel 의 ProcessingSeparation 와 달리 clientToken / progress 같은
 * in-memory 전용 필드는 없다 — resume 시 복원에 필요한 최소 정보만.
 *
 * @property rangeStartMs null = 영상 전체 분리 (whole-video).
 */
data class PersistedSeparationJob(
    val jobId: String,
    val segmentId: String,
    val rangeStartMs: Long? = null,
    val rangeEndMs: Long? = null,
    val numberOfSpeakers: Int = 2,
    val muteOriginalSegmentAudio: Boolean = true,
)

/**
 * 자동 자막/더빙 관련 모든 필드를 IDLE 로 리셋 + render 캐시 두 슬롯 비움 + `isRenderStale=true`.
 *
 * 호출자가 추가로 정리할 필드 (separation*, segments volume 등) 는 별도 `.copy()` 로 merge.
 * timeline mutation 시 `TimelineViewModel.invalidateGeneratedResults` (구 `clearGeneratedSubtitleAndDub`)
 * 와 영상편집 commit 시 `resetTimelineDerivedResults` 양쪽이 본 helper 를 공유 — 두 곳에서
 * 자막/더빙 무효화 필드 set 이 어긋나지 않도록 SSOT.
 */
fun EditProject.clearAutoSubtitleDub(): EditProject = copy(
    autoSubtitleStatus = AutoJobStatus.IDLE,
    autoDubStatus = AutoJobStatus.IDLE,
    autoSubtitleError = null,
    autoDubError = null,
    autoSubtitleJobId = null,
    autoDubJobId = null,
    autoDubStatusByLang = emptyMap(),
    autoDubJobIdByLang = emptyMap(),
    dubbedAudioPaths = emptyMap(),
    dubbedVideoPaths = emptyMap(),
    dubbedAudioPath = null,
    pendingReviewTargetLangsCsv = null,
    isRenderStale = true,
    currentAudioRenderJobId = null,
    currentVideoRenderJobId = null,
)

/**
 * 음원분리 영속화 필드만 IDLE 로 리셋. BFF 가 잡을 잃었거나 결과가 expired/Consumed 됐을 때
 * 사용자가 새 분리를 시작할 수 있도록 단일 진입점. [clearAutoSubtitleDub] 와 동등한 SSOT 패턴.
 *
 * 동시 분리 list ([processingSeparations]) 는 본 헬퍼가 건드리지 않는다 — 진행 중인 다른 잡들은
 * "다시 시도"/완료 흐름과 무관하게 보존되어야 함. 영상편집 commit 등 list 자체를 비울 필요가
 * 있는 곳에서만 직접 `copy(processingSeparations = emptyList())`.
 */
fun EditProject.clearSeparation(): EditProject = copy(
    separationJobId = null,
    separationSegmentId = null,
    separationStatus = AutoJobStatus.IDLE,
    separationError = null,
)

/** 새 in-flight 잡을 [processingSeparations] 에 append. 중복 jobId 는 무시. */
fun EditProject.addProcessingSeparation(job: PersistedSeparationJob): EditProject =
    if (processingSeparations.any { it.jobId == job.jobId }) this
    else copy(processingSeparations = processingSeparations + job)

/** jobId 로 [processingSeparations] entry 제거. 일치하는 게 없으면 그대로. */
fun EditProject.removeProcessingSeparation(jobId: String): EditProject =
    copy(processingSeparations = processingSeparations.filter { it.jobId != jobId })

/**
 * 원본 언어 자막 (lang="" SubtitleClip) 이 export variant / preview chip 에 노출 가능한 상태인지.
 *
 * 조건:
 *  - lang="" 클립이 1개 이상 존재
 *  - review 대기 마킹 ([EditProject.pendingReviewTargetLangsCsv]) 가 null
 *    (= review 모드 미사용 또는 사용자 confirm 완료)
 *
 * SSOT — UI chip 노출 ([TimelineScreen]) 과 export variant 산출
 * ([SaveAllVariantsUseCase.computeAllVariantKeys]) 양쪽이 본 헬퍼만 호출.
 * 조건 추가 시 한 곳만 수정.
 */
fun hasConfirmedOriginalSubtitle(
    subtitleClips: List<SubtitleClip>,
    pendingReviewTargetLangsCsv: String?,
): Boolean = subtitleClips.any { it.languageCode.isBlank() } &&
    pendingReviewTargetLangsCsv == null
