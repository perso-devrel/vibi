package com.vibi.shared.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.domain.error.InsufficientCreditsException
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.PersistedSeparationJob
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.ValidationError
import com.vibi.shared.domain.model.ValidationResult
import com.vibi.shared.domain.model.VideoInfo
import com.vibi.shared.domain.model.addProcessingSeparation
import com.vibi.shared.domain.model.clearSeparation
import com.vibi.shared.domain.model.removeProcessingSeparation
import com.vibi.shared.data.repository.AuthRepository
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.repository.SeparationStatus
import com.vibi.shared.domain.repository.StemSelection
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.AudioSourceKind
import com.vibi.shared.platform.VideoThumbnailExtractor
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.platform.generateId
import com.vibi.shared.domain.usecase.draft.ExpireOldDraftsUseCase
import com.vibi.shared.domain.usecase.input.CreateProjectWithInitialVideoSegmentUseCase
import com.vibi.shared.domain.usecase.input.ValidateVideoUseCase
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.domain.usecase.separation.PollSeparationUseCase
import com.vibi.shared.domain.usecase.separation.StartAudioSeparationUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class InputUiState(
    val selectedVideo: VideoInfo? = null,
    val validationResult: ValidationResult? = null,
    val isExtracting: Boolean = false,
    /**
     * "작업 준비중" 섹션 — 영상 선택 직후 백그라운드로 전체 음원분리가 도는 프로젝트들. drafts 보다
     * 위에 노출되며, 분리가 완료되면 해당 프로젝트가 여기서 빠지고 [drafts] 로 내려간다.
     */
    val preparing: List<PreparingSummary> = emptyList(),
    /** 메인 화면 "이어서 작업" 카드 데이터 — 자동 저장된 EditProject 들의 요약. */
    val drafts: List<DraftSummary> = emptyList(),
)

/**
 * "이어서 작업" 카드 한 장에 필요한 최소 정보. EditProject 전체를 UI 로 흘리지 않기 위함.
 *
 * @param jobsRunningSummary RUNNING 인 음원분리 잡 수 한 줄. 0이면 null.
 */
data class DraftSummary(
    val projectId: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val jobsRunningSummary: String?,
    val thumbnailPath: String? = null,
)

/**
 * "작업 준비중" 카드 한 장. 영상 전체 음원분리 진행 상태를 표시.
 *
 * @param progress 0..100. @param progressReason BFF raw reason (UI 가 localize).
 * @param failed 분리 실패(네트워크/서버/크레딧). @param insufficientCredits 실패 사유가 크레딧 부족인지.
 */
data class PreparingSummary(
    val projectId: String,
    val title: String?,
    val createdAt: Long,
    val thumbnailPath: String?,
    val progress: Int,
    val progressReason: String?,
    val failed: Boolean = false,
    val insufficientCredits: Boolean = false,
)

