package com.vibi.shared.domain.model

data class EditProject(
    val projectId: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 사용자 입력 제목. null 이면 UI 가 createdAt 포맷팅으로 fallback. */
    val title: String? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f,
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
     * 음성분리 가 편집 영상을 source 로 쓸 때 `editedRenderJobId` 로 전송하면 BFF 가
     * 캐시된 audio m4a 를 재사용한다. 이전 jobId 는 보존하지 않음 (최신 1개만).
     */
    val currentAudioRenderJobId: String? = null,
    /**
     * BFF 에 가장 최근에 제출한 video render jobId (RenderKind.VIDEO).
     * AUDIO 와 별도 슬롯 — 한 종류 캐시 hit 이 다른 종류로 cross-contaminate 하지 않도록.
     */
    val currentVideoRenderJobId: String? = null,
    /** 마지막 render 와 timeline 이 어긋나는지 여부 — schema 호환 위해 보존. */
    val isRenderStale: Boolean = true,
) {
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
 * 음원분리 영속화 필드만 IDLE 로 리셋. BFF 가 잡을 잃었거나 결과가 expired/Consumed 됐을 때
 * 사용자가 새 분리를 시작할 수 있도록 단일 진입점.
 *
 * 동시 분리 list ([processingSeparations]) 는 본 헬퍼가 건드리지 않는다 — 진행 중인 다른 잡들은
 * "다시 시도"/완료 흐름과 무관하게 보존되어야 함.
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
