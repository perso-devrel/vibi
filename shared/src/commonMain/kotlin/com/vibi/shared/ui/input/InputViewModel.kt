package com.vibi.shared.ui.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.ValidationError
import com.vibi.shared.domain.model.ValidationResult
import com.vibi.shared.domain.model.VideoInfo
import com.vibi.shared.data.repository.AuthRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.platform.VideoThumbnailExtractor
import com.vibi.shared.domain.usecase.draft.ExpireOldDraftsUseCase
import com.vibi.shared.domain.usecase.input.CreateProjectWithInitialVideoSegmentUseCase
import com.vibi.shared.domain.usecase.input.ValidateVideoUseCase
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class InputUiState(
    val selectedVideo: VideoInfo? = null,
    val validationResult: ValidationResult? = null,
    val isExtracting: Boolean = false,
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

class InputViewModel constructor(
    private val extractor: VideoMetadataExtractor,
    private val validateVideo: ValidateVideoUseCase,
    private val createProjectWithInitialVideoSegment: CreateProjectWithInitialVideoSegmentUseCase,
    private val editProjectRepository: EditProjectRepository,
    private val segmentRepository: SegmentRepository,
    private val thumbnailExtractor: VideoThumbnailExtractor,
    private val expireOldDrafts: ExpireOldDraftsUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InputUiState())
    val uiState: StateFlow<InputUiState> = _uiState.asStateFlow()

    private val _navigateToTimeline = MutableSharedFlow<String>()
    val navigateToTimeline: SharedFlow<String> = _navigateToTimeline.asSharedFlow()

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin: SharedFlow<Unit> = _navigateToLogin.asSharedFlow()

    /** drafts 썸네일 추출 job — observeAllProjects 새 emission 마다 교체(이전 추출 취소). */
    private var thumbnailJob: Job? = null

    init {
        // 7일 미접근 drafts cleanup. 실패해도 drafts observe 는 진행.
        viewModelScope.launch { runCatching { expireOldDrafts() } }
        editProjectRepository.observeAllProjects()
            .onEach { projects ->
                // 플레이스홀더(썸네일 null)는 즉시 반영 — 삭제 등 DB 변경이 곧장 목록에 보이게.
                _uiState.update { it.copy(drafts = projects.map { p -> p.toDraftSummary(thumbnailPath = null) }) }
                // 썸네일 추출은 취소 가능 job 으로 분리. 이전엔 coroutineScope 로 onEach 를 블록해
                // 추출이 끝날 때까지 다음 emission(삭제 등) 반영이 지연됐다. 새 emission 이 오면
                // 진행 중 추출을 취소(flatMapLatest 의미)해 stale 작업 누적과 반영 지연을 함께 해소.
                thumbnailJob?.cancel()
                thumbnailJob = viewModelScope.launch {
                    projects.forEach { project ->
                        launch {
                            val firstUri = runCatching {
                                segmentRepository.getFirstSourceUri(project.projectId)
                            }.getOrNull() ?: return@launch
                            val thumbPath = runCatching {
                                thumbnailExtractor.extractThumbnail(firstUri)
                            }.getOrNull() ?: return@launch
                            _uiState.update { s ->
                                s.copy(drafts = s.drafts.map { d ->
                                    if (d.projectId == project.projectId)
                                        project.toDraftSummary(thumbPath) else d
                                })
                            }
                        }
                    }
                }
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
            _navigateToTimeline.emit(projectId)
        }
    }

    /** "이어서 작업" 카드 클릭 — 해당 projectId 의 timeline 으로 진입. */
    fun onContinueDraft(projectId: String) {
        viewModelScope.launch {
            _navigateToTimeline.emit(projectId)
        }
    }

    /** 카드 X 버튼 / long-press 삭제. 자식 row 들은 deleteProject 가 cascade. */
    fun onDeleteDraft(projectId: String) {
        viewModelScope.launch {
            runCatching { editProjectRepository.deleteProject(projectId) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            _navigateToLogin.emit(Unit)
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