class InputViewModel constructor(
    private val extractor: VideoMetadataExtractor,
    private val validateVideo: ValidateVideoUseCase,
    private val createProjectWithInitialVideoSegment: CreateProjectWithInitialVideoSegmentUseCase,
    private val editProjectRepository: EditProjectRepository,
    private val segmentRepository: SegmentRepository,
    private val thumbnailExtractor: VideoThumbnailExtractor,
    private val expireOldDrafts: ExpireOldDraftsUseCase,
    private val authRepository: AuthRepository,
    private val startAudioSeparation: StartAudioSeparationUseCase,
    private val pollSeparation: PollSeparationUseCase,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
    private val audioExtractor: AudioExtractor,
    private val bffBaseUrl: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InputUiState())
    val uiState: StateFlow<InputUiState> = _uiState.asStateFlow()

    private val _navigateToTimeline = MutableSharedFlow<String>()
    val navigateToTimeline: SharedFlow<String> = _navigateToTimeline.asSharedFlow()

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    /** projectId → 전체 음원분리 진행 상태(in-memory). "작업 준비중" 섹션의 SSOT. */
    private val _separationProgress = MutableStateFlow<Map<String, SepProgress>>(emptyMap())

    /** projectId → 썸네일 JPEG path. 추출 완료분만 채워짐. drafts/preparing 양쪽 카드가 공유. */
    private val _thumbnails = MutableStateFlow<Map<String, String>>(emptyMap())

    /** 진행 중인 분리 코루틴 — projectId 당 1개(전체영상 분리는 프로젝트당 단일 잡). */
    private val separationJobs = mutableMapOf<String, Job>()

    /** 썸네일 추출 in-flight/완료 projectId — 중복 추출 방지. */
    private val thumbnailRequested = mutableSetOf<String>()

    private data class SepProgress(
        val progress: Int,
        val reason: String?,
        val failed: Boolean = false,
        val insufficientCredits: Boolean = false,
    )

    init {
        // 7일 미접근 drafts cleanup. 실패해도 목록 observe 는 진행.
        viewModelScope.launch { runCatching { expireOldDrafts() } }

        // projects(영속) + 분리 진행(in-memory) + 썸네일을 합쳐 preparing/drafts 로 분리한다.
        // 분리 진행 map 에 있으면 "작업 준비중", 없으면 기존 "이어서 작업"(drafts).
        combine(
            editProjectRepository.observeAllProjects(),
            _separationProgress,
            _thumbnails,
        ) { projects, prog, thumbs -> Triple(projects, prog, thumbs) }
            .onEach { (projects, prog, thumbs) ->
                // 영속된 전체영상 분리 잡 재개 — 앱 재시작/화면 복귀 시 폴링 다시 시작.
                projects.forEach { p ->
                    p.processingSeparations
                        .filter { it.rangeStartMs == null }
                        .forEach { job -> maybeResume(p.projectId, job) }
                }
                val preparing = projects
                    .filter { prog[it.projectId] != null }
                    .sortedByDescending { it.createdAt }
                    .mapNotNull { p ->
                        val jp = prog[p.projectId] ?: return@mapNotNull null
                        PreparingSummary(
                            projectId = p.projectId,
                            title = p.title,
                            createdAt = p.createdAt,
                            thumbnailPath = thumbs[p.projectId],
                            progress = jp.progress,
                            progressReason = jp.reason,
                            failed = jp.failed,
                            insufficientCredits = jp.insufficientCredits,
                        )
                    }
                val drafts = projects
                    .filter { prog[it.projectId] == null }
                    .map { it.toDraftSummary(thumbs[it.projectId]) }
                _uiState.update { it.copy(preparing = preparing, drafts = drafts) }
                ensureThumbnails(projects)
            }
            .launchIn(viewModelScope)
    }

    fun onVideoPicked(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExtracting = true)

            val videoInfo = extractor.extract(uri)
            if (videoInfo == null) {
                _uiState.value = _uiState.value.copy(
                    selectedVideo = null,
                    validationResult = ValidationResult.Invalid(ValidationError.METADATA_UNREADABLE),
                    isExtracting = false
                )
                return@launch
            }

            val result = validateVideo(videoInfo)
            _uiState.value = _uiState.value.copy(
                selectedVideo = videoInfo,
                validationResult = result,
                isExtracting = false
            )
            if (result is ValidationResult.Valid) {
                onContinue()
            }
        }
    }

    fun onResetSelection() {
        _uiState.value = _uiState.value.copy(
            selectedVideo = null,
            validationResult = null,
            isExtracting = false,
        )
    }

    fun onContinue() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        viewModelScope.launch {
            val projectId = createProjectWithInitialVideoSegment(videoInfo = video)
            if (audioExtractor.isSupported) {
                // 분리 지원 플랫폼(iOS): 에디터로 가지 않고 메인에 머무름. 전체영상 분리를 백그라운드로
                // 시작하고 "작업 준비중" 카드로 진행률을 보여준다. 완료되면 카드가 drafts 로 내려간다.
                startWholeVideo(projectId)
                onResetSelection()
            } else {
                // 분리 미지원 플랫폼(Android 현행): 기존 흐름 유지 — 곧장 Timeline 진입.
                _navigateToTimeline.emit(projectId)
            }
        }
    }

    /** "이어서 작업" 카드 클릭 — 해당 projectId 의 timeline 으로 진입. */
    fun onContinueDraft(projectId: String) {
        viewModelScope.launch {
            _navigateToTimeline.emit(projectId)
        }
    }

    /** 준비중 카드(실패) "다시 시도" — 같은 프로젝트의 전체영상 분리를 재시작. */
    fun onRetryPreparing(projectId: String) {
        startWholeVideo(projectId)
    }

    /** 카드 X 버튼 / long-press 삭제. 자식 row 들은 deleteProject 가 cascade. */
    fun onDeleteDraft(projectId: String) {
        viewModelScope.launch {
            separationJobs.remove(projectId)?.cancel()
            clearProgress(projectId)
            runCatching { editProjectRepository.deleteProject(projectId) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            _navigateToLogin.emit(Unit)
        }
    }

    // ── 전체영상 음원분리 (백그라운드) ──────────────────────────────────────────

    /**
     * 프로젝트의 영상 전체를 음원분리한다. 기존 BFF 분리 경로([startAudioSeparation] = 로컬 오디오
     * 추출 + 업로드 + `/separate`, [pollSeparation] = 폴링) 재사용. 전체영상이므로 trim=null.
     */
    private fun startWholeVideo(projectId: String) {
        if (!audioExtractor.isSupported) return
        if (separationJobs[projectId]?.isActive == true) return
        setProgress(projectId, SepProgress(progress = 0, reason = null))
        val job = viewModelScope.launch {
            val segment = runCatching { segmentRepository.getByProjectId(projectId) }
                .getOrNull()?.firstOrNull()
            if (segment == null) {
                setProgress(projectId, SepProgress(0, null, failed = true))
                return@launch
            }
            val startResult = startAudioSeparation(
                sourceUri = segment.sourceUri,
                sourceKind = AudioSourceKind.VIDEO,
                trimStartMs = null,
                trimEndMs = null,
            )
            val jobId = startResult.getOrElse { err ->
                val insufficient = err is InsufficientCreditsException
                setProgress(projectId, SepProgress(0, null, failed = true, insufficientCredits = insufficient))
                editProjectRepository.getProject(projectId)?.let { p ->
                    editProjectRepository.updateProject(
                        p.copy(separationStatus = AutoJobStatus.FAILED),
                        touchActivity = false,
                    )
                }
                return@launch
            }
            // jobId 받자마자 영속화 — 화면을 떠나거나 앱을 재실행해도 재개(maybeResume) 가능.
            editProjectRepository.getProject(projectId)?.let { p ->
                editProjectRepository.updateProject(
                    p.addProcessingSeparation(
                        PersistedSeparationJob(
                            jobId = jobId,
                            segmentId = segment.id,
                            rangeStartMs = null,
                            rangeEndMs = null,
                            numberOfSpeakers = p.separationNumberOfSpeakers,
                            muteOriginalSegmentAudio = p.separationMuteOriginal,
                        )
                    ).copy(
                        separationJobId = jobId,
                        separationSegmentId = segment.id,
                        separationStatus = AutoJobStatus.RUNNING,
                        separationError = null,
                    ),
                    touchActivity = false,
                )
            }
            pollAndCommit(projectId, jobId, segment)
        }
        separationJobs[projectId] = job
    }

    /** 영속된 잡 재개 — 이미 폴링 중이면 no-op. */
    private fun maybeResume(projectId: String, persisted: PersistedSeparationJob) {
        if (!audioExtractor.isSupported) return
        if (separationJobs[projectId]?.isActive == true) return
        setProgress(projectId, SepProgress(progress = 0, reason = null))
        val job = viewModelScope.launch {
            val segments = runCatching { segmentRepository.getByProjectId(projectId) }
                .getOrNull().orEmpty()
            val segment = segments.firstOrNull { it.id == persisted.segmentId }
                ?: segments.firstOrNull()
            if (segment == null) {
                clearProgress(projectId)
                return@launch
            }
            pollAndCommit(projectId, persisted.jobId, segment)
        }
        separationJobs[projectId] = job
    }

    private suspend fun pollAndCommit(projectId: String, jobId: String, segment: Segment) {
        try {
            pollSeparation(jobId).collect { status ->
                when (status) {
                    is SeparationStatus.Processing ->
                        setProgress(projectId, SepProgress(status.progress, status.progressReason))
                    is SeparationStatus.Ready ->
                        commitWholeVideoDirective(projectId, jobId, segment, status)
                    is SeparationStatus.Failed -> {
                        setProgress(projectId, SepProgress(0, status.progressReason, failed = true))
                        markProjectFailed(projectId, jobId)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setProgress(projectId, SepProgress(0, null, failed = true))
            markProjectFailed(projectId, jobId)
        } finally {
            // collect 종료(완료/실패) 시 잡 핸들 정리 — 같은 프로젝트 재시작 가드가 stale active 로
            // 막히지 않도록.
            if (separationJobs[projectId]?.isActive != true) separationJobs.remove(projectId)
        }
    }

    /**
     * 분리 완료 → SeparationDirective 영속화. 전체영상이라 세그먼트가 1개뿐이고 range 가 영상 전체이므로
     * (TimelineViewModel 의 commitProcessingSeparationToDirective isWholeVideo 분기를 축약) 최소 커밋.
     */
    private suspend fun commitWholeVideoDirective(
        projectId: String,
        jobId: String,
        segment: Segment,
        status: SeparationStatus.Ready,
    ) {
        // stem.url 이 path-only(`/api/v2/...`) 면 절대 URL 로 보정 — iOS AVAudioPlayer 가 host 없는
        // URL 을 silent fail.
        val baseTrim = bffBaseUrl.trimEnd('/')
        val absStems = status.stems.map { stem ->
            if (stem.url.startsWith("http")) stem
            else stem.copy(url = "$baseTrim/${stem.url.trimStart('/')}")
        }
        // 모든 stem default 선택. 단 VOICE_ALL("모든 화자") 은 화자별 SPEAKER stem 과 중복이라 비선택.
        val selections = absStems.map { stem ->
            StemSelection(
                stemId = stem.stemId,
                volume = 1.0f,
                audioUrl = stem.url,
                selected = stem.stemId != Stem.STEM_ID_VOICE_ALL,
            )
        }
        if (selections.none { it.selected }) {
            setProgress(projectId, SepProgress(0, null, failed = true))
            markProjectFailed(projectId, jobId)
            return
        }
        val project = editProjectRepository.getProject(projectId)
        val end = status.actualDurationMs?.takeIf { it > 0L } ?: segment.durationMs.coerceAtLeast(1L)
        separationDirectiveRepository.add(
            SeparationDirective(
                id = generateId(),
                projectId = projectId,
                rangeStartMs = 0L,
                rangeEndMs = end,
                numberOfSpeakers = project?.separationNumberOfSpeakers ?: 2,
                muteOriginalSegmentAudio = true,
                selections = selections,
                createdAt = currentTimeMillis(),
                jobId = jobId,
                segmentId = segment.id,
                localStartMs = 0L,
                localEndMs = end,
            )
        )
        project?.let {
            editProjectRepository.updateProject(it.removeProcessingSeparation(jobId).clearSeparation())
        }
        // progress map 에서 제거 → 카드가 "작업 준비중"에서 사라지고 drafts 로 내려간다.
        clearProgress(projectId)
        separationJobs.remove(projectId)
    }

    private suspend fun markProjectFailed(projectId: String, jobId: String) {
        editProjectRepository.getProject(projectId)?.let { p ->
            editProjectRepository.updateProject(
                p.removeProcessingSeparation(jobId).copy(separationStatus = AutoJobStatus.FAILED),
                touchActivity = false,
            )
        }
    }

    private fun setProgress(projectId: String, value: SepProgress) {
        _separationProgress.update { it + (projectId to value) }
    }

    private fun clearProgress(projectId: String) {
        _separationProgress.update { it - projectId }
    }

    // ── 썸네일 ──────────────────────────────────────────────────────────────

    /** 아직 추출 안 한 프로젝트의 썸네일을 비동기 추출해 [_thumbnails] 에 채운다. */
    private fun ensureThumbnails(projects: List<EditProject>) {
        projects.forEach { project ->
            if (!thumbnailRequested.add(project.projectId)) return@forEach
            viewModelScope.launch {
                val firstUri = runCatching {
                    segmentRepository.getFirstSourceUri(project.projectId)
                }.getOrNull() ?: return@launch
                val thumbPath = runCatching {
                    thumbnailExtractor.extractThumbnail(firstUri)
                }.getOrNull() ?: return@launch
                _thumbnails.update { it + (project.projectId to thumbPath) }
            }
        }
    }

    private fun EditProject.toDraftSummary(thumbnailPath: String?): DraftSummary {
        var running = 0
        if (processingSeparations.isNotEmpty()) {
            running += processingSeparations.size
        } else if (separationStatus == AutoJobStatus.RUNNING) {
            running++
        }
        return DraftSummary(
            projectId = projectId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            jobsRunningSummary = when {
                running <= 0 -> null
                running == 1 -> "1 job in progress"
                else -> "$running jobs in progress"
            },
            thumbnailPath = thumbnailPath,
        )
    }
}
