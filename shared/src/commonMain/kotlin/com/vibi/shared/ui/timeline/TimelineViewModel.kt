@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.vibi.shared.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.platform.currentTimeMillis
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.uuid.Uuid
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.StemKind
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.DirectiveAnchor
import com.vibi.shared.domain.model.cloneForSegment
import com.vibi.shared.domain.model.isContiguousMergeableRun
import com.vibi.shared.domain.model.isContiguousMergeableWith
import com.vibi.shared.domain.model.PersistedSeparationJob
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.addProcessingSeparation
import com.vibi.shared.domain.model.removeProcessingSeparation
import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.platform.generateId
import com.vibi.shared.domain.model.clearSeparation
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.repository.SeparationStatus
import com.vibi.shared.domain.repository.StemSelection
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.AudioSourceKind
import com.vibi.shared.domain.usecase.bgm.AddBgmClipUseCase
import com.vibi.shared.domain.usecase.bgm.UpdateBgmClipUseCase
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.domain.usecase.save.ExportVariant
import com.vibi.shared.domain.usecase.save.PrewarmAssetUploadUseCase
import com.vibi.shared.domain.usecase.save.SaveAllVariantsUseCase
import com.vibi.shared.domain.usecase.separation.PollSeparationUseCase
import com.vibi.shared.domain.usecase.separation.StartAudioSeparationUseCase
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import com.vibi.shared.domain.util.UndoRedoManager
import com.vibi.shared.domain.usecase.timeline.AddVideoSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.DuplicateSegmentRangeUseCase
import com.vibi.shared.domain.usecase.timeline.MoveSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.MergeSegmentsUseCase
import com.vibi.shared.domain.usecase.timeline.RemoveSegmentRangeUseCase
import com.vibi.shared.domain.usecase.timeline.RemoveSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.SplitSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.UpdateSegmentSpeedUseCase
import com.vibi.shared.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.vibi.shared.domain.usecase.timeline.UpdateSegmentVolumeUseCase
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.russhwolf.settings.Settings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Timeline "저장" 흐름 상태. headerbar 의 저장 버튼 라벨/snackbar 에 사용.
 *  - IDLE: 저장 시작 전.
 *  - RUNNING(progress): 0..100 평균 진행률.
 *  - DONE: 모든 variant 갤러리 저장 완료.
 *  - FAILED(message): 어느 단계에서든 실패.
 */
sealed interface SaveStatus {
    data object IDLE : SaveStatus
    data class RUNNING(val progress: Int) : SaveStatus
    data object DONE : SaveStatus
    data class FAILED(val message: String) : SaveStatus
}

/**
 * 공유 흐름 상태 — Save 와 분리. 공유는 갤러리 저장 X, 프로젝트 보존, navigate 안 함.
 */
sealed interface ShareStatus {
    data object IDLE : ShareStatus
    data class RUNNING(val progress: Int) : ShareStatus
    data object DONE : ShareStatus
    data class FAILED(val message: String) : ShareStatus
}


/**
 * 백그라운드 폴링 중인 음원분리 1건. 동시에 여러 구간을 분리할 수 있도록
 * [TimelineUiState.processingSeparations] 리스트로 보관한다.
 *
 * - [clientToken]: jobId 가 BFF 응답 전까지는 null 이므로, 시작 시점 식별자로 별도 token 부여.
 * - [jobId]: BFF 응답 후 채워짐. 폴링 / EditProject 영속화 / "다시 시도" 분기에 사용.
 * - [rangeStartMs] / [rangeEndMs]: timeline 상 점유 범위 — free-interval 계산·중복 방지·overlay 렌더.
 *
 * sheet UI 의 stem toggle / volume slider 는 PICK_STEMS 단계에서만 활성이므로 entry 가 Ready 가
 * 되면 곧바로 directive 로 commit 후 리스트에서 제거된다. PICK_STEMS UI 가 필요한 케이스(직접 편집)
 * 는 [TimelineUiState.audioSeparation] (단일) 가 담당.
 */
data class ProcessingSeparation(
    val clientToken: String,
    val jobId: String? = null,
    val segmentId: String,
    val rangeStartMs: Long?,
    val rangeEndMs: Long?,
    val numberOfSpeakers: Int,
    val muteOriginalSegmentAudio: Boolean,
    val progress: Int = 0,
    val progressReason: String? = null,
    /** 사용자가 직접 stem 편집 모드로 진입한 경우 기존 directive id — 새 commit 시 같은 id 로 upsert. */
    val editingDirectiveId: String? = null,
)

/**
 * 영상 다듬기 모드에서 액션(복제/삭제/볼륨/속도) 이 적용될 트랙.
 *
 * 사용자가 같은 구간을 선택한 채 어떤 트랙(영상 자체 / BGM / 분리된 stem) 에 액션을 적용할지
 * 선택. multi-select 가능 — 모두 선택 시 한번에 동일 액션이 각 트랙에 적용.
 *
 * - [Video] : 영상 segment 자체. split/delete/volume/speed 4종 액션 적용 가능.
 * - [Bgm]   : 그 구간에 시간상 걸친 BGM 클립들. clipId null = 전체, 명시 시 그 한 클립만.
 * - [Stem]  : 그 구간 안 분리 directive 의 같은 stemId stem. 의미상 볼륨/음소거만 활성 —
 *             stem 의 복제/삭제는 SoundCard 토글로 분리 노출하므로 본 모드에서 no-op.
 */
sealed class EditTarget {
    object Video : EditTarget()
    data class Bgm(val clipId: String? = null) : EditTarget()
    data class Stem(val stemId: String) : EditTarget()
}

fun Set<EditTarget>.hasVideo(): Boolean = any { it is EditTarget.Video }
fun Set<EditTarget>.hasBgm(): Boolean = any { it is EditTarget.Bgm }

/**
 * 미리듣기 모드 — Sound Deck UI 의 A/B 비교용.
 *  - [MIX]   : 현재 사용자가 만든 mix (분리 stem volume 적용 + directive range 내 video mute).
 *  - [ORIGINAL]: directive 와 무관하게 영상 원본 audio 만 들림 (분리 stem 전부 mute, video 항상 unmute).
 * 분리된 directive 가 하나도 없으면 사실상 의미 없음 — UI 에서 바 자체 hidden.
 */
enum class PreviewMode { MIX, ORIGINAL }

/**
 * BGM "배경음 제거" 작업의 진행 단계. 캐시(voiceOnlyUri) 가 채워진 이후 토글은 즉시(state 변경 없이)
 * 라 본 enum 은 첫 분리 작업 진행 중 / 실패 만 다룬다.
 */
sealed interface BgmRemovalProgress {
    /** 분리 작업 진행 중 — UI 는 버튼 비활성 + 로딩 표시. */
    data object Processing : BgmRemovalProgress
    /** 마지막 시도 실패 — 사용자가 다시 누르면 새 분리 시도. */
    data class Failed(val message: String) : BgmRemovalProgress
}

data class TimelineUiState(
    val projectId: String = "",
    /** 사용자가 지정한 프로젝트 제목. null/blank 이면 헤더가 "Untitled" 표시. 에디터 헤더 제목 탭으로 편집. */
    val projectTitle: String? = null,
    val segments: List<Segment> = emptyList(),
    val selectedSegmentId: String? = null,
    val videoUri: String = "",
    val videoDurationMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val showAppendSheet: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    /**
     * directive range 안에서 stem 재생 중일 때 video player 의 원본 audio 만 mute 하는 ephemeral 플래그.
     */
    val runtimeVideoMutedForDirective: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isVideoSelected: Boolean = false,
    val videoVolume: Float = 1.0f,
    val showVideoVolumeSlider: Boolean = false,
    val isTrimming: Boolean = false,
    val pendingTrimStartMs: Long = 0L,
    val pendingTrimEndMs: Long = 0L,
    val isRangeSelecting: Boolean = false,
    val rangeTargetSegmentId: String? = null,
    val pendingRangeStartMs: Long = 0L,
    val pendingRangeEndMs: Long = 0L,
    val showRangeActionSheet: Boolean = false,
    val pendingRangeVolume: Float = 1.0f,
    val pendingRangeSpeed: Float = 1.0f,
    val isSegmentEditMode: Boolean = false,
    val editTargets: Set<EditTarget> = setOf(EditTarget.Video),
    val bgmClips: List<BgmClip> = emptyList(),
    val selectedBgmClipId: String? = null,
    val isAddingBgm: Boolean = false,
    val bgmError: String? = null,
    val bgmTrimRequest: BgmTrimRequest? = null,
    val bgmLaneCount: Int = 3,
    /**
     * BGM "배경음 제거" 작업 진행 상태 (clipId → 상태). 캐시(BgmClip.voiceOnlyUri) 가 채워지기
     * 전까지의 ephemeral 진행만 추적 — 캐시 채워진 뒤엔 본 맵에서 제거되고 UI 가 BgmClip 자체의
     * sourceUri / voiceOnlyUri 비교로 토글 라벨 결정. Processing 동안 같은 클립 재요청은 무시.
     */
    val bgmBackgroundRemovalProgress: Map<String, BgmRemovalProgress> = emptyMap(),
    /**
     * BGM "배경음 제거" 첫 분리 비용 confirmation. null = prompt 없음 (이미 분리 캐시 있는 토글은
     * prompt 안 띄움). 사용자가 Confirm 누르면 실제 separation job 시작 + 본 필드 null 화.
     */
    val bgmRemovalCostPrompt: BgmRemovalCostPrompt? = null,
    val audioSeparation: AudioSeparationUiState? = null,
    val showAudioSeparationSheet: Boolean = false,
    val processingSeparations: List<ProcessingSeparation> = emptyList(),
    val separationDirectives: List<SeparationDirective> = emptyList(),
    val separationStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val showExportOptionsSheet: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val shareStatus: ShareStatus = ShareStatus.IDLE,
    /** Sound Deck A/B 미리듣기 모드. */
    val previewMode: PreviewMode = PreviewMode.MIX,
) {
    val effectiveTrimEndMs: Long get() = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
}

data class TimelineSnapshot(
    val segments: List<Segment>,
    val bgmClips: List<BgmClip> = emptyList(),
    val separationDirectives: List<SeparationDirective> = emptyList(),
)

class TimelineViewModel constructor(
    private val projectId: String,
    private val segmentRepository: SegmentRepository,
    private val editProjectRepository: EditProjectRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val updateSegmentTrim: UpdateSegmentTrimUseCase,
    private val addVideoSegment: AddVideoSegmentUseCase,
    private val removeSegment: RemoveSegmentUseCase,
    private val splitSegment: SplitSegmentUseCase,
    private val duplicateSegmentRange: DuplicateSegmentRangeUseCase,
    private val moveSegment: MoveSegmentUseCase,
    private val mergeSegments: MergeSegmentsUseCase,
    private val removeSegmentRange: RemoveSegmentRangeUseCase,
    private val updateSegmentVolume: UpdateSegmentVolumeUseCase,
    private val updateSegmentSpeed: UpdateSegmentSpeedUseCase,
    private val addBgmClip: AddBgmClipUseCase,
    private val updateBgmClip: UpdateBgmClipUseCase,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    private val audioMetadataExtractor: AudioMetadataExtractor,
    private val startAudioSeparation: StartAudioSeparationUseCase,
    private val pollSeparation: PollSeparationUseCase,
    private val audioExtractor: AudioExtractor,
    private val audioSeparationRepository: AudioSeparationRepository,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
    private val bffBaseUrl: String,
    private val saveAllVariants: SaveAllVariantsUseCase,
    private val shareSheetLauncher: ShareSheetLauncher,
    private val prewarmAssetUpload: PrewarmAssetUploadUseCase,
    /** 멀티플랫폼 영속 설정 — 현재는 "음원분리 취소 경고 다시 보지 않기" 플래그에만 사용. */
    private val settings: Settings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState(projectId = projectId))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    /**
     * 저장 완료 후 InputScreen 으로 돌아가는 1회성 신호. UI 가 collect 해 nav stack pop.
     * SaveStatus.DONE 만으로 navigation 트리거하면 재진입 시 즉시 또 pop 되는 사고가 나므로 분리.
     */
    init {
        // local.properties 의 BFF_BASE_URL 누락 시 stem URL 이 relative 가 돼 iOS AVPlayer
        // 가 silent fail — fail-fast 로 설정 누락을 명시.
        require(bffBaseUrl.isNotBlank()) {
            "bffBaseUrl 속성이 비어있음 — local.properties 의 BFF_BASE_URL 또는 KoinHelper.initKoin 의 property 누락"
        }
    }

    /** UI 가 분리 진입 entry button hide / disable 판단에 사용. iOS=true, Android=false (Android
     *  은 iOS 우선 출시 정책). ViewModel + Repository 진입부 가드와 함께 3단 방어 중 첫 번째 단. */
    val isSeparationSupported: Boolean = audioExtractor.isSupported

    private val _navigateBackHome = MutableSharedFlow<Unit>()
    val navigateBackHome: SharedFlow<Unit> = _navigateBackHome.asSharedFlow()

    /**
     * 잔액 부족으로 분리 시작이 막혔을 때 사용자가 "충전하기" 를 누르면 emit. UI 가 collect 해
     * UserMenu/CreditPurchaseSheet 로 navigate. SharedFlow 라 화면 회전 등으로 collect 가 끊겨도
     * 다음 진입에서 다시 받지 않음 (1회성 명령).
     */
    private val _navigateToBuyCredits = MutableSharedFlow<Unit>()
    val navigateToBuyCredits: SharedFlow<Unit> = _navigateToBuyCredits.asSharedFlow()

    /**
     * 메인 timeline undo 스택 — 모든 편집(영상 segment / BGM / 분리 directive / frame /
     * text overlay) 이 같은 스택을 공유.
     *
     * 영상편집 commit / 음원분리 commit 같은 비가역 checkpoint 후에는 [UndoRedoManager.clear] +
     * 새 baseline push 로 스택을 끊어 사용자가 commit 이전 상태로 되돌리지 않도록 막는다.
     */
    private val mainUndoManager: UndoRedoManager<TimelineSnapshot> = UndoRedoManager(maxHistory = 50)

    private var hasSeededUndoSnapshot = false

    // 음원분리 자동 재개·refresh 가드 — project 가 다시 emit 되어도 한 번만 fire.
    private enum class TriggerGate { ARMED, FIRED }
    private var separationGate = TriggerGate.ARMED
    private var separationRefreshGate = TriggerGate.ARMED

    // R2 선업로드(prewarm) 추적 — 이미 선업로드 trigger 한 원본 URI 집합. segment/BGM flow 가
    // 재emit 돼도 같은 URI 는 재trigger 안 하고, 진입 후 "새로 추가된" 영상/BGM 소스만 선업로드한다
    // (1회성 게이트는 진입 후 append 된 소스를 영영 놓쳤음). ensureUploaded 가 멱등이라 정확성엔 무관,
    // 불필요한 코루틴 spawn 만 막는 가드.
    private val prewarmedSourceUris = mutableSetOf<String>()

    init {
        loadSegments()
        observeProject()
        observeBgmClips()
        observeSeparationDirectives()
    }

    /**
     * BGM clip 의 visual lane (위/아래 행) override — DB 영속화 없는 in-memory map.
     * 사용자가 vertical drag 으로 lane 을 바꾸면 갱신되고, repository flow 가 새 list 를
     * emit 할 때마다 이 map 을 적용해 lane 을 복원한다. clip id 가 사라지면 자동 정리.
     */
    private val bgmClipLaneOverrides = mutableMapOf<String, Int>()

    private fun applyBgmLaneOverrides(clips: List<BgmClip>): List<BgmClip> {
        if (bgmClipLaneOverrides.isEmpty()) return clips
        // GC: clip 이 사라졌으면 override 도 제거.
        val liveIds = clips.mapTo(HashSet(clips.size)) { it.id }
        bgmClipLaneOverrides.keys.retainAll(liveIds)
        return clips.map { c ->
            val lane = bgmClipLaneOverrides[c.id]
            if (lane != null && lane != c.lane) c.copy(lane = lane) else c
        }
    }

    private fun observeBgmClips() {
        viewModelScope.launch {
            bgmClipRepository.observeClips(projectId).collect { clips ->
                val applied = applyBgmLaneOverrides(clips)
                maybePrewarmBgmUpload(applied)
                // 사용자가 lane N 에 clip 둔 채 종료 → 재진입 시 default 3 으로 lane N 이 영역 밖 시각
                // 깜빡임 방지. 현재 lane 수가 점유보다 작으면 그만큼 자동 확장.
                val maxOccupiedLane = applied.maxOfOrNull { it.lane } ?: -1
                val minLaneCount = (maxOccupiedLane + 1).coerceAtLeast(3).coerceAtMost(8)
                // _uiState.update — concurrent observers 간 race 방지 (atomic CAS).
                // value = value.copy(...) 패턴은 다른 observer 가 동시에 write 시 일부 필드 (e.g.
                // videoUri, segments) 가 stale 한 옛 값으로 덮어쓰일 수 있음.
                _uiState.update { current ->
                    current.copy(
                        bgmClips = applied,
                        bgmLaneCount = current.bgmLaneCount.coerceAtLeast(minLaneCount),
                    )
                }
            }
        }
    }

    /**
     * 사용자가 BGM clip 을 vertical drag 으로 다른 lane (위·아래 행) 으로 옮길 때 호출.
     * 음수 입력은 0 으로 clamp. 현재는 in-memory only — DB 영속화는 별도 마이그레이션
     * 단계에서 추가. 화면 재진입 시 lane 은 0 으로 리셋된다.
     */
    fun onUpdateBgmLane(clipId: String, newLane: Int) {
        // atomic CAS — observeBgmClips emit 와 동시 시점에 lane override 만 박는 race 차단.
        // bgmClipLaneOverrides 는 in-memory override 라 update 람다 안에서 max lane 재계산.
        _uiState.update { current ->
            val maxLane = (current.bgmLaneCount - 1).coerceAtLeast(0)
            val lane = newLane.coerceIn(0, maxLane)
            bgmClipLaneOverrides[clipId] = lane
            val updated = current.bgmClips.map { c ->
                if (c.id == clipId && c.lane != lane) c.copy(lane = lane) else c
            }
            current.copy(bgmClips = updated)
        }
    }

    /** BGM 영역 lane 개수 직접 set. drag handle 이 부드럽게 여러 step 한 번에 적용. */
    fun onSetBgmLaneCount(count: Int) {
        _uiState.update { current ->
            val nextCount = count.coerceIn(1, 8)
            // 마지막 lane 들 안에 clip 이 있으면 그 lane 까지는 유지 (축소 보류).
            val maxOccupiedLane = current.bgmClips.maxOfOrNull { it.lane } ?: -1
            val safeCount = nextCount.coerceAtLeast(maxOccupiedLane + 1)
            if (safeCount == current.bgmLaneCount) current
            else current.copy(bgmLaneCount = safeCount)
        }
    }


    private fun observeSeparationDirectives() {
        viewModelScope.launch {
            separationDirectiveRepository.observe(projectId).collect { directives ->
                // atomic CAS — 다른 observer / mutation 핸들러와 race 시 stale state read-modify-write
                // 방지 (loadSegments / observeBgmClips / observeProject / observeClips 와 동일 패턴).
                _uiState.update { it.copy(separationDirectives = directives) }
            }
        }
    }

    private fun observeProject() {
        viewModelScope.launch {
            editProjectRepository.observeProject(projectId).collect { project ->
                if (project != null) {
                    _uiState.update { current ->
                        current.copy(
                            projectTitle = project.title,
                            separationStatus = project.separationStatus,
                        )
                    }
                    if (!hasSeededUndoSnapshot) {
                        hasSeededUndoSnapshot = true
                        pushUndoState()
                    }
                    maybeTriggerAutoPipelines(project)
                }
            }
        }
    }

    private fun maybeTriggerAutoPipelines(project: EditProject) {
        if (shouldResumeSeparation(project)) {
            separationGate = TriggerGate.FIRED
            resumeSeparationPolling(project)
        }
        if (shouldRefreshSeparation(project)) {
            separationRefreshGate = TriggerGate.FIRED
            refreshSeparationFreshness(project)
        }
    }

    private fun shouldResumeSeparation(project: EditProject): Boolean =
        separationGate == TriggerGate.ARMED && (
            project.processingSeparations.isNotEmpty() ||
                (project.separationStatus == AutoJobStatus.RUNNING && !project.separationJobId.isNullOrBlank())
            )

    private fun shouldRefreshSeparation(project: EditProject): Boolean =
        separationRefreshGate == TriggerGate.ARMED &&
            project.separationStatus == AutoJobStatus.READY &&
            !project.separationJobId.isNullOrBlank()

    /**
     * 화면 재진입 또는 앱 재실행 시 영속화된 잡들로 폴링 재개. EditProject.processingSeparations 리스트의
     * 모든 entry 를 in-memory 로 복원하고 각각 독립 폴링 launch. legacy 단일 슬롯
     * (separationJobId, etc.) 는 리스트가 비어 있을 때만 fallback 으로 사용 (구 데이터 호환).
     * sheet 는 hidden — 사용자가 timeline overlay 와 버튼으로 진행 상태 인지.
     */
    private fun resumeSeparationPolling(project: EditProject) {
        project.processingSeparations.forEach { startResumePoll(it) }
        // legacy 단일 슬롯 fallback — DB v2 데이터 호환 path.
        if (project.processingSeparations.isEmpty()) {
            val jobId = project.separationJobId ?: return
            val segmentId = project.separationSegmentId ?: return
            startResumePoll(
                PersistedSeparationJob(
                    jobId = jobId,
                    segmentId = segmentId,
                    rangeStartMs = null,
                    rangeEndMs = null,
                    numberOfSpeakers = project.separationNumberOfSpeakers,
                    muteOriginalSegmentAudio = project.separationMuteOriginal,
                )
            )
        }
    }

    private fun startResumePoll(persisted: PersistedSeparationJob) {
        if (_uiState.value.processingSeparations.any { it.jobId == persisted.jobId }) return
        val clientToken = Uuid.random().toString()
        addProcessingSeparationEntry(
            ProcessingSeparation(
                clientToken = clientToken,
                jobId = persisted.jobId,
                segmentId = persisted.segmentId,
                rangeStartMs = persisted.rangeStartMs,
                rangeEndMs = persisted.rangeEndMs,
                numberOfSpeakers = persisted.numberOfSpeakers,
                muteOriginalSegmentAudio = persisted.muteOriginalSegmentAudio,
            )
        )
        // clientToken 은 방금 생성한 새 UUID 라 map 에 없음 → ?.cancel() 은 항상 no-op. 중복 방지는
        // 위 jobId 가드(477)가 담당.
        separationJobs[clientToken] = viewModelScope.launch {
            pollSeparationFlow(clientToken, persisted.jobId)
        }
    }

    /**
     * READY 결과 재진입 시 한 번 검증해서 stale token 갱신 또는 정리. 4xx 이외 실패는 일시적일 수 있어
     * gate 만 되돌리고 다음 진입에 재시도 — 사용자가 영구 오프라인이거나 BFF 가 잠시 5xx 일 때 멀쩡한
     * directive 가 사라지는 사고 방지.
     */
    private fun refreshSeparationFreshness(project: EditProject) {
        val jobId = project.separationJobId ?: return
        viewModelScope.launch {
            val result = audioSeparationRepository.pollStatus(jobId)
            when (val status = result.getOrNull()) {
                is SeparationStatus.Ready -> {
                    val freshUrlByStemId = status.stems.associate { it.stemId to it.url }
                    separationDirectiveRepository.getByProject(projectId).forEach { dir ->
                        val updated = dir.selections.map { sel ->
                            val fresh = freshUrlByStemId[sel.stemId]
                            if (fresh != null && fresh != sel.audioUrl) sel.copy(audioUrl = fresh) else sel
                        }
                        if (updated != dir.selections) {
                            separationDirectiveRepository.add(dir.copy(selections = updated))
                        }
                    }
                }
                is SeparationStatus.Failed -> clearStaleSeparation()
                is SeparationStatus.Processing -> Unit
                null -> {
                    val httpStatus = (result.exceptionOrNull() as? ClientRequestException)
                        ?.response?.status?.value
                    if (httpStatus == HTTP_NOT_FOUND) clearStaleSeparation()
                    else separationRefreshGate = TriggerGate.ARMED
                }
            }
        }
    }

    private suspend fun clearStaleSeparation() {
        separationDirectiveRepository.deleteByProject(projectId)
        editProjectRepository.getProject(projectId)?.let {
            editProjectRepository.updateProject(it.clearSeparation())
        }
    }

    // 자막/더빙 trigger 제거 — separation 만 유지.

    private fun loadSegments() {
        viewModelScope.launch {
            segmentRepository.observeByProjectId(projectId).collect { segments ->
                val first = segments.firstOrNull()
                val total = segments.sumOf { it.effectiveDurationMs }
                maybePrewarmAssetUpload(segments)
                // _uiState.update {} (atomic CAS) — 같은 mutation 시 splitSegment 와 updateSegmentVolume
                // 두 transaction commit 이 Room invalidation 으로 별개 emit 을 만든다. 사이에 사용자가
                // 다른 segment 를 탭해 click 핸들러의 `_uiState.value = state.copy(...)` 가 stale
                // segments 를 read-modify-write 로 다시 박아 넣으면, 의도한 NEW2 가 NEW1 (split only,
                // volume 미반영) 로 영구 되돌려진 채 굳는 race (영상편집 + 볼륨 Apply 후 다른 곳 클릭하면
                // 파형이 원복되던 버그). atomic CAS 로 다른 launch 의 비-atomic write 와 인터리브 시에도
                // 같은 transaction 결과로 수렴하게.
                _uiState.update { current ->
                    val selectedId = current.selectedSegmentId
                        ?.takeIf { id -> segments.any { it.id == id } }
                    val selected = segments.firstOrNull { it.id == selectedId }
                    val (globalTrimStart, globalTrimEnd) = selectedSegmentGlobalTrim(segments, selected)
                    current.copy(
                        segments = segments,
                        selectedSegmentId = selectedId,
                        videoUri = first?.sourceUri.orEmpty(),
                        videoDurationMs = total,
                        videoWidth = first?.width ?: 0,
                        videoHeight = first?.height ?: 0,
                        trimStartMs = globalTrimStart,
                        trimEndMs = globalTrimEnd,
                    )
                }
            }
        }
    }

    /**
     * 영상 원본을 R2 에 미리 올려 저장 시점의 업로드 대기를 없앤다. segment flow 는 매 편집마다 재emit
     * 되므로 [prewarmedSourceUris] 로 이미 선업로드한 URI 는 건너뛰고, 진입 후 새로 append 된 소스만
     * trigger 한다. 업로드는 best-effort 라 실패해도 저장 경로가 평소대로 처리하므로 회귀가 없다.
     * segment flow 처리를 막지 않도록 별도 launch.
     */
    private fun maybePrewarmAssetUpload(segments: List<Segment>) {
        val newPaths = segments
            .filter { it.type == SegmentType.VIDEO }
            .map { it.sourceUri }
            .filter { it.isNotBlank() && prewarmedSourceUris.add(it) }
        if (newPaths.isEmpty()) return
        viewModelScope.launch { prewarmAssetUpload.prewarmVideos(newPaths) }
    }

    /**
     * BGM 원본을 R2 에 미리 올려 저장 시점의 업로드 대기를 없앤다. [maybePrewarmAssetUpload] 와 동일
     * 패턴 — BGM 은 보통 편집 중간에 추가되므로(진입 시점엔 없음) 소스별 추적이 필수. 원격 URL BGM 은
     * 로컬 stat 불가라 prewarm 이 best-effort skip 되고 저장 시점에 평소대로 처리된다.
     */
    private fun maybePrewarmBgmUpload(clips: List<BgmClip>) {
        val newPaths = clips
            .map { it.sourceUri }
            .filter { it.isNotBlank() && prewarmedSourceUris.add(it) }
        if (newPaths.isEmpty()) return
        viewModelScope.launch { prewarmAssetUpload.prewarmAudio(newPaths) }
    }

    private fun selectedSegmentGlobalTrim(
        segments: List<Segment>,
        selected: Segment?
    ): Pair<Long, Long> {
        if (selected == null || selected.type != SegmentType.VIDEO) return 0L to 0L
        val segStart = segmentStartOffsetMs(segments, selected.id)
        val trimStart = selected.trimStartMs
        val trimEnd = if (selected.trimEndMs <= 0L) selected.durationMs else selected.trimEndMs
        val hasTrim = trimStart > 0L || trimEnd < selected.durationMs
        return if (hasTrim) {
            (segStart + trimStart) to (segStart + trimEnd)
        } else {
            0L to 0L
        }
    }

    private fun segmentStartOffsetMs(segments: List<Segment>, segmentId: String): Long =
        DirectiveAnchor.segmentStartOffsetMs(segments, segmentId)
            ?: segments.sumOf { it.effectiveDurationMs }   // 못 찾으면 끝(기존 fallback 동작 유지)

    /**
     * directive 생성 시 앵커(segmentId + source-local 좌표) 계산. [segment] 가 null(whole-video) 이면
     * 미앵커(글로벌 range 그대로 사용). 앵커되면 세그먼트 이동/복제 시 글로벌 range 가 자동 재계산됨.
     */
    private fun directiveAnchor(
        segment: Segment?,
        segStart: Long,
        globalStart: Long,
        globalEnd: Long,
    ): Triple<String, Long, Long> =
        if (segment == null) Triple("", 0L, 0L)
        else Triple(
            segment.id,
            DirectiveAnchor.toLocalMs(globalStart, segment, segStart),
            DirectiveAnchor.toLocalMs(globalEnd, segment, segStart),
        )

    /**
     * In-memory snapshot — `_uiState.value` 가 이미 모든 repository 의 최신 상태를 반영하고 있으므로
     * 다시 async fan-out 으로 repository 를 .first() 하지 않는다.
     *
     * 효과: pushUndoState 가 hot-path (드래그·슬라이더·복제 등) 에서 호출돼도 zero round-trip.
     * 이전 fan-out 은 매 mutation 마다 .first() 콜드 콜렉트 → dispatcher hop 비용이 측정됐다.
     */
    private fun buildSnapshot(): TimelineSnapshot {
        val s = _uiState.value
        return TimelineSnapshot(
            segments = s.segments,
            bgmClips = s.bgmClips,
            separationDirectives = s.separationDirectives,
        )
    }

    private fun pushUndoState() {
        mainUndoManager.pushState(buildSnapshot())
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        val mgr = mainUndoManager
        // update{} (atomic CAS) — observe* 콜렉터 emit 과 read-modify-write 가 interleave 해도
        // segments/bgmClips/directives 가 stale 값으로 revert 되지 않게. (onUndo/onRedo/commit* 등
        // 여러 경로에서 호출됨.)
        _uiState.update { it.copy(canUndo = mgr.canUndo, canRedo = mgr.canRedo) }
    }

    fun onUpdatePlaybackPosition(positionMs: Long) {
        _uiState.update { s ->
            val total = s.videoDurationMs.coerceAtLeast(0L)
            val hasSelection = s.pendingRangeEndMs > s.pendingRangeStartMs
            // range 모드 (음원분리/영상편집) + 선택 있음 → pendingRange 안으로 clamp.
            // zero-width 선택 또는 평상 모드 → 영상 전체 자유 재생.
            val clamped = when {
                s.isRangeSelecting && hasSelection ->
                    positionMs.coerceIn(s.pendingRangeStartMs, s.pendingRangeEndMs)
                else ->
                    positionMs.coerceIn(0L, total)
            }
            s.copy(playbackPositionMs = clamped)
        }
    }

    fun onTogglePlayback() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    /**
     * Single-selection model: at any moment at most one of segment / bgm may be selected.
     * This helper applies a tap-toggle on the chosen target while clearing
     * every other selected*Id.
     */
    private enum class SelectionTarget {
        Segment, Bgm
    }

    private fun selectExclusively(target: SelectionTarget, id: String?) {
        // 모든 segment/BGM 탭에서 호출되는 핵심 선택 경로 — observe* 콜렉터 emit 과 interleave 하므로
        // update{} 로 read-modify-write 를 atomic 화. 람다는 순수 (state·target·id 만 읽음).
        _uiState.update { state ->
            val current = when (target) {
                SelectionTarget.Segment -> state.selectedSegmentId
                SelectionTarget.Bgm -> state.selectedBgmClipId
            }
            val next = if (id != null && id == current) null else id
            // BGM 을 새로 선택(next != null) 한 경우엔 영상 다듬기/range 선택 모드를 함께 종료. 그래야
            // 하단 액션 토글이 Video 에서 Bgm 으로 교체된다 — 안 그러면 BGM 을 골랐는데 영상 토글이
            // 그대로 남아 사용자가 어느 트랙을 편집하는지 혼선.
            val switchingToBgm = target == SelectionTarget.Bgm && next != null
            state.copy(
                selectedSegmentId = if (target == SelectionTarget.Segment) next else null,
                selectedBgmClipId = if (target == SelectionTarget.Bgm) next else null,
                isVideoSelected = false,
                showVideoVolumeSlider = false,
                isSegmentEditMode = if (switchingToBgm) false else state.isSegmentEditMode,
                isRangeSelecting = if (switchingToBgm) false else state.isRangeSelecting,
                editTargets = if (switchingToBgm) setOf(EditTarget.Video) else state.editTargets,
                rangeTargetSegmentId = if (switchingToBgm) null else state.rangeTargetSegmentId,
                pendingRangeStartMs = if (switchingToBgm) 0L else state.pendingRangeStartMs,
                pendingRangeEndMs = if (switchingToBgm) 0L else state.pendingRangeEndMs,
            )
        }
    }

    // 자막 위치/스타일 메서드 제거 — 자막 기능 삭제.

    fun onUndo() {
        viewModelScope.launch {
            val snapshot = mainUndoManager.undo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
        }
    }

    fun onRedo() {
        viewModelScope.launch {
            val snapshot = mainUndoManager.redo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
        }
    }

    private suspend fun restoreSnapshot(snapshot: TimelineSnapshot) {
        // N+1 insert 회피 — DAO insertAll 한 번 IPC.
        segmentRepository.deleteAllByProjectId(projectId)
        segmentRepository.addSegments(snapshot.segments)
        bgmClipRepository.deleteAllByProjectId(projectId)
        bgmClipRepository.addClips(snapshot.bgmClips)
        separationDirectiveRepository.deleteByProject(projectId)
        if (snapshot.separationDirectives.isNotEmpty()) {
            separationDirectiveRepository.addAll(snapshot.separationDirectives)
        }
    }

    fun onVideoTrackTapped() {
        if (_uiState.value.isVideoSelected) {
            onDeselectVideo()
        } else {
            _uiState.value = _uiState.value.copy(
                isVideoSelected = true
            )
        }
    }

    fun onDeselectVideo() {
        _uiState.value = _uiState.value.copy(isVideoSelected = false, showVideoVolumeSlider = false)
    }

    fun onToggleVideoVolumeSlider() {
        _uiState.value = _uiState.value.copy(showVideoVolumeSlider = !_uiState.value.showVideoVolumeSlider)
    }

    fun onUpdateVideoVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(videoVolume = volume.coerceIn(0f, 2f))
    }

    fun onEnterTrimMode() {
        val state = _uiState.value
        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId }
            ?: state.segments.firstOrNull { it.type == SegmentType.VIDEO }
            ?: return
        if (selected.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, selected.id)
        val trimEndLocal = if (selected.trimEndMs <= 0L) selected.durationMs else selected.trimEndMs
        _uiState.value = state.copy(
            isTrimming = true,
            selectedSegmentId = selected.id,
            pendingTrimStartMs = segStart + selected.trimStartMs,
            pendingTrimEndMs = segStart + trimEndLocal,
            isPlaying = false
        )
    }

    fun onSetPendingTrimStart(ms: Long) {
        val state = _uiState.value
        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId } ?: return
        if (selected.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, selected.id)
        val segEnd = segStart + selected.durationMs
        val upperBound = (state.pendingTrimEndMs - 500L).coerceAtMost(segEnd - 500L)
        val newStart = ms.coerceIn(segStart, upperBound.coerceAtLeast(segStart))
        _uiState.value = state.copy(pendingTrimStartMs = newStart)
    }

    fun onSetPendingTrimEnd(ms: Long) {
        val state = _uiState.value
        val selected = state.segments.firstOrNull { it.id == state.selectedSegmentId } ?: return
        if (selected.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, selected.id)
        val segEnd = segStart + selected.durationMs
        val lowerBound = (state.pendingTrimStartMs + 500L).coerceAtLeast(segStart + 500L)
        val newEnd = ms.coerceIn(lowerBound.coerceAtMost(segEnd), segEnd)
        _uiState.value = state.copy(pendingTrimEndMs = newEnd)
    }

    fun onConfirmTrim() {
        val state = _uiState.value
        val segmentId = state.selectedSegmentId
            ?: state.segments.firstOrNull { it.type == SegmentType.VIDEO }?.id
            ?: return
        val segStart = segmentStartOffsetMs(state.segments, segmentId)
        val localTrimStart = (state.pendingTrimStartMs - segStart).coerceAtLeast(0L)
        val localTrimEnd = (state.pendingTrimEndMs - segStart).coerceAtLeast(localTrimStart + 500L)
        _uiState.value = state.copy(
            trimStartMs = state.pendingTrimStartMs,
            trimEndMs = state.pendingTrimEndMs,
            isTrimming = false,
            isVideoSelected = false
        )
        viewModelScope.launch {
            updateSegmentTrim(
                segmentId = segmentId,
                trimStartMs = localTrimStart,
                trimEndMs = localTrimEnd
            )
            pushUndoState()
        }
    }

    fun onCancelTrim() {
        _uiState.update { it.copy(isTrimming = false, isVideoSelected = false) }
    }

    /** Timeline 헤더 "저장" 버튼 — 원본 영상 렌더 + 갤러리 저장. */
    fun onSaveAllVariants() {
        if (_uiState.value.saveStatus is SaveStatus.RUNNING) return
        _uiState.update { s ->
            s.copy(
                saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
                shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
            )
        }
        viewModelScope.launch { runSaveAllVariants() }
    }

    private suspend fun runSaveAllVariants() {
        // 진행률 콜백이 suspend 경계를 넘나들며 반복 발화 — 그 사이 observe* 콜렉터가 segments/bgmClips
        // 를 갱신할 수 있으므로 status 쓰기는 모두 update{} (atomic) 로. value=value.copy() 면 진행률
        // tick 이 콜렉터 갱신을 덮어쓴다.
        _uiState.update { it.copy(saveStatus = SaveStatus.RUNNING(0)) }
        val result = saveAllVariants(
            projectId = projectId,
            onProgress = { percent ->
                _uiState.update { it.copy(saveStatus = SaveStatus.RUNNING(percent)) }
            },
        )
        result.fold(
            onSuccess = {
                _uiState.update { it.copy(saveStatus = SaveStatus.DONE) }
                runCatching { editProjectRepository.deleteProject(projectId) }
                _navigateBackHome.emit(Unit)
            },
            onFailure = { e ->
                com.vibi.shared.platform.logError("TimelineVM", "save failed", e)
                _uiState.update { it.copy(saveStatus = SaveStatus.FAILED("Save failed")) }
            }
        )
    }

    fun onClearSaveStatus() {
        _uiState.update { it.copy(saveStatus = SaveStatus.IDLE) }
    }

    /** Timeline 헤더 "공유" 버튼 — 원본 영상을 렌더 후 share sheet. */
    fun onShareExport() {
        if (_uiState.value.shareStatus is ShareStatus.RUNNING) return
        if (_uiState.value.segments.isEmpty()) return
        _uiState.update { s ->
            s.copy(
                saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
                shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
            )
        }
        viewModelScope.launch { runShareExport() }
    }

    private suspend fun runShareExport() {
        _uiState.update { it.copy(shareStatus = ShareStatus.RUNNING(0)) }
        val result = saveAllVariants(
            projectId = projectId,
            onProgress = { percent ->
                _uiState.update { it.copy(shareStatus = ShareStatus.RUNNING(percent)) }
            },
            saveToGallery = false,
        )
        result.fold(
            onSuccess = { variants ->
                val paths = variants.map { it.outputPath }
                if (paths.isEmpty()) {
                    _uiState.update { it.copy(shareStatus = ShareStatus.FAILED("Nothing to share")) }
                    return@fold
                }
                shareSheetLauncher.shareVideos(
                    sourcePaths = paths,
                    mimeType = "video/mp4",
                    title = "vibi",
                ).fold(
                    onSuccess = {
                        _uiState.update { it.copy(shareStatus = ShareStatus.DONE) }
                    },
                    onFailure = { e ->
                        com.vibi.shared.platform.logError("TimelineVM", "share sheet failed", e)
                        _uiState.update { it.copy(shareStatus = ShareStatus.FAILED("Share failed")) }
                    }
                )
            },
            onFailure = { e ->
                com.vibi.shared.platform.logError("TimelineVM", "share render failed", e)
                _uiState.update { it.copy(shareStatus = ShareStatus.FAILED("Share failed")) }
            }
        )
    }

    fun onClearShareStatus() {
        _uiState.update { it.copy(shareStatus = ShareStatus.IDLE) }
    }

    fun onShowAppendSheet() {
        _uiState.value = _uiState.value.copy(showAppendSheet = true)
    }

    fun onDismissAppendSheet() {
        _uiState.value = _uiState.value.copy(showAppendSheet = false)
    }

    fun onAppendVideoSegment(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAppendSheet = false, isPlaying = false)
            val info = videoMetadataExtractor.extract(uri) ?: return@launch
            addVideoSegment(projectId = projectId, videoInfo = info)
            pushUndoState()
        }
    }

    fun onSelectSegment(segmentId: String?) {
        selectExclusively(SelectionTarget.Segment, segmentId)
        // Segment selection additionally seeds the trim fields used by the
        // trim-mode overlay; the helper handles only the selected*Id fields.
        val selected = _uiState.value.segments.firstOrNull { it.id == _uiState.value.selectedSegmentId }
        _uiState.value = _uiState.value.copy(
            trimStartMs = selected?.trimStartMs ?: 0L,
            trimEndMs = selected?.trimEndMs ?: 0L
        )
    }

    fun onDeleteSelectedSegment() {
        val segmentId = _uiState.value.selectedSegmentId ?: return
        val segments = _uiState.value.segments
        if (segments.size <= 1) return
        viewModelScope.launch {
            // 삭제되는 세그먼트에 앵커된 directive 는 콘텐츠가 사라지므로 함께 삭제(고아 앵커 방지). 그 뒤
            // 세그먼트가 당겨지며 이후 directive 들의 글로벌 위치가 바뀌므로 reanchorDirectiveCache 로 캐시
            // 재계산 + _uiState 동기(undo 스냅샷 정합).
            separationDirectiveRepository.getByProject(projectId)
                .filter { it.segmentId == segmentId }
                .forEach { separationDirectiveRepository.delete(it.id) }
            removeSegment(segmentId)
            refreshSegmentsStateFromDb()
            purgeOrphanedDirectives()
            reanchorDirectiveCache()
            _uiState.value = _uiState.value.copy(
                selectedSegmentId = null,
                isPlaying = false,
                playbackPositionMs = 0L
            )
            pushUndoState()
        }
    }

    /**
     * 점유 구간과 겹치지 않는 자유 구간들 — segment 영역 [segStart, segEnd] 안에서. 빈 리스트면 전체 점유.
     *
     * 점유 기준은 호출 맥락에 따라 다르다 ([excludeCompletedDirectives]):
     *  - 음원분리 *추가* 흐름(default true): 진행 중 잡 + 완료 directive 모두 점유 — 이미 분리됐거나 분리
     *    중인 구간을 다시 분리 대상으로 잡지 못하게.
     *  - 영상 다듬기 흐름(false): 진행 중 잡만 점유 — 완료된 분리 구간은 인접 영상과 함께 선택/편집 가능.
     */
    private fun freeIntervalsInSegment(
        segStart: Long,
        segEnd: Long,
        processing: List<ProcessingSeparation> = _uiState.value.processingSeparations,
        excludeCompletedDirectives: Boolean = true,
    ): List<LongRange> {
        // 진행 중 분리는 격리된 분리 세그먼트(segmentId)에 앵커 — 점유 구간을 그 세그먼트의 *현재* timeline
        // 범위로 계산해 재정렬로 세그먼트가 이동해도 점유가 따라온다(정적 캐시 range 의 stale 방지). 세그먼트가
        // 없으면(이례적) 캐시 range 로 폴백, range 도 null 이면 전체 영상 분리로 보고 segStart..segEnd 점유.
        val segs = _uiState.value.segments
        val processingRanges = processing.map { p ->
            val seg = segs.firstOrNull { it.id == p.segmentId }
            if (seg != null) {
                val s = segmentStartOffsetMs(segs, seg.id)
                s..(s + seg.effectiveDurationMs)
            } else {
                (p.rangeStartMs ?: segStart)..(p.rangeEndMs ?: segEnd)
            }
        }
        val committedRanges = if (excludeCompletedDirectives)
            _uiState.value.separationDirectives.map { it.rangeStartMs..it.rangeEndMs }
        else emptyList()
        val occupied = (processingRanges + committedRanges)
            .filter { it.last > segStart && it.first < segEnd }
            .sortedBy { it.first }
        val free = mutableListOf<LongRange>()
        var cursor = segStart
        for (occ in occupied) {
            if (occ.first - cursor >= MIN_RANGE_MS) free += cursor..(occ.first)
            cursor = maxOf(cursor, occ.last)
        }
        if (segEnd - cursor >= MIN_RANGE_MS) free += cursor..segEnd
        return free
    }

    fun onEnterRangeMode(segmentId: String) {
        val state = _uiState.value
        // 자유 구간(분리중/완료 directive 제외)이 있는 첫 비디오 세그먼트로 진입. 첫 세그먼트가 분리중이라
        // 자유 구간이 없어도 다른 세그먼트에서 새 분리를 시작할 수 있게 — 종전엔 항상 첫 세그먼트만 보고
        // 자유 구간이 없으면 진입 자체를 거부해 "음원분리 버튼이 안 눌리는" 문제가 있었다. 어디에도 자유
        // 구간이 없으면(전 구간 분리중/완료) 거부.
        val target = state.segments
            .filter { it.type == SegmentType.VIDEO }
            .firstNotNullOfOrNull { s ->
                val segStart = segmentStartOffsetMs(state.segments, s.id)
                val segEnd = segStart + s.effectiveDurationMs
                freeIntervalsInSegment(segStart, segEnd).firstOrNull()?.let { s to it }
            } ?: return
        val seg = target.first
        val defaultRange = target.second
        _uiState.value = state.copy(
            isRangeSelecting = true,
            isSegmentEditMode = false,
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            pendingRangeStartMs = defaultRange.first,
            pendingRangeEndMs = defaultRange.last,
            showRangeActionSheet = false,
            pendingRangeVolume = seg.volumeScale,
            pendingRangeSpeed = seg.speedScale,
            isPlaying = false,
            // 재생 헤드는 그대로 — 사용자가 보던 위치 유지. 구간 선택만 영향.
        )
    }

    /**
     * 영상 위 우상단 연필 버튼에서 호출 — segment 편집(복제/삭제/볼륨/속도) 진입.
     * [onEnterRangeMode] 와 동일한 range slider UI 를 띄우지만 confirm 액션이 음성분리가 아닌
     * segment edit action sheet (복제/삭제/볼륨/속도) 로 갈라진다.
     *
     * 영상편집 액션은 통합 timeline undo 스택에 push — 음원분리/음원삽입/자막/더빙과 같이 단일 흐름으로 되돌릴 수 있다.
     */
    fun onEnterSegmentEditMode(
        segmentId: String,
        targets: Set<EditTarget> = setOf(EditTarget.Video),
        tapMs: Long? = null,
    ) {
        val state = _uiState.value
        // tapMs 가 주어지면 그 지점이 속한 비디오 세그먼트로 진입한다 — 첫 세그먼트가 분리중이어도 탭한
        // 세그먼트를 편집할 수 있게(UI 가 항상 첫 세그먼트 id 를 넘기던 한계 보정). 없으면 넘어온 segmentId.
        val seg = (if (tapMs != null) {
            state.segments.firstOrNull { sg ->
                if (sg.type != SegmentType.VIDEO) return@firstOrNull false
                val s = segmentStartOffsetMs(state.segments, sg.id)
                tapMs in s..(s + sg.effectiveDurationMs)
            }
        } else null) ?: state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, seg.id)
        val segEnd = segStart + seg.effectiveDurationMs
        // 탭으로 진입하면 첫 클릭부터 그 지점의 free 구간(음원분리 *진행 중* 제외, 완료 directive 포함)으로
        // 선택을 좁힌다 — 진입 시 세그먼트 전체(분리중 포함)가 잡히던 문제 해소. 탭 지점이 분리중이면 첫 free
        // 구간으로 스냅. free 구간이 하나도 없으면(=전체 구간 음원분리 진행 중) 진입 자체를 막는다 — 진입을
        // 허용하면 같은 free-구간 규칙에 막혀 선택 해제(onSelectSegmentInEdit)도 안 돼 해제 불가 상태에 갇힘.
        // tapMs 없으면(비-탭 진입) segment 전체.
        // 진입 시 원본 영상으로 강제 reset — 사용자는 편집 결과를 원본 영상에서 확인.
        // 진입 시 음성분리/자막더빙 sheet 들도 같이 닫음 — 영상편집 중엔 노출 금지.
        val range = if (tapMs != null) {
            val frees = freeIntervalsInSegment(segStart, segEnd, excludeCompletedDirectives = false)
            frees.firstOrNull { tapMs in it } ?: frees.firstOrNull() ?: return
        } else {
            segStart..segEnd
        }
        _uiState.value = state.copy(
            isRangeSelecting = true,
            isSegmentEditMode = true,
            editTargets = targets.ifEmpty { setOf(EditTarget.Video) },
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            // 영상 편집 모드 진입 = 단일 선택 모델상 BGM 선택 해제. 안 그러면 deck
            // 카드 highlight 또는 잠재적 BGM 하단바가 영상 모드와 동시에 잡힘.
            selectedBgmClipId = null,
            pendingRangeStartMs = range.first,
            pendingRangeEndMs = range.last,
            showRangeActionSheet = false,
            pendingRangeVolume = seg.volumeScale,
            pendingRangeSpeed = seg.speedScale,
            isPlaying = false,
            showAudioSeparationSheet = false,
            showAppendSheet = false,
        )
        updateUndoRedoState()
    }

    /**
     * 영상편집 모드에서 타임라인 바의 segment 블록을 탭 — 해당 segment 전체를 pendingRange 로 잡고
     * 선택 상태로 만든다. 볼륨/속도 슬라이더 초기값도 그 segment 의 현재 값으로.
     * 재생 헤드를 segment 시작점으로 이동 — 사용자가 어느 segment 를 보고 있는지 즉시 확인.
     */
    /**
     * 사용자 탭 진입점. 같은 segment 재탭 → 선택 해제 토글. 다른 id → 그 segment 로 select.
     * 시스템(편집 후 자동 reselect) 용 path 는 [selectSegmentInEditInternal] 직접 호출 — 토글 로직 우회.
     */
    fun onSelectSegmentInEdit(segmentId: String, tapMs: Long? = null) {
        val snapshot = _uiState.value
        if (!snapshot.isSegmentEditMode) return
        val seg = snapshot.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(snapshot.segments, seg.id)
        val segEnd = segStart + seg.effectiveDurationMs
        // 탭 지점([tapMs])이 주어지면 그 점이 속한 free 구간(음원분리 *진행 중* 제외)으로 선택을 좁힌다.
        // 완료된 directive 는 free 에 포함되므로 인접 영상과 함께 선택됨. 진행 중 구간 위 탭이면 무동작
        // (선택 안 함). tapMs 없으면(시스템 호출 등) segment 전체.
        val target: LongRange = if (tapMs != null) {
            // 영상 다듬기 — 진행 중 분리만 제외, 완료 directive 는 선택 가능(free 에 포함).
            freeIntervalsInSegment(
                segStart, segEnd, snapshot.processingSeparations,
                excludeCompletedDirectives = false,
            ).firstOrNull { tapMs in it } ?: return
        } else {
            segStart..segEnd
        }
        // 같은 구간 재탭 → deselect 토글.
        if (snapshot.selectedSegmentId == segmentId &&
            snapshot.pendingRangeStartMs == target.first &&
            snapshot.pendingRangeEndMs == target.last
        ) {
            // atomic CAS — Room observe Flow 가 intermediate state 를 emit 하는 윈도우에 click 핸들러의
            // 비-atomic write 가 stale segments 를 박아 넣어 영상편집 결과가 원복되던 race 차단.
            _uiState.update {
                it.copy(
                    selectedSegmentId = null,
                    rangeTargetSegmentId = null,
                    pendingRangeStartMs = 0L,
                    pendingRangeEndMs = 0L,
                    editTargets = setOf(EditTarget.Video),
                )
            }
            return
        }
        selectSegmentInEditAtRange(segmentId, target.first, target.last)
    }

    /**
     * 토글 없이 강제 select — 시스템에서 호출 (apply/duplicate/delete 직후 새 middle 로 reselect).
     * 사용자 탭 동작과 분리해 race 가 deselect 로 빠지지 않게 한다.
     */
    private fun selectSegmentInEditInternal(segmentId: String) {
        // atomic CAS — Room observe Flow intermediate emit 와 인터리브 되어도 항상 최신 segments 로
        // segStart/segEnd 를 계산해 stale snapshot 으로 _uiState 를 덮어쓰지 않게. 영상편집 + Apply
        // 직후 selectSegmentInEdit 가 stale `_uiState.value.segments` 를 read-modify-write 하면서
        // refresh 결과를 박살내던 race 차단.
        _uiState.update { state ->
            if (!state.isSegmentEditMode) return@update state
            val seg = state.segments.firstOrNull { it.id == segmentId } ?: return@update state
            if (seg.type != SegmentType.VIDEO) return@update state
            val segStart = segmentStartOffsetMs(state.segments, seg.id)
            val segEnd = segStart + seg.effectiveDurationMs
            state.copy(
                rangeTargetSegmentId = seg.id,
                selectedSegmentId = seg.id,
                pendingRangeStartMs = segStart,
                pendingRangeEndMs = segEnd,
                pendingRangeVolume = seg.volumeScale,
                pendingRangeSpeed = seg.speedScale,
                // 재생바(playhead)는 선택과 무관하게 현재 위치 유지 — 클립 선택 시 따라 이동하지 않음.
                // 영상 segment 탭 → editTargets 를 Video 로 (BGM 모드였다면 자동 해제).
                editTargets = setOf(EditTarget.Video),
            )
        }
    }

    /**
     * 명시적 range 로 segment select — [onSelectSegmentInEdit] 가 탭 지점의 free 구간으로 좁힌 범위를
     * 넘긴다. range 는 atomic update 안에서 최신 segment bounds 로 clamp (stale snapshot 방어).
     */
    private fun selectSegmentInEditAtRange(segmentId: String, rangeStart: Long, rangeEnd: Long) {
        _uiState.update { state ->
            if (!state.isSegmentEditMode) return@update state
            val seg = state.segments.firstOrNull { it.id == segmentId } ?: return@update state
            if (seg.type != SegmentType.VIDEO) return@update state
            val segStart = segmentStartOffsetMs(state.segments, seg.id)
            val segEnd = segStart + seg.effectiveDurationMs
            val rs = rangeStart.coerceIn(segStart, segEnd)
            val re = rangeEnd.coerceIn(rs, segEnd)
            state.copy(
                rangeTargetSegmentId = seg.id,
                selectedSegmentId = seg.id,
                pendingRangeStartMs = rs,
                pendingRangeEndMs = re,
                pendingRangeVolume = seg.volumeScale,
                pendingRangeSpeed = seg.speedScale,
                // 재생바는 선택과 무관하게 현재 위치 유지.
                editTargets = setOf(EditTarget.Video),
            )
        }
    }

    /**
     * range 모드 (음원분리/영상편집) 안에서 "구간 선택만" 비움 — 모드 자체는 유지.
     * 사용자가 다른 segment/free interval 을 탭하면 다시 selection 생성.
     */
    fun onClearRangeSelection() {
        // atomic CAS — loadSegments emit 와 인터리브 시 stale segments 박힘 race 차단.
        // segment edit 모드 중이면 range 해제 = 편집 의미 상실 → 모드 자체 종료해 하단바도 함께 닫힘.
        // 음원분리 range mode (isSegmentEditMode=false) 는 사용자가 새 구간 다시 잡을 수 있도록 모드 유지.
        _uiState.update { state ->
            if (!state.isRangeSelecting) return@update state
            if (state.isSegmentEditMode) {
                state.copy(
                    selectedSegmentId = null,
                    rangeTargetSegmentId = null,
                    pendingRangeStartMs = 0L,
                    pendingRangeEndMs = 0L,
                    isSegmentEditMode = false,
                    isRangeSelecting = false,
                )
            } else {
                state.copy(
                    selectedSegmentId = null,
                    rangeTargetSegmentId = null,
                    pendingRangeStartMs = 0L,
                    pendingRangeEndMs = 0L,
                )
            }
        }
    }

    fun onCancelSegmentEditMode() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            editTargets = setOf(EditTarget.Video),
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
        )
        updateUndoRedoState()
    }

    /**
     * 다듬기 모드 활성 시 사용자가 칩으로 트랙 multi-select 토글. 빈 set 으로는 못 만듦 —
     * 사용자가 모두 해제하면 자동으로 Video 다시 추가.
     */
    fun onToggleEditTarget(target: EditTarget) {
        val state = _uiState.value
        if (!state.isSegmentEditMode) return
        val next = state.editTargets.toMutableSet().also {
            if (!it.add(target)) it.remove(target)
        }
        _uiState.value = state.copy(
            editTargets = next.ifEmpty { setOf(EditTarget.Video) },
        )
    }

    /** chip row 의 "다 끄고 새로 선택" 같은 강제 set. 빈 set 은 Video 로 폴백. */
    fun onSetEditTargets(targets: Set<EditTarget>) {
        val state = _uiState.value
        if (!state.isSegmentEditMode) return
        _uiState.value = state.copy(
            editTargets = targets.ifEmpty { setOf(EditTarget.Video) },
        )
    }

    /**
     * 영상편집 모드의 X(취소) — 편집 진입 직전 스냅샷으로 즉시 복원하고 모드 종료.
     * 사용자가 영상편집 중 적용한 복제/삭제/볼륨/속도 변경을 모두 무효화.
     */
    /**
     * 영상편집 모드의 ✓(체크) — 편집 확정. 영상 segment 와 다른 트랙 산출물은 그대로 두고 모드만 종료.
     * BFF 호출 없음 — 단순 로컬 상태 리셋만.
     *
     * **undo/redo 는 보존한다** — 영상 전체 삭제처럼 다듬기 모드에서 한 편집도 확정 후 되돌릴 수
     * 있어야 한다. (과거엔 commit 이 산출물을 wipe 해 undo 가 inconsistent → 스택을 clear 했지만,
     * 정책 변경으로 더 이상 wipe 하지 않으므로 clear 는 회귀를 만든다. 스냅샷은 full-state 라 commit
     * 경계를 넘어 복원해도 안전.)
     */
    fun onCommitSegmentEdit() {
        viewModelScope.launch { commitSegmentEdit() }
    }

    /**
     * commit 본체 — sequential 호출 (onAdvanceStep 의 자동 commit) 가능하도록 suspend 로 분리.
     *
     * 정책 변경 (기획: "구간 × 트랙 N:M 편집") — 더 이상 산출물(BGM/분리/자막/더빙) wipe 안 함.
     * 다듬기 모드의 액션은 [editTargets] 가 지정한 트랙에만 mutate 됐고, 다른 트랙의 산출물은
     * 그대로 유효. 영상 segment 가 split/delete 되어 글로벌 timeline 길이가 변한 경우의
     * BGM/stem 시간축 보정은 별도 sync (후속 phase) 로 다룬다.
     */
    private suspend fun commitSegmentEdit() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            editTargets = setOf(EditTarget.Video),
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
            selectedSegmentId = null,
        )
        // commit 은 콘텐츠를 바꾸지 않고 (모드 플래그만, 스냅샷에 미포함) 직전 편집 액션이 이미 undo
        // 스냅샷을 push 했다. 여기서 또 push 하면 동일 상태가 중복 적재돼 "첫 undo 가 헛도는" 문제가
        // 생기므로 push 하지 않고 canUndo/canRedo 만 갱신.
        updateUndoRedoState()
    }

    /**
     * directive 사이의 gap 을 한 번에 pendingRange 로 점프. range 모드 비활성이면 자동 진입.
     * 타임라인의 회색(directive) 구간을 시각화하고, 그 외(사용 가능) 구간 탭으로 즉시 그 구간을
     * 풀 셀렉트 → 슬라이더로 fine-tune 하는 UX.
     */
    fun onSelectFreeRange(startMs: Long, endMs: Long) {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(0L)
        val s = startMs.coerceIn(0L, total)
        val e = endMs.coerceIn(0L, total)
        if (e - s < MIN_RANGE_MS) return
        // 재생바(playhead)는 선택 구간과 무관하게 현재 위치 유지 — 클립/구간 선택 시 따라 이동·clamp 하지 않음.
        if (state.isRangeSelecting) {
            _uiState.value = state.copy(
                pendingRangeStartMs = s,
                pendingRangeEndMs = e,
            )
        } else {
            // range 모드 진입 — 첫 video segment 를 타깃으로. 슬라이더 valueRange 는 이후 영상 전체.
            val seg = state.segments.firstOrNull { it.type == SegmentType.VIDEO } ?: return
            _uiState.value = state.copy(
                isRangeSelecting = true,
                isSegmentEditMode = false,
                rangeTargetSegmentId = seg.id,
                selectedSegmentId = seg.id,
                pendingRangeStartMs = s,
                pendingRangeEndMs = e,
                showRangeActionSheet = false,
                pendingRangeVolume = seg.volumeScale,
                pendingRangeSpeed = seg.speedScale,
                isPlaying = false,
            )
        }
    }

    /**
     * Segment edit 중 영상 strip 의 free 영역 탭 — editTargets=Video 로 전환 + range 스냅을 한 번에.
     * UI 가 두 콜을 따로 하면 매번 _uiState 갱신 2회 → 중간 recomposition 한 번 더 발생.
     */
    fun onSelectVideoRange(startMs: Long, endMs: Long) {
        val state = _uiState.value
        if (state.isSegmentEditMode && !state.editTargets.hasVideo()) {
            _uiState.value = state.copy(editTargets = setOf(EditTarget.Video))
        }
        onSelectFreeRange(startMs, endMs)
    }

    /**
     * Segment edit 중 BGM 블럭 탭 — selectedBgm + editTargets=Bgm 전환 + range=BGM bounds 를 한 번에.
     * 셋을 따로 호출하면 _uiState 갱신 3회 → recomposition 3사이클. selectExclusively 가 _uiState
     * 를 직접 갱신하는 구조라 외부에서 단일 copy 로는 합쳐지지 않아 두 단계(selection, range+target)로만 묶음.
     */
    fun onSelectBgmForRangeEdit(clipId: String) {
        val clip = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return
        onSelectBgmClip(clipId)
        val state = _uiState.value
        if (!state.editTargets.hasBgm() ||
            state.editTargets.none { it is EditTarget.Bgm && it.clipId == clipId }
        ) {
            _uiState.value = state.copy(editTargets = setOf(EditTarget.Bgm(clipId)))
        }
        onSelectFreeRange(clip.startMs, clip.startMs + clip.effectiveDurationMs)
    }

    /**
     * BGM/녹음 클립 단독 탭 → 영상 다듬기와 동일한 구간편집 모드 진입(isSegmentEditMode=false 인 BGM 모드).
     * 범위는 클립 전체로 시작, 핸들로 서브-구간 좁힘([rangeBoundsForCurrentMode] 가 클립 안으로 clamp).
     * 같은 클립 재호출 → 토글 종료. 배경음 제거 진행 중인 클립은 무시(편집 잠금).
     */
    fun onEnterBgmRangeEditMode(clipId: String) {
        val state = _uiState.value
        // 진행 중 분리 클립은 잠금
        if (state.bgmBackgroundRemovalProgress[clipId] is BgmRemovalProgress.Processing) return
        // 같은 클립 재탭 → 토글 종료
        val alreadyThisClip = !state.isSegmentEditMode &&
            state.editTargets.any { it is EditTarget.Bgm && it.clipId == clipId }
        if (alreadyThisClip) {
            _uiState.value = state.copy(
                isRangeSelecting = false,
                editTargets = setOf(EditTarget.Video),
                selectedBgmClipId = null,
                rangeTargetSegmentId = null,
                pendingRangeStartMs = 0L,
                pendingRangeEndMs = 0L,
            )
            return
        }
        val clip = state.bgmClips.firstOrNull { it.id == clipId } ?: return
        _uiState.value = state.copy(
            // 음원 클릭 → 선택 표시 + 양 끝 구간 핸들 + 하단 편집 버튼(in-timeline range edit). 별도 분리 시트로는
            // 진입하지 않음(이건 이 함수가 아예 안 여는 경로라 무관). 핸들/선택 UI 는 showRange=isRangeSelecting 필요.
            isRangeSelecting = true,
            isSegmentEditMode = false,
            editTargets = setOf(EditTarget.Bgm(clipId)),
            selectedBgmClipId = clipId,
            selectedSegmentId = null,
            isVideoSelected = false,
            showVideoVolumeSlider = false,
            rangeTargetSegmentId = null,
            pendingRangeStartMs = clip.startMs,
            pendingRangeEndMs = clip.startMs + clip.effectiveDurationMs,
            pendingRangeVolume = clip.volumeScale,
            pendingRangeSpeed = clip.speedScale,
            showRangeActionSheet = false,
            isPlaying = false,
        )
    }

    /**
     * BGM 단일 클립 구간편집이면 그 액션을 clip-local 헬퍼로 실행하고 true 반환(호출부 early-return).
     * 4개 range 액션(볼륨/속도/삭제/복제)의 동일한 분기·launch·재타깃·undo 보일러플레이트를 한 곳으로.
     * 호출 시점엔 이미 MIN_RANGE_MS 가드를 통과한 상태(각 함수 상단).
     */
    private fun runBgmClipLocal(action: suspend (BgmClip, Long, Long) -> BgmEditFocus): Boolean {
        val state = _uiState.value
        val clip = bgmClipLocalTarget(state) ?: return false
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        viewModelScope.launch {
            val focus = action(clip, start, end)
            // observeClips 는 Room DAO Flow(비동기 emit) 라 repo 쓰기 직후 _uiState.bgmClips 가 아직 분할 전.
            // Apply 는 저빈도(탭) 이므로 1회 동기 reload 로 스냅샷을 분할 결과로 갱신 → undo/redo 정확.
            // 동시에 새 focus 클립이 state 에 즉시 들어와 하단 패널이 그 클립을 바로 찾음(깜빡임 방지).
            val fresh = applyBgmLaneOverrides(bgmClipRepository.observeClips(projectId).first())
            _uiState.update { it.copy(bgmClips = fresh) }
            applyBgmEditFocus(focus)
            pushUndoState()
        }
        return true
    }

    /**
     * 이동/리사이즈 clamp 경계.
     * - 영상편집 모드: *진행 중* 음원분리 구간만 침범 금지(완료 directive 는 인접 영상과 함께 편집 가능).
     *   현재 선택을 포함하는, 분리중 구간으로 막히지 않은 최대 연속 구간으로 clamp — 초기 선택 규칙
     *   ([onSelectSegmentInEdit] 의 excludeCompletedDirectives=false)과 동일. sliceGlobalRange 가
     *   다중 segment 자동 처리.
     * - 음원분리 모드: directive-free interval(완료 directive + 진행 중 모두 제외) 안으로 clamp.
     */
    private fun rangeBoundsForCurrentMode(): Pair<Long, Long> {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(0L)
        // BGM 단일 클립 구간편집 — 핸들을 그 클립의 timeline 범위 안으로 clamp.
        val bgmTarget = state.editTargets.firstOrNull { it is EditTarget.Bgm } as? EditTarget.Bgm
        if (!state.isSegmentEditMode && bgmTarget?.clipId != null) {
            val clip = state.bgmClips.firstOrNull { it.id == bgmTarget.clipId }
            if (clip != null) return clip.startMs to (clip.startMs + clip.effectiveDurationMs)
        }
        // 영상편집: 진행 중 분리만 침범 금지(완료 directive 는 편집 가능). 음원분리: 진행 중 + 완료 모두 제외.
        val frees = freeIntervalsInSegment(0L, total, excludeCompletedDirectives = !state.isSegmentEditMode)
        if (frees.isEmpty()) return 0L to total
        // 현재 선택을 담는 free interval 로 clamp — 핸들이 그 경계(=분리중 구간 가장자리)를 넘지 못한다.
        // 어느 끝도 free interval 안에 없으면(이례적) 전체로 풀지 말고 선택 중점에 가장 가까운 interval 로
        // 가둔다 — 종전 `?: total` 폴백이 분리중 구간으로 핸들이 넘어가게 두던 문제 차단.
        val mid = (state.pendingRangeStartMs + state.pendingRangeEndMs) / 2
        val containing = frees.firstOrNull { state.pendingRangeStartMs in it }
            ?: frees.firstOrNull { state.pendingRangeEndMs in it }
            ?: frees.minByOrNull { minOf(abs(it.first - mid), abs(it.last - mid)) }
            ?: return 0L to total
        return containing.first to containing.last
    }

    fun onSetPendingRangeStart(globalMs: Long) {
        val state = _uiState.value
        val upper = (state.pendingRangeEndMs - MIN_RANGE_MS).coerceAtLeast(0L)
        val (lower, hi) = rangeBoundsForCurrentMode()
        val clamped = globalMs.coerceIn(
            minimumValue = lower,
            maximumValue = upper.coerceIn(lower, hi),
        )
        _uiState.value = state.copy(pendingRangeStartMs = clamped)
    }

    fun onSetPendingRangeEnd(globalMs: Long) {
        val state = _uiState.value
        val lower = (state.pendingRangeStartMs + MIN_RANGE_MS).coerceAtMost(state.videoDurationMs)
        val (freeLower, freeUpper) = rangeBoundsForCurrentMode()
        val clamped = globalMs.coerceIn(
            minimumValue = lower.coerceIn(freeLower, freeUpper),
            maximumValue = freeUpper,
        )
        _uiState.value = state.copy(pendingRangeEndMs = clamped)
    }

    /**
     * 구간 fill 영역을 잡고 드래그하여 양쪽 끝을 같은 폭으로 이동. width 보존, 영상편집/음원분리
     * 양쪽 모두 자기 모드 bounds 안으로 clamp.
     */
    fun onTranslateRange(newStartMs: Long) {
        val state = _uiState.value
        val width = (state.pendingRangeEndMs - state.pendingRangeStartMs).coerceAtLeast(MIN_RANGE_MS)
        val (lower, upper) = rangeBoundsForCurrentMode()
        val maxStart = (upper - width).coerceAtLeast(lower)
        val clampedStart = newStartMs.coerceIn(lower, maxStart)
        _uiState.value = state.copy(
            pendingRangeStartMs = clampedStart,
            pendingRangeEndMs = clampedStart + width,
        )
    }

    /**
     * Slice the global range [globalStart, globalEnd] into per-segment local
     * ranges. Returns segments that overlap, sorted by `order`. Skips IMAGE
     * and segments where the overlap is below MIN_RANGE_MS.
     */
    private data class SegmentRangeSlice(
        val segmentId: String,
        val order: Int,
        val localStart: Long,
        val localEnd: Long
    )

    private fun sliceGlobalRange(globalStart: Long, globalEnd: Long): List<SegmentRangeSlice> {
        val out = mutableListOf<SegmentRangeSlice>()
        var acc = 0L
        for (seg in _uiState.value.segments) {
            val segDur = seg.effectiveDurationMs   // 타임라인 위 길이 (speed 반영)
            val segGlobalStart = acc
            val segGlobalEnd = acc + segDur
            acc += segDur
            if (seg.type != SegmentType.VIDEO) continue
            val overlapStart = maxOf(segGlobalStart, globalStart)
            val overlapEnd = minOf(segGlobalEnd, globalEnd)
            if (overlapEnd - overlapStart < MIN_RANGE_MS) continue
            // global ms (timeline) → source-media ms 변환 — speedScale 만큼 stretch.
            // round() 후 toLong() — `.toLong()` 단독은 truncate 라 (overlap*speed) = 999.67 같은
            // 값에서 999 로 떨어져 1ms 미만 잔재 segment 가 생김. 인접 segment 의 trimStart 와
            // 어긋나 audio render 가 silent fallback 으로 빠지는 원인.
            val speed = if (seg.speedScale > 0f) seg.speedScale else 1f
            val localStart = seg.trimStartMs + kotlin.math.round((overlapStart - segGlobalStart) * speed).toLong()
            val localEnd = seg.trimStartMs + kotlin.math.round((overlapEnd - segGlobalStart) * speed).toLong()
            out += SegmentRangeSlice(seg.id, seg.order, localStart, localEnd)
        }
        return out
    }

    fun onConfirmRangeSelection() {
        _uiState.value = _uiState.value.copy(showRangeActionSheet = true)
    }

    fun onCancelRangeMode() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false
        )
    }

    fun onUpdatePendingRangeVolume(value: Float) {
        _uiState.value = _uiState.value.copy(pendingRangeVolume = value.coerceIn(0f, 2f))
    }

    fun onUpdatePendingRangeSpeed(value: Float) {
        _uiState.value = _uiState.value.copy(pendingRangeSpeed = value.coerceIn(0.25f, 4f))
    }

    /**
     * 편집 mutation (split/remove/duplicate/volume/speed) 직후 _uiState.segments 를 동기 fetch 로
     * 강제 갱신 — Room observe Flow emit 지연이 reselect 시점에 stale segments 보게 만드는 race 우회.
     * loadSegments collector 가 다음 emit 에서 같은 값으로 덮어쓰지만 결과는 idempotent.
     *
     * `_uiState.update {}` (atomic CAS) 로 다른 launch 의 read-modify-write 와 인터리브 시
     * stale state 위에 segments 만 partial update 되는 race 차단. 본 함수가 await 하는 동안
     * 다른 mutation handler 가 selectedSegmentId 등을 갱신해도 그 값을 보존.
     */
    /**
     * directive 편집 mutation 직후 `_uiState.separationDirectives` 를 동기 fetch 로 강제 갱신 —
     * [refreshSegmentsStateFromDb] 의 directive 버전. observe Flow emit 지연이 곧바로 찍는 undo
     * 스냅샷([pushUndoState])에 stale directive 를 박는 race 우회. 다음 Flow emit 이 같은 값으로
     * 덮어쓰므로 idempotent.
     */
    private suspend fun refreshDirectivesStateFromDb() {
        val fresh = separationDirectiveRepository.getByProject(projectId)
        _uiState.update { it.copy(separationDirectives = fresh) }
    }

    private suspend fun refreshSegmentsStateFromDb() {
        val fresh = segmentRepository.getByProjectId(projectId)
        val total = fresh.sumOf { it.effectiveDurationMs }
        val first = fresh.firstOrNull()
        _uiState.update { current ->
            current.copy(
                segments = fresh,
                videoDurationMs = total,
                videoUri = first?.sourceUri.orEmpty(),
                videoWidth = first?.width ?: 0,
                videoHeight = first?.height ?: 0,
            )
        }
    }

    /**
     * 세그먼트 드래그 재정렬 — [segmentId] 를 [targetIndex] 로 이동. directive(음원분리)는 세그먼트에
     * 앵커돼 있어 order 만 바꾸면 자동으로 따라간다: [reanchorDirectiveCache] 가 새 순서로 글로벌 range
     * 캐시를 재계산(+ _uiState 동기). no-op(제자리)면 아무 것도 안 함.
     */
    fun onMoveSegment(segmentId: String, targetIndex: Int) {
        viewModelScope.launch {
            moveSegment(segmentId, targetIndex) ?: return@launch
            refreshSegmentsStateFromDb()
            reanchorDirectiveCache()
            pushUndoState()
        }
    }

    fun onDuplicateRange() {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        if (runBgmClipLocal { c, s, e -> applyBgmClipRangeDuplicate(c, s, e) }) return
        val applyToVideo = state.editTargets.hasVideo()
        val applyToBgm = state.editTargets.hasBgm()
        val slices = if (applyToVideo) sliceGlobalRange(start, end).sortedByDescending { it.order } else emptyList()
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            var lastDuplicated: Segment? = null
            // 다중 세그먼트 범위는 복제본을 하나의 병합 세그먼트로 범위 끝 뒤에 삽입 ([a|b|c] → a+b 복제 → [a|b|ab|c]).
            // 병합 불가(다른 source 등)면 null → 기존 per-segment 복제로 폴백.
            if (slices.size > 1) {
                lastDuplicated = duplicateRangeAsMergedBlock(slices)
            }
            if (lastDuplicated == null) {
                slices.forEach { s ->
                    lastDuplicated = duplicateSegmentRange(s.segmentId, s.localStart, s.localEnd)
                }
            }
            if (applyToBgm) applyBgmRangeDuplicate(start, end)
            refreshSegmentsStateFromDb()
            if (applyToVideo) {
                // directive 따라가기 — DuplicateSegmentRangeUseCase 가 복제본 세그먼트에 directive 를 새
                // segmentId 로 복제(앵커 보존)했고, 여기서 모든 앵커 directive 의 글로벌 range 캐시를 새
                // 배치(복제로 뒤가 +width 밀림)로 재계산한다. 앵커링이 기존 글로벌 ms ripple 을 대체.
                reanchorDirectiveCache()
            }
            if (wasSegmentEdit) {
                lastDuplicated?.id?.let { selectSegmentInEditInternal(it) }
            }
            pushUndoState()
        }
    }

    /**
     * 다중 세그먼트 범위 복제 — 복제본(범위 내용)을 **범위 끝 바로 뒤**에 삽입한다. 삽입 자리를 만들기 위해
     * **범위 끝(마지막 세그먼트)만 그 지점에서 split** 한다(b1b2 → b1|b2). 시작·중간 세그먼트는 **안 쪼갠다**.
     * [a1a2|b1b2] 에서 a2b1 복제 → [a1a2 | b1 | a2b1 | b2] (A 그대로, B만 b1|b2, 복제본은 b1 뒤).
     *
     * 복제본 trim = [첫 slice localStart, 마지막 slice localEnd] — 같은 source 연속이면 한 덩어리.
     * 다른 source 면 각 slice 구간을 조각별로(순서 유지) 범위 끝 뒤에 그룹 삽입.
     */
    private suspend fun duplicateRangeAsMergedBlock(slices: List<SegmentRangeSlice>): Segment? {
        val ordered = slices.sortedBy { it.order }
        if (ordered.size < 2) return null
        val pieces = ordered.map { _uiState.value.segments.firstOrNull { seg -> seg.id == it.segmentId } ?: return null }
        val mergeable = pieces.isContiguousMergeableRun(allowDuplicates = true)

        // 범위 끝 세그먼트만 그 지점에서 split → 복제본이 들어갈 자리(끝 조각 뒤) 생성. 시작·중간은 보존.
        val lastSlice = ordered.last()
        val lastMid = runCatching { splitSegment(lastSlice.segmentId, lastSlice.localStart, lastSlice.localEnd) }
            .getOrNull()?.middle ?: return null
        refreshSegmentsStateFromDb()
        val insertionOrder = (_uiState.value.segments.firstOrNull { it.id == lastMid.id }?.order
            ?: return null) + 1

        val copies: List<Segment> = if (mergeable) {
            listOf(
                pieces.first().copy(
                    id = generateId(),
                    order = insertionOrder,
                    trimStartMs = ordered.first().localStart,
                    trimEndMs = ordered.last().localEnd,
                    duplicatedFromId = pieces.first().id,
                )
            )
        } else {
            ordered.mapIndexed { i, s ->
                pieces[i].copy(
                    id = generateId(),
                    order = insertionOrder + i,
                    trimStartMs = s.localStart,
                    trimEndMs = s.localEnd,
                    duplicatedFromId = s.segmentId,
                )
            }
        }

        segmentRepository.getByProjectId(projectId)
            .filter { it.order >= insertionOrder }
            .sortedByDescending { it.order }
            .forEach { segmentRepository.updateSegment(it.copy(order = it.order + copies.size)) }
        copies.forEach { segmentRepository.addSegment(it) }

        // directive 따라가기 — 범위 내 세그먼트에 앵커된 directive(범위 source 구간에 든 것)를 복제본에 clone.
        val allDirectives = separationDirectiveRepository.getByProject(projectId)
        val clones = if (copies.size == 1) {
            val dup = copies.first()
            val ids = pieces.map { it.id }.toSet()
            allDirectives.filter {
                it.segmentId in ids &&
                    it.localStartMs >= dup.trimStartMs && it.localEndMs <= dup.effectiveTrimEndMs
            }.map { it.cloneForSegment(dup.id) }
        } else {
            ordered.flatMapIndexed { i, s ->
                allDirectives.filter {
                    it.segmentId == s.segmentId && it.localStartMs >= s.localStart && it.localEndMs <= s.localEnd
                }.map { it.cloneForSegment(copies[i].id) }
            }
        }
        if (clones.isNotEmpty()) separationDirectiveRepository.addAll(clones)
        return copies.first()
    }

    fun onDeleteRange() {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        if (runBgmClipLocal { c, s, e -> applyBgmClipRangeDelete(c, s, e) }) return
        val applyToVideo = state.editTargets.hasVideo()
        val applyToBgm = state.editTargets.hasBgm()
        val slices = if (applyToVideo) sliceGlobalRange(start, end).sortedByDescending { it.order } else emptyList()
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            slices.forEach { s -> removeSegmentRange(s.segmentId, s.localStart, s.localEnd) }
            if (applyToBgm) applyBgmRippleDelete(start, end)
            if (applyToVideo) {
                // directive ripple — 구간을 관통하는 directive 는 두 조각으로 split (사용자: "구간1-1, 구간1-2"),
                // 완전 포함은 삭제, 부분 겹침은 truncate, 구간 뒤는 width 만큼 left shift.
                applyDirectiveRippleDelete(start, end)
            }
            refreshSegmentsStateFromDb()
            // delete 는 글로벌 ms 를 직접 옮기는(global-truth) 연산 — 앵커를 새 글로벌로 재동기화해
            // 이후 복제/이동의 reanchor 가 stale 앵커로 clobber 하지 않게 한다.
            if (applyToVideo) {
                // 삭제된 세그먼트(분리 구간 포함)에 앵커된 directive 를 먼저 정리 — 남으면 SoundDeck 카드가
                // 안 사라짐. 글로벌 ms ripple 의 stale 캐시 의존을 앵커 기반 purge 로 보강.
                purgeOrphanedDirectives()
                resyncDirectiveAnchorsFromGlobal()
            }
            if (wasSegmentEdit) {
                _uiState.value.segments.firstOrNull { it.type == SegmentType.VIDEO }
                    ?.id?.let { selectSegmentInEditInternal(it) }
            }
            pushUndoState()
        }
    }

    fun onApplyRangeVolume(value: Float) {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        if (runBgmClipLocal { c, s, e -> applyBgmClipRangeVolume(c, s, e, value) }) return
        val applyToVideo = state.editTargets.hasVideo()
        val applyToBgm = state.editTargets.hasBgm()
        val slices = if (applyToVideo) sliceGlobalRange(start, end).sortedByDescending { it.order } else emptyList()
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            var lastMiddleId: String? = null
            slices.forEach { s ->
                val r = splitSegment(s.segmentId, s.localStart, s.localEnd)
                updateSegmentVolume(r.middle.id, value)
                lastMiddleId = r.middle.id
            }
            if (applyToBgm) applyBgmRangeVolume(start, end, value)
            // volume 은 timeline 길이를 안 바꿔 directive 글로벌 range ripple 은 불필요하나, split 이
            // directive 의 segmentId 를 새 조각으로 재앵커(행 변경)할 수 있어 undo 스냅샷 전 동기 반영.
            refreshSegmentsStateFromDb()
            if (applyToVideo) refreshDirectivesStateFromDb()
            if (wasSegmentEdit) {
                lastMiddleId?.let { selectSegmentInEditInternal(it) }
            }
            pushUndoState()
        }
    }

    fun onApplyRangeSpeed(value: Float) {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        if (runBgmClipLocal { c, s, e -> applyBgmClipRangeSpeed(c, s, e, value) }) return
        val applyToVideo = state.editTargets.hasVideo()
        val applyToBgm = state.editTargets.hasBgm()
        val slices = if (applyToVideo) sliceGlobalRange(start, end).sortedByDescending { it.order } else emptyList()
        val wasSegmentEdit = state.isSegmentEditMode
        val newSpeed = if (value > 0f) value else 1f
        // directive ripple delta = (새 effective 합 - 옛 effective 합). 각 slice 의 source 구간 길이는
        // speed 변경 전·후 글로벌 길이 차이가 곧 timeline 이동량.
        val segById = state.segments.associateBy { it.id }
        val rippleDelta = slices.sumOf { s ->
            val oldSpeed = segById[s.segmentId]?.speedScale?.takeIf { it > 0f } ?: 1f
            val sourceDur = (s.localEnd - s.localStart).coerceAtLeast(0L)
            val oldGlobal = (sourceDur.toFloat() / oldSpeed).toLong()
            val newGlobal = (sourceDur.toFloat() / newSpeed).toLong()
            newGlobal - oldGlobal
        }
        resetRangeMode()
        viewModelScope.launch {
            var lastMiddleId: String? = null
            slices.forEach { s ->
                // 사용자 직관: "2x speed → 그 구간 글로벌 길이 절반으로 줄어듦". 선택 구간을
                // 그대로 split 한 뒤 middle 에만 speed 적용 → middle.global = source / newSpeed.
                // (이전: source 를 newSpeed/curSpeed 배 확장해 글로벌 길이 보존했지만 사용자
                // 호소 — 2배 올리면 길이가 늘어나 보임 — 와 반대 방향. 직관 우선.)
                val r = splitSegment(s.segmentId, s.localStart, s.localEnd)
                updateSegmentSpeed(r.middle.id, newSpeed)
                lastMiddleId = r.middle.id
            }
            if (applyToBgm) applyBgmRangeSpeed(start, end, newSpeed)
            if (applyToVideo) {
                // directive 정리 — speed 와 겹치는 directive 는 stem tempo mismatch 로 sync 깨짐 →
                // 삭제 후 재분리 유도. delete 가 먼저, shift 는 살아남은 downstream directive 만 처리.
                applyDirectiveDeleteOverlapping(start, end)
                applyDirectiveShiftAfter(after = end, deltaMs = rippleDelta)
            }
            refreshSegmentsStateFromDb()
            // speed 도 글로벌 ms ripple(shiftAfter)로 range 를 옮기는 global-truth 연산 — 앵커 재동기화.
            if (applyToVideo) resyncDirectiveAnchorsFromGlobal()
            if (wasSegmentEdit) {
                lastMiddleId?.let { selectSegmentInEditInternal(it) }
            }
            pushUndoState()
        }
    }

    // ── BGM 클립별 다듬기 액션 — SoundCard in-card expansion 패널 전용 ─────────────────
    // 다듬기 모드(isSegmentEditMode/pendingRange) 를 거치지 않고 한 클립에 직접 적용. 기존 private
    // [applyBgmRangeSpeed] 헬퍼를 그 클립의 bounds 로 호출해 ripple 정책(뒤따르는 BGM 클립
    // startMs shift) 도 영상 다듬기와 동일하게 적용. 볼륨은 ripple 없이 그 클립만 갱신하므로
    // 기존 [onUpdateBgmVolume] 재사용. 배경음 제거는 기존 [onStartBgmSeparation] (분리 sheet 진입).

    fun onApplyBgmClipSpeed(clipId: String, value: Float) {
        val bgm = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return
        val start = bgm.startMs
        val end = bgm.startMs + bgm.effectiveDurationMs
        val newSpeed = if (value > 0f) value else 1f
        viewModelScope.launch {
            applyBgmRangeSpeed(start, end, newSpeed)
            pushUndoState()
        }
    }

    private fun resetRangeMode() {
        // segment edit mode 에서는 액션(복제/삭제/볼륨/속도) 후에도 모드 유지 — "저장" 만 종료 트리거.
        if (_uiState.value.isSegmentEditMode) return
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
            selectedSegmentId = null
        )
    }

    // ── BGM range-action helpers ────────────────────────────────────────────
    // 영상 구간 액션(삭제·볼륨·속도·복제)을 BGM 에도 동시 적용하기 위한 헬퍼.
    // BgmClip 의 timeline 상 범위 = [startMs, startMs + effectiveDurationMs] (speedScale 반영).
    // sourceDurationMs 는 source media ms — `(timelineDelta * speedScale).toLong()` 로 환산.
    //
    // 모델 한계: BgmClip 에 sourceOffsetMs 가 없어 부분 split 시 둘째 조각의 source 시작점은
    // 항상 0 이라 사용자에게는 BGM 의 앞부분이 잘려 들리는 모양새. 정확한 split 은 후속 마이그
    // 레이션(BgmClip + Room) 으로.

    /**
     * 영상 구간 [start, end] 삭제 시 BGM ripple delete.
     *  - be ≤ start: no-op
     *  - 완전 포함: 삭제
     *  - 관통(bs < start, be > end): split — 첫 조각 길이 (start-bs), 둘째 새 클립 startMs=start
     *  - 뒤쪽 잘림(bs < start, start < be ≤ end): sourceDurationMs 축소
     *  - 앞쪽 잘림(start ≤ bs < end, be > end): startMs=start + sourceDurationMs 축소
     *  - 구간 뒤(bs ≥ end): startMs -= (end-start)
     */
    private suspend fun applyBgmRippleDelete(start: Long, end: Long) {
        val width = end - start
        if (width <= 0L) return
        val snapshot = _uiState.value.bgmClips.toList()
        for (bgm in snapshot) {
            val bs = bgm.startMs
            val be = bs + bgm.effectiveDurationMs
            val speed = bgm.speedScale.coerceAtLeast(0.01f)
            when {
                be <= start -> {}
                bs >= start && be <= end -> bgmClipRepository.deleteClip(bgm.id)
                bs >= end -> bgmClipRepository.updateClip(
                    bgm.copy(startMs = (bs - width).coerceAtLeast(0L))
                )
                bs < start && be > end -> {
                    val firstSourceDur = ((start - bs).toFloat() * speed).toLong().coerceAtLeast(1L)
                    bgmClipRepository.updateClip(bgm.copy(sourceDurationMs = firstSourceDur))
                    val secondSourceDur = ((be - end).toFloat() * speed).toLong().coerceAtLeast(1L)
                    bgmClipRepository.addClip(
                        BgmClip(
                            id = generateId(),
                            projectId = bgm.projectId,
                            sourceUri = bgm.sourceUri,
                            sourceDurationMs = secondSourceDur,
                            startMs = start,
                            volumeScale = bgm.volumeScale,
                            speedScale = bgm.speedScale,
                            lane = bgm.lane,
                            createdAt = currentTimeMillis(),
                            customName = bgm.customName,
                        )
                    )
                }
                bs < start && be > start && be <= end -> {
                    val newSourceDur = ((start - bs).toFloat() * speed).toLong().coerceAtLeast(1L)
                    bgmClipRepository.updateClip(bgm.copy(sourceDurationMs = newSourceDur))
                }
                bs >= start && bs < end && be > end -> {
                    val newSourceDur = ((be - end).toFloat() * speed).toLong().coerceAtLeast(1L)
                    bgmClipRepository.updateClip(
                        bgm.copy(sourceDurationMs = newSourceDur, startMs = start)
                    )
                }
            }
        }
    }

    /** 구간에 **완전히 포함**된 BGM 의 volumeScale 만 갱신. 부분 겹침은 보존. */
    private suspend fun applyBgmRangeVolume(start: Long, end: Long, value: Float) {
        val v = value.coerceIn(BgmClip.MIN_VOLUME, BgmClip.MAX_VOLUME)
        val snapshot = _uiState.value.bgmClips.toList()
        for (bgm in snapshot) {
            val bs = bgm.startMs
            val be = bs + bgm.effectiveDurationMs
            if (bs >= start && be <= end) {
                bgmClipRepository.updateClip(bgm.copy(volumeScale = v))
            }
        }
    }

    /**
     * 구간에 **완전히 포함**된 BGM 의 speedScale 갱신 + 변화한 effectiveDurationMs 차이만큼
     * 그 뒤 BGM 들 startMs ripple shift. 부분 겹침 BGM 은 보존 (ripple 도 안 적용 — 위치 정합성
     * 어차피 깨질 case).
     */
    private suspend fun applyBgmRangeSpeed(start: Long, end: Long, value: Float) {
        val newSpeed = value.coerceIn(BgmClip.MIN_SPEED, BgmClip.MAX_SPEED)
        val snapshot = _uiState.value.bgmClips.toList().sortedBy { it.startMs }
        var rippleDelta = 0L
        for (bgm in snapshot) {
            val bs = bgm.startMs
            val be = bs + bgm.effectiveDurationMs
            when {
                bs >= start && be <= end -> {
                    // trim 적용된 effective source 길이 기반으로 새 effective 계산 — 단순 sourceDurationMs
                    // 쓰면 trim 된 BGM 의 새 speed 후 길이가 잘못 커져 ripple delta 가 어긋남.
                    val effSource = bgm.effectiveSourceDurationMs
                    val newEffective = if (newSpeed > 0f) (effSource / newSpeed).toLong() else effSource
                    val itemDelta = newEffective - bgm.effectiveDurationMs
                    bgmClipRepository.updateClip(
                        bgm.copy(
                            speedScale = newSpeed,
                            startMs = (bs + rippleDelta).coerceAtLeast(0L),
                        )
                    )
                    rippleDelta += itemDelta
                }
                bs >= end -> bgmClipRepository.updateClip(
                    bgm.copy(startMs = (bs + rippleDelta).coerceAtLeast(0L))
                )
                else -> {} // 앞쪽 + 부분 겹침 보존
            }
        }
    }

    /**
     * 구간에 **완전히 포함**된 BGM 을 복제해 구간 뒤(startMs + width)에 삽입.
     * 그 외 구간 뒤 BGM 들은 startMs += width (ripple insert).
     */
    private suspend fun applyBgmRangeDuplicate(start: Long, end: Long) {
        val width = end - start
        if (width <= 0L) return
        val snapshot = _uiState.value.bgmClips.toList()
        for (bgm in snapshot) {
            if (bgm.startMs >= end) {
                bgmClipRepository.updateClip(bgm.copy(startMs = bgm.startMs + width))
            }
        }
        for (bgm in snapshot) {
            val be = bgm.startMs + bgm.effectiveDurationMs
            if (bgm.startMs >= start && be <= end) {
                bgmClipRepository.addClip(
                    BgmClip(
                        id = generateId(),
                        projectId = bgm.projectId,
                        sourceUri = bgm.sourceUri,
                        sourceDurationMs = bgm.sourceDurationMs,
                        startMs = bgm.startMs + width,
                        volumeScale = bgm.volumeScale,
                        speedScale = bgm.speedScale,
                        sourceTrimStartMs = bgm.sourceTrimStartMs,
                        sourceTrimEndMs = bgm.sourceTrimEndMs,
                        lane = bgm.lane,
                        createdAt = currentTimeMillis(),
                        customName = bgm.customName,
                    )
                )
            }
        }
    }

    // ── BGM 클립 구간편집 (영상 다듬기와 동일 UX) — clip-local split ──────────────────
    // 위 applyBgmRange* / applyBgmRippleDelete 는 "영상 구간 편집에 딸려가는" BGM 처리라 BGM 레인
    // 전체를 한 타임라인으로 보고 하위 클립을 ripple shift 한다. 아래 헬퍼는 사용자가 BGM 클립 하나를
    // 탭해 그 안의 서브-구간만 편집하는 신규 흐름 전용 — **편집한 클립의 조각만** 손대고 다른 클립은
    // 절대 안 옮긴다(단일 클립 에디터). 분할은 sourceTrimStart/End 경계만 새로 잡아 표현(컬럼 추가 불필요).

    private data class BgmSplit(val before: BgmClip?, val middle: BgmClip, val after: BgmClip?)

    /** 적용 후 연속 편집(chain) 을 위한 재타깃 정보. clipId=null 이면 모드 종료. */
    private data class BgmEditFocus(
        val clipId: String?,
        val start: Long,
        val end: Long,
        val volume: Float = 1f,
        val speed: Float = 1f,
    )

    /**
     * 현재 상태가 "BGM 단일 클립 구간편집"(영상 다듬기 아님 + editTargets=Bgm(clipId)) 이면 그 클립 반환.
     * 4개 range 액션이 이 분기로 clip-local 헬퍼를 탄다. 영상-동기 BGM 처리(applyToBgm)와 구분.
     */
    private fun bgmClipLocalTarget(state: TimelineUiState): BgmClip? {
        if (state.isSegmentEditMode) return null
        val clipId = (state.editTargets.firstOrNull { it is EditTarget.Bgm } as? EditTarget.Bgm)?.clipId
            ?: return null
        return state.bgmClips.firstOrNull { it.id == clipId }
    }

    /** 적용 후 재타깃 — flow 재조회 없이 split 계산값으로 직접 set(연속 편집). clipId=null=모드 종료. */
    private fun applyBgmEditFocus(focus: BgmEditFocus) {
        val state = _uiState.value
        _uiState.value = if (focus.clipId == null) state.copy(
            isRangeSelecting = false,
            editTargets = setOf(EditTarget.Video),
            selectedBgmClipId = null,
            rangeTargetSegmentId = null,
            pendingRangeStartMs = 0L,
            pendingRangeEndMs = 0L,
        ) else state.copy(
            isRangeSelecting = true,
            isSegmentEditMode = false,
            editTargets = setOf(EditTarget.Bgm(focus.clipId)),
            selectedBgmClipId = focus.clipId,
            pendingRangeStartMs = focus.start,
            pendingRangeEndMs = focus.end,
            pendingRangeVolume = focus.volume,
            pendingRangeSpeed = focus.speed,
        )
    }

    /**
     * 글로벌 ms 경계 [globalStart, globalEnd] 로 클립을 before/middle/after 로 분할.
     * - source-offset 은 sourceTrimStartMs 가 대신함 — 둘째/셋째 조각도 source 의 올바른 지점부터 재생.
     * - 원본 id 를 재사용하는 조각(before 있으면 before, 없으면 middle)은 createdAt 유지 → 색/번호 안정.
     * - 신규 조각은 새 id/createdAt + 원본 lane override 등록(R1: lane 은 DB 영속 아님).
     * - MIN_FRAGMENT_MS 미만 before/after 는 middle 에 흡수(미세 ghost 클립 방지).
     */
    private fun splitBgmClip(clip: BgmClip, globalStart: Long, globalEnd: Long): BgmSplit {
        val speed = clip.speedScale.coerceAtLeast(0.01f)
        val bs = clip.startMs
        val be = bs + clip.effectiveDurationMs
        val srcTrimStart = clip.sourceTrimStartMs
        val srcTrimEnd = if (clip.sourceTrimEndMs > 0L) clip.sourceTrimEndMs else clip.sourceDurationMs
        val cutStart = globalStart.coerceIn(bs, be)
        val cutEnd = globalEnd.coerceIn(bs, be)
        var srcCutStart = (srcTrimStart + ((cutStart - bs) * speed).roundToLong())
            .coerceIn(srcTrimStart, srcTrimEnd)
        var srcCutEnd = (srcTrimStart + ((cutEnd - bs) * speed).roundToLong())
            .coerceIn(srcCutStart, srcTrimEnd)
        // snap-to-edge: 미세 before/after 흡수
        if (srcCutStart - srcTrimStart in 1 until SplitSegmentUseCase.MIN_FRAGMENT_MS) srcCutStart = srcTrimStart
        if (srcTrimEnd - srcCutEnd in 1 until SplitSegmentUseCase.MIN_FRAGMENT_MS) srcCutEnd = srcTrimEnd

        val hasBefore = srcCutStart > srcTrimStart
        val hasAfter = srcCutEnd < srcTrimEnd
        // global startMs: before 는 bs, middle 은 (before 있으면 cutStart, 없으면 bs), after 는 cutEnd
        val before = if (hasBefore) clip.copy(
            // 원본 id 재사용 — createdAt/색/번호 유지
            sourceTrimStartMs = srcTrimStart,
            sourceTrimEndMs = srcCutStart,
            startMs = bs,
        ) else null
        val middle = clip.copy(
            id = if (hasBefore) generateId() else clip.id,
            sourceTrimStartMs = srcCutStart,
            sourceTrimEndMs = srcCutEnd,
            startMs = if (hasBefore) cutStart else bs,
            createdAt = if (hasBefore) currentTimeMillis() else clip.createdAt,
        )
        val after = if (hasAfter) clip.copy(
            id = generateId(),
            sourceTrimStartMs = srcCutEnd,
            sourceTrimEndMs = srcTrimEnd,
            startMs = cutEnd,
            createdAt = currentTimeMillis(),
        ) else null
        // 신규 id 는 lane override 등록 — 안 그러면 DB 저장 lane(보통 0)으로 튐
        listOfNotNull(before, after).forEach { if (it.id != clip.id) bgmClipLaneOverrides[it.id] = clip.lane }
        if (middle.id != clip.id) bgmClipLaneOverrides[middle.id] = clip.lane
        return BgmSplit(before = before, middle = middle, after = after)
    }

    /** 선택 구간만 볼륨 적용 — 분할 후 middle 만 갱신. 길이 불변 → ripple 없음. */
    private suspend fun applyBgmClipRangeVolume(clip: BgmClip, start: Long, end: Long, value: Float): BgmEditFocus {
        val v = value.coerceIn(BgmClip.MIN_VOLUME, BgmClip.MAX_VOLUME)
        val s = splitBgmClip(clip, start, end)
        s.before?.let { bgmClipRepository.updateClip(it) } // before 는 원본 id 재사용 → update
        bgmClipRepository.let { repo ->
            if (s.before == null) repo.updateClip(s.middle.copy(volumeScale = v)) // middle=원본 id
            else repo.addClip(s.middle.copy(volumeScale = v))                     // middle=신규 id
        }
        s.after?.let { bgmClipRepository.addClip(it) }
        return BgmEditFocus(s.middle.id, s.middle.startMs, s.middle.startMs + s.middle.effectiveDurationMs, v, s.middle.speedScale)
    }

    /** 선택 구간만 속도 적용 — middle 만 speed 변경, 그 클립의 after 만 재앵커(다른 클립 미이동). */
    private suspend fun applyBgmClipRangeSpeed(clip: BgmClip, start: Long, end: Long, value: Float): BgmEditFocus {
        val newSpeed = value.coerceIn(BgmClip.MIN_SPEED, BgmClip.MAX_SPEED)
        val s = splitBgmClip(clip, start, end)
        val newMiddle = s.middle.copy(speedScale = newSpeed)
        s.before?.let { bgmClipRepository.updateClip(it) }
        if (s.before == null) bgmClipRepository.updateClip(newMiddle) else bgmClipRepository.addClip(newMiddle)
        // after 를 새 middle 끝으로 재앵커 (clip-local: 형제 클립은 안 옮김)
        s.after?.let { bgmClipRepository.addClip(it.copy(startMs = newMiddle.startMs + newMiddle.effectiveDurationMs)) }
        return BgmEditFocus(newMiddle.id, newMiddle.startMs, newMiddle.startMs + newMiddle.effectiveDurationMs, newMiddle.volumeScale, newSpeed)
    }

    /** 선택 구간만 삭제 — middle 제거 + after 를 앞으로 당겨 갭 닫기(clip-local). 전체 삭제면 모드 종료. */
    private suspend fun applyBgmClipRangeDelete(clip: BgmClip, start: Long, end: Long): BgmEditFocus {
        val s = splitBgmClip(clip, start, end)
        // 범위==클립 전체: before/after 없음 → 클립 통째 삭제, 모드 종료
        if (s.before == null && s.after == null) {
            bgmClipRepository.deleteClip(clip.id)
            return BgmEditFocus(null, 0L, 0L)
        }
        // middle 제거: 원본 id 면 delete, 신규 id 면 애초에 add 안 함
        if (s.middle.id == clip.id) bgmClipRepository.deleteClip(clip.id)
        s.before?.let { bgmClipRepository.updateClip(it) }
        // after 를 before 끝(없으면 bs)으로 당김
        val gapStart = s.before?.let { it.startMs + it.effectiveDurationMs } ?: clip.startMs
        val survivor = s.after?.copy(startMs = gapStart) ?: s.before!!
        s.after?.let { bgmClipRepository.addClip(survivor) }
        return BgmEditFocus(survivor.id, survivor.startMs, survivor.startMs + survivor.effectiveDurationMs, survivor.volumeScale, survivor.speedScale)
    }

    /** 선택 구간만 복제 — middle 복제본을 middle 바로 뒤에 삽입 + 그 클립 after 만 오른쪽 shift(clip-local). */
    private suspend fun applyBgmClipRangeDuplicate(clip: BgmClip, start: Long, end: Long): BgmEditFocus {
        val s = splitBgmClip(clip, start, end)
        s.before?.let { bgmClipRepository.updateClip(it) }
        // middle 을 자기 윈도우로 영속 — before 있으면 신규 id(add), 없으면 원본 id(update)
        if (s.before == null) bgmClipRepository.updateClip(s.middle) else bgmClipRepository.addClip(s.middle)
        val middleEff = s.middle.effectiveDurationMs
        val dupStart = s.middle.startMs + middleEff
        bgmClipRepository.addClip(
            s.middle.copy(id = generateId(), startMs = dupStart, createdAt = currentTimeMillis())
                .also { bgmClipLaneOverrides[it.id] = clip.lane }
        )
        // dup 이 차지한 middleEff 만큼 이 클립의 after 만 오른쪽으로 (형제 클립 미이동)
        s.after?.let { bgmClipRepository.addClip(it.copy(startMs = it.startMs + middleEff)) }
        return BgmEditFocus(s.middle.id, s.middle.startMs, s.middle.startMs + middleEff, s.middle.volumeScale, s.middle.speedScale)
    }

    /**
     * 영상 range delete [start, end] 를 separation directive 들에 ripple — split/truncate/shift 로
     * directive 가 video edit 으로 stale 되지 않게 유지. 같은 stem audio URL 을 공유한 채
     * `sourceOffsetMs` 만 누적해, 잘려나간 뒤쪽 piece 가 stem audio 의 중간부터 재생되게 한다.
     *
     * BFF render 는 아직 sourceOffsetMs 를 무시 — export 시 split 뒤쪽 piece 가 stem 처음부터 재생됨.
     * 본 변경은 mobile preview (stem mixer) 에만 즉시 반영. BFF 업데이트는 follow-up.
     */
    private suspend fun applyDirectiveRippleDelete(start: Long, end: Long) {
        val width = end - start
        if (width <= 0L) return
        val upserts = mutableListOf<SeparationDirective>()
        val deletes = mutableListOf<String>()
        // 관통(case 4) 의 앞 piece 와 부분-겹침(case 5) 가 둘 다 `dir.copy(rangeEndMs = start)` 형태로
        // 동일한 의미(=뒤쪽 잘림)라 같은 분기로 통합. 마찬가지로 관통 뒤 piece 와 case 6 (앞쪽 잘림)
        // 모두 `dir.copy(rangeStartMs=start, rangeEndMs=de-width, sourceOffsetMs += (end-ds))`.
        for (dir in _uiState.value.separationDirectives) {
            val ds = dir.rangeStartMs
            val de = dir.rangeEndMs
            when {
                de <= start -> {}
                ds >= end -> upserts += dir.copy(
                    rangeStartMs = (ds - width).coerceAtLeast(0L),
                    rangeEndMs = (de - width).coerceAtLeast(0L),
                )
                ds >= start && de <= end -> deletes += dir.id
                ds < start && de > end -> {
                    upserts += dir.copy(rangeEndMs = start)
                    upserts += dir.copy(
                        id = generateId(),
                        rangeStartMs = start,
                        rangeEndMs = de - width,
                        sourceOffsetMs = dir.sourceOffsetMs + (end - ds),
                    )
                }
                ds < start && de <= end -> upserts += dir.copy(rangeEndMs = start)
                ds >= start && de > end -> upserts += dir.copy(
                    rangeStartMs = start,
                    rangeEndMs = de - width,
                    sourceOffsetMs = dir.sourceOffsetMs + (end - ds),
                )
            }
        }
        deletes.forEach { separationDirectiveRepository.delete(it) }
        separationDirectiveRepository.addAll(upserts)
    }

    /**
     * `after` 시점 이후에 시작하는 directive 들의 rangeStart/End 를 +deltaMs 평행 이동.
     * duplicate (delta = +width) / speed (delta = newEffDur - oldEffDur) downstream ripple 공용.
     */
    private suspend fun applyDirectiveShiftAfter(after: Long, deltaMs: Long) {
        if (deltaMs == 0L) return
        val shifted = _uiState.value.separationDirectives
            .filter { it.rangeStartMs >= after }
            .map { it.copy(
                rangeStartMs = (it.rangeStartMs + deltaMs).coerceAtLeast(0L),
                rangeEndMs = (it.rangeEndMs + deltaMs).coerceAtLeast(0L),
            ) }
        separationDirectiveRepository.addAll(shifted)
    }

    /**
     * 앵커된 directive 의 글로벌 range 캐시를 현재 세그먼트 배치로 재계산(앵커→글로벌). 복제/이동처럼
     * 세그먼트 위치만 바뀌고 directive 의 source-local 좌표는 그대로인 연산 직후 호출 — directive 가
     * 새 위치를 자동으로 따라간다(per-operation 글로벌 ripple 대체). repo 가 권위 원본.
     */
    /**
     * 앵커 세그먼트가 더 이상 존재하지 않는 directive 를 삭제한다. 분리 구간을 포함해 영상을 삭제하면
     * `removeSegmentRange` 가 그 구간 세그먼트를 지우지만 거기 앵커된 directive 는 남아(고아) SoundDeck
     * 카드가 사라지지 않던 문제를 막는다. 비앵커(legacy, segmentId 빈 값) directive 는 글로벌 range 로만
     * 사는 것이라 건드리지 않는다. 호출 전 [refreshSegmentsStateFromDb] 로 세그먼트가 최신이어야 한다.
     */
    private suspend fun purgeOrphanedDirectives() {
        val segIds = _uiState.value.segments.map { it.id }.toSet()
        separationDirectiveRepository.getByProject(projectId)
            .filter { it.segmentId.isNotEmpty() && it.segmentId !in segIds }
            .forEach { separationDirectiveRepository.delete(it.id) }
    }

    private suspend fun reanchorDirectiveCache() {
        val updates = DirectiveAnchor.reanchor(
            separationDirectiveRepository.getByProject(projectId),
            _uiState.value.segments,
        )
        if (updates.isNotEmpty()) separationDirectiveRepository.addAll(updates)
        // use case 가 쓴 clone + 위 reanchor 결과를 undo 스냅샷 전에 _uiState 로 동기 반영.
        refreshDirectivesStateFromDb()
    }

    /**
     * 글로벌 range 를 진실로 보고 앵커(segmentId + local)를 재계산(글로벌→앵커). delete/speed 처럼 기존
     * 글로벌 ms ripple 로 range 를 직접 옮긴 연산 직후 호출해 앵커를 일관되게 맞춘다 — 이후 복제/이동의
     * [reanchorDirectiveCache] 가 stale 앵커로 글로벌을 덮어쓰는(clobber) 사고를 막는다.
     */
    private suspend fun resyncDirectiveAnchorsFromGlobal() {
        val updates = DirectiveAnchor.resyncAnchors(
            separationDirectiveRepository.getByProject(projectId),
            _uiState.value.segments,
        )
        if (updates.isNotEmpty()) separationDirectiveRepository.addAll(updates)
        // 글로벌 ms ripple 결과 + 위 anchor 재동기화를 undo 스냅샷 전에 _uiState 로 동기 반영.
        refreshDirectivesStateFromDb()
    }

    /**
     * 구간 `[start, end]` 와 조금이라도 겹치는 directive 를 모두 삭제. speed 변경 시 stem audio 는
     * 원본 tempo 라 video 와 sync 가 깨지므로 보존 불가 — 사용자에게 재분리 유도가 가장 정직한 UX.
     * (split/scale 로 살리려면 directive 에 appliedSpeedScale 추가 + BFF atempo wiring 필요 — 별도
     * 작업으로 두고 본 단계에선 conservative delete.)
     */
    private suspend fun applyDirectiveDeleteOverlapping(start: Long, end: Long) {
        val toDelete = _uiState.value.separationDirectives
            .filter { it.rangeEndMs > start && it.rangeStartMs < end }
            .map { it.id }
        toDelete.forEach { separationDirectiveRepository.delete(it) }
    }

    /** segment edit mode 종료 ("저장" 버튼) — range/segment 상태를 모두 정리하고 기본 timeline 으로. */
    fun onFinishSegmentEdit() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
            selectedSegmentId = null,
        )
    }

    fun segmentStartMs(segmentId: String): Long =
        segmentStartOffsetMs(_uiState.value.segments, segmentId)

    fun currentSegmentAt(positionMs: Long): Segment? {
        val segments = _uiState.value.segments
        var acc = 0L
        for (seg in segments) {
            val next = acc + seg.effectiveDurationMs
            if (positionMs < next) return seg
            acc = next
        }
        return segments.lastOrNull()
    }

    fun onPickBgmAudio(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAddingBgm = true, bgmError = null)
            try {
                val info = audioMetadataExtractor.extract(uri)
                if (info == null || info.durationMs <= 0L) {
                    _uiState.value = _uiState.value.copy(
                        isAddingBgm = false,
                        bgmError = "Couldn't read audio metadata"
                    )
                    return@launch
                }
                val state = _uiState.value
                val playhead = state.playbackPositionMs
                // 영상보다 길면 사용자에게 구간 선택 시트 — 실제 삽입은 onConfirmBgmTrim 에서.
                // videoDurationMs == 0 (empty timeline) 인 경우엔 비교 불가라 그냥 통과 — addBgmClip 이
                // sourceDurationMs > 0 만 require.
                if (state.videoDurationMs > 0L && info.durationMs > state.videoDurationMs) {
                    _uiState.update {
                        it.copy(
                            isAddingBgm = false,
                            bgmTrimRequest = BgmTrimRequest(
                                sourceUri = uri,
                                sourceDurationMs = info.durationMs,
                                insertStartMs = playhead,
                                rangeStartMs = 0L,
                                rangeEndMs = state.videoDurationMs,
                            ),
                        )
                    }
                    return@launch
                }
                // 재생바 위치에 삽입하되, 재생바위치 + 음원길이 가 영상길이를 넘으면 넘는 만큼 앞으로 당겨
                // 끝에 맞춘다 (startMs = 영상길이 - 음원길이, 0 미만이면 0). 영상 길이 미상(0)이면 그대로 재생바.
                val startMs = if (state.videoDurationMs > 0L) {
                    playhead.coerceIn(0L, (state.videoDurationMs - info.durationMs).coerceAtLeast(0L))
                } else {
                    playhead
                }
                addBgmClip(
                    projectId = projectId,
                    sourceUri = uri,
                    sourceDurationMs = info.durationMs,
                    startMs = startMs,
                    volumeScale = 1.0f,
                )
                _uiState.value = _uiState.value.copy(isAddingBgm = false)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    isAddingBgm = false,
                    bgmError = "Failed to add BGM"
                )
            }
        }
    }

    /** BgmTrimSheet 의 시작/끝 핸들 drag 진행 시 호출. 음원 범위 안으로만 clamp — 구간이 영상 길이를
     *  넘는지 여부는 시트의 "삽입" 버튼 enable 판정에 위임 (한쪽 핸들 끌 때 다른 쪽이 따라가지 않도록). */
    fun onUpdateBgmTrimRange(rangeStartMs: Long, rangeEndMs: Long) {
        _uiState.update { state ->
            val req = state.bgmTrimRequest ?: return@update state
            val source = req.sourceDurationMs
            val clampedStart = rangeStartMs.coerceIn(0L, source)
            val clampedEnd = rangeEndMs.coerceIn(clampedStart, source)
            state.copy(
                bgmTrimRequest = req.copy(
                    rangeStartMs = clampedStart,
                    rangeEndMs = clampedEnd,
                )
            )
        }
    }

    /** BgmTrimSheet 의 "삽입" 클릭 시 호출. 선택 구간 ≤ 영상 길이일 때만 진행.
     *
     *  플랫폼 audio extractor 가 지원되면 선택 구간만큼 잘라낸 **새 m4a 파일**을 만들어 그 path
     *  를 BgmClip 의 sourceUri 로 박는다. iOS=AVAssetExportPresetAppleM4A sample-copy, Android=
     *  현재 stub (지원 안 함 → fallback). trim 결과 파일을 sourceUri 로 쓰면 BgmClip 의 sub-range
     *  offset 은 0 — render-time ffmpeg atrim 단계가 사라져 timeline preview/seek 도 단순화.
     *
     *  fallback (extractor unsupported / 실패): 기존처럼 원본 sourceUri + sourceTrim* offset 으로
     *  메타만 박아 BFF render 가 atrim 처리 — 즉시 삽입 보장이 더 중요해서 silent fallback.
     */
    fun onConfirmBgmTrim() {
        val state = _uiState.value
        val req = state.bgmTrimRequest ?: return
        val span = req.rangeEndMs - req.rangeStartMs
        if (span < MIN_RANGE_MS) return
        if (state.videoDurationMs > 0L && span > state.videoDurationMs) return
        // 삽입 위치 = 재생바(insertStartMs). 끝(영상 길이)을 넘으면 넘는 만큼 앞으로 당겨 끝에 맞춤.
        val insertAt = if (state.videoDurationMs > 0L) {
            req.insertStartMs.coerceIn(0L, (state.videoDurationMs - span).coerceAtLeast(0L))
        } else {
            req.insertStartMs
        }
        viewModelScope.launch {
            try {
                val prepared = if (audioExtractor.isSupported) {
                    runCatching {
                        audioExtractor.prepareSeparationAudio(
                            sourceUri = req.sourceUri,
                            sourceKind = AudioSourceKind.AUDIO_COMPATIBLE,
                            startMs = req.rangeStartMs,
                            endMs = req.rangeEndMs,
                        )
                    }.getOrNull()
                } else null

                if (prepared != null) {
                    addBgmClip(
                        projectId = projectId,
                        sourceUri = prepared.path,
                        sourceDurationMs = span,
                        startMs = insertAt,
                        volumeScale = 1.0f,
                    )
                } else {
                    addBgmClip(
                        projectId = projectId,
                        sourceUri = req.sourceUri,
                        sourceDurationMs = req.sourceDurationMs,
                        startMs = insertAt,
                        volumeScale = 1.0f,
                        sourceTrimStartMs = req.rangeStartMs,
                        sourceTrimEndMs = req.rangeEndMs,
                    )
                }
                _uiState.update { it.copy(bgmTrimRequest = null) }
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.update {
                    it.copy(bgmTrimRequest = null, bgmError = "Failed to add BGM")
                }
            }
        }
    }

    /** BgmTrimSheet 의 "취소" 또는 dismiss. BGM 미삽입. */
    fun onCancelBgmTrim() {
        _uiState.update { it.copy(bgmTrimRequest = null) }
    }

    fun onSelectBgmClip(clipId: String?) = selectExclusively(SelectionTarget.Bgm, clipId)

    // BGM 클립 위치 드래그 중 pendingRange(구간 핸들/fill)를 클립과 함께 라이브 이동시키기 위한 baseline.
    private var bgmDragClipBaseMs: Long? = null
    private var bgmDragRangeBase: Pair<Long, Long>? = null

    /**
     * BGM 클립 위치 드래그 **중**(라이브) 호출 — 그 클립이 구간편집 대상(BGM range)이면 pendingRange 를
     * 클립 이동량만큼 같이 옮겨 핸들/ fill 이 클립과 함께 움직이게 한다. DB 쓰기/undo 없음(시각만).
     * 커밋은 [onUpdateBgmStartMs] (드래그 종료).
     */
    fun onBgmDragLive(clipId: String, newStartMs: Long) {
        val state = _uiState.value
        if (state.editTargets.none { it is EditTarget.Bgm && it.clipId == clipId }) return
        if (bgmDragClipBaseMs == null) {
            bgmDragClipBaseMs = state.bgmClips.firstOrNull { it.id == clipId }?.startMs ?: return
            bgmDragRangeBase = state.pendingRangeStartMs to state.pendingRangeEndMs
        }
        val base = bgmDragClipBaseMs ?: return
        val (rs, re) = bgmDragRangeBase ?: return
        val delta = newStartMs.coerceAtLeast(0L) - base
        _uiState.update {
            it.copy(
                pendingRangeStartMs = (rs + delta).coerceAtLeast(0L),
                pendingRangeEndMs = (re + delta).coerceAtLeast(0L),
            )
        }
    }

    fun onUpdateBgmStartMs(clipId: String, newStartMs: Long) {
        // 라이브 드래그 baseline 초기화 — pendingRange 는 라이브에서 이미 동반 이동됨(커밋 시 재이동 안 함).
        bgmDragClipBaseMs = null
        bgmDragRangeBase = null
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, startMs = newStartMs.coerceAtLeast(0L))
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    /**
     * 타임라인 위에서 BGM 클립 좌·우 엣지를 드래그해 trim. start 핸들 드래그는 [newStartMs] 도 동반
     * 갱신 (CapCut 의미상 좌측 잘라낼 때 timeline 좌측 엣지는 손가락 위치에 머무름). end 핸들 드래그는
     * [newStartMs] 가 null — startMs 불변. trim range 유효성은 use case 가 검증 + clamp.
     */
    fun onUpdateBgmTrim(
        clipId: String,
        newSourceTrimStartMs: Long,
        newSourceTrimEndMs: Long,
        newStartMs: Long?,
    ) {
        viewModelScope.launch {
            try {
                updateBgmClip(
                    clipId = clipId,
                    startMs = newStartMs?.coerceAtLeast(0L),
                    sourceTrimStartMs = newSourceTrimStartMs.coerceAtLeast(0L),
                    sourceTrimEndMs = newSourceTrimEndMs.coerceAtLeast(0L),
                )
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    /**
     * BGM volume/speed 슬라이더는 드래그 중 60Hz 가까이 호출 — pushUndoState 를 매 호출에 두면
     * undo 스택이 한 번의 드래그로 수십 entry 폭주, 사용자가 그 만큼 ↶ 눌러야 원위치. 본 함수는
     * DB write 만 수행하고, undo snapshot 은 호출자가 드래그 종료 시점 ([commitBgmEditUndo]) 또는
     * 1-shot 액션 직후에 명시 호출.
     */
    fun onUpdateBgmSpeed(clipId: String, newSpeed: Float) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, speedScale = newSpeed)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    fun onUpdateBgmVolume(clipId: String, newVolume: Float) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, volumeScale = newVolume)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    /** 드래그 종료(Slider onValueChangeFinished) 또는 1-shot mute 토글 직후 호출. undo entry 1 개 push. */
    fun commitBgmEditUndo() {
        pushUndoState()
    }

    /**
     * 음원·녹음 카드 이름 변경 (카드 연필 탭). blank 면 customName=null 로 되돌려 자동 라벨
     * (파일명 / "Recording N") 복귀. 길이 상한으로 비정상 입력 차단. undo entry 1 개 push.
     */
    fun onRenameBgmClip(clipId: String, newName: String) {
        viewModelScope.launch {
            val clip = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return@launch
            val sanitized = newName.trim().take(MAX_DISPLAY_NAME_LEN).takeIf { it.isNotBlank() }
            if (sanitized == clip.customName) return@launch
            bgmClipRepository.updateClip(clip.copy(customName = sanitized))
            pushUndoState()
        }
    }

    /**
     * 프로젝트 제목 변경 (에디터 헤더 제목 탭). blank 면 title=null 로 되돌려 목록 카드가 createdAt
     * 타임스탬프로 fallback. touchActivity=true 로 최근 편집 순 정렬 갱신. observeProject 가
     * projectTitle 을 다시 hydrate 하므로 별도 _uiState write 불필요.
     */
    fun onRenameProject(newTitle: String) {
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            val sanitized = newTitle.trim().take(MAX_DISPLAY_NAME_LEN).takeIf { it.isNotBlank() }
            if (sanitized == project.title) return@launch
            editProjectRepository.updateProject(project.copy(title = sanitized), touchActivity = true)
        }
    }

    /**
     * BGM 단일 클립 복제 — 선택된 클립의 모든 속성(sourceUri/trim/volume/speed/lane + 캐시된
     * originalSourceUri/voiceOnlyUri) 을 그대로 복사하여 원본 끝(startMs + effectiveDuration) 에
     * 새 클립 추가. createdAt 은 새로(= 색·번호 새로) 부여, id 도 새로.
     */
    fun onDuplicateBgmClip(clipId: String) {
        viewModelScope.launch {
            val original = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return@launch
            val newStartMs = original.startMs + original.effectiveDurationMs
            bgmClipRepository.addClip(
                BgmClip(
                    id = generateId(),
                    projectId = original.projectId,
                    sourceUri = original.sourceUri,
                    sourceDurationMs = original.sourceDurationMs,
                    startMs = newStartMs,
                    volumeScale = original.volumeScale,
                    speedScale = original.speedScale,
                    sourceTrimStartMs = original.sourceTrimStartMs,
                    sourceTrimEndMs = original.sourceTrimEndMs,
                    lane = original.lane,
                    createdAt = currentTimeMillis(),
                    originalSourceUri = original.originalSourceUri,
                    voiceOnlyUri = original.voiceOnlyUri,
                    customName = original.customName,
                )
            )
            pushUndoState()
        }
    }

    /**
     * BGM "배경음 제거 ↔ 원래대로" 토글.
     *
     * - 현재 voice-only 활성 ([BgmClip.isBackgroundRemoved] == true) → sourceUri 를 originalSourceUri 로
     *   되돌림. voiceOnlyUri 캐시는 보존.
     * - voiceOnlyUri 캐시가 이미 있음 (한 번 분리한 적 있고 지금은 원본) → sourceUri 를 voiceOnlyUri 로
     *   즉시 swap. originalSourceUri 도 동일하게 유지(이미 채워져 있음).
     * - 캐시 없음 (첫 분리) → headless 음원분리 시작, Ready 시 voice_all stem URL 을 voiceOnlyUri 에
     *   저장 + originalSourceUri 에 현 sourceUri 보존 + sourceUri swap. Failed 는 [bgmBackgroundRemovalProgress]
     *   에 메시지 기록.
     *
     * Processing 중 같은 클립 재요청은 무시. 다른 BGM 의 분리 작업과 무관 (각자 job).
     */
    fun onToggleBgmBackgroundRemoval(clipId: String) {
        if (!audioExtractor.isSupported) return
        val state = _uiState.value
        val clip = state.bgmClips.firstOrNull { it.id == clipId } ?: return
        if (state.bgmBackgroundRemovalProgress[clipId] is BgmRemovalProgress.Processing) return

        // 1. 이미 voice-only 활성 → 원래대로 (instant swap, 캐시 보존)
        if (clip.isBackgroundRemoved) {
            val original = clip.originalSourceUri ?: return // invariant 깨졌으면 no-op
            viewModelScope.launch {
                bgmClipRepository.updateClip(clip.copy(sourceUri = original))
                clearRemovalProgress(clipId)
                pushUndoState()
            }
            return
        }

        // 2. 캐시된 voice-only 가 있고 지금은 원본 → 캐시로 즉시 swap (재분리 없음)
        val cached = clip.voiceOnlyUri
        if (cached != null) {
            viewModelScope.launch {
                bgmClipRepository.updateClip(
                    clip.copy(
                        sourceUri = cached,
                        // originalSourceUri 가 비어 있을 수 없는 invariant 지만 안전망.
                        originalSourceUri = clip.originalSourceUri ?: clip.sourceUri,
                    )
                )
                clearRemovalProgress(clipId)
                pushUndoState()
            }
            return
        }

        // 3. 첫 분리 — 영상 구간 "Separate this range" 와 동일하게 비용 confirmation 먼저.
        //    사용자가 차감액/잔액 확인 후 명시 confirm → [onConfirmBgmRemovalCost] 가 실제 시작.
        val durationMs = clip.sourceDurationMs.coerceAtLeast(0L)
        _uiState.update {
            it.copy(bgmRemovalCostPrompt = BgmRemovalCostPrompt(clipId = clipId, durationMs = durationMs))
        }
        prefetchBgmRemovalCost(clipId, durationMs)
    }

    /** BGM 분리 cost prefetch — 응답 도착 시 prompt 가 여전히 같은 clipId 면 costPreview 갱신. */
    private fun prefetchBgmRemovalCost(clipId: String, durationMs: Long) {
        viewModelScope.launch {
            audioSeparationRepository.getCost(durationMs)
                .onSuccess { cost ->
                    _uiState.update { current ->
                        val prompt = current.bgmRemovalCostPrompt
                        if (prompt == null || prompt.clipId != clipId) return@update current
                        current.copy(
                            bgmRemovalCostPrompt = prompt.copy(
                                costPreview = CreditCostPreview(
                                    durationMs = cost.durationMs,
                                    credits = cost.credits,
                                    balance = cost.balance,
                                    sufficient = cost.sufficient,
                                ),
                            )
                        )
                    }
                }
            // 실패는 silent — BFF 권위 검증이 폴백 (start 호출 시 402 mapping).
        }
    }

    fun onDismissBgmRemovalCost() {
        _uiState.update { it.copy(bgmRemovalCostPrompt = null) }
    }

    /** Confirm 후 실제 separation job 시작. 잔액 부족 케이스는 호출자가 [onRequestBuyCredits] 로 분기. */
    fun onConfirmBgmRemovalCost() {
        val prompt = _uiState.value.bgmRemovalCostPrompt ?: return
        _uiState.update { it.copy(bgmRemovalCostPrompt = null) }
        startBgmBackgroundRemoval(prompt.clipId)
    }

    private fun startBgmBackgroundRemoval(clipId: String) {
        val clip = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return
        setRemovalProgress(clipId, BgmRemovalProgress.Processing)
        // 분리 시작 시 해당 BGM 선택 해제 — 처리 중엔 재선택/트림 불가(위치 드래그는 유지). 선택된 채
        // 멈춰 trim 핸들이 떠 있던 것 방지.
        _uiState.update { st ->
            if (st.selectedBgmClipId == clipId) st.copy(selectedBgmClipId = null) else st
        }
        val originalUri = clip.sourceUri
        viewModelScope.launch {
            val jobResult = startAudioSeparation(
                sourceUri = originalUri,
                sourceKind = AudioSourceKind.AUDIO_COMPATIBLE,
            )
            val jobId = jobResult.getOrElse { err ->
                setRemovalProgress(
                    clipId,
                    BgmRemovalProgress.Failed(err.message ?: ERROR_SEPARATION_GENERIC),
                )
                return@launch
            }
            try {
                pollSeparation(jobId).collect { status ->
                    when (status) {
                        is SeparationStatus.Processing -> { /* keep Processing state */ }
                        is SeparationStatus.Ready -> {
                            val baseTrim = bffBaseUrl.trimEnd('/')
                            // Perso 분리 결과 화자 수 기반 voice 트랙 선택:
                            //   - 1명: speaker_0 — voice_all 은 의미상 동일하지만 BFF 가 1명 케이스에선
                            //     아예 skip. Perso 가 reactions 를 별도 화자로 분리하지 않은 케이스라
                            //     voice_all 사용 시 reactions 가 BGM 에 잔존 → BGM 슬라이더로 못 잡음.
                            //     speaker_0 만 쓰면 Perso voice 분류 그대로 들음.
                            //   - 2명+: voice_all (모든 SPEAKER amix) — 화자별 단일 선택 시 다른 화자
                            //     누락. reactions 가 별 SPEAKER 로 빠지는 케이스라 voice_all 이 깨끗.
                            val speakers = status.stems.filter { it.kind == StemKind.SPEAKER }
                            val voiceStem = when {
                                speakers.size >= 2 -> status.stems.firstOrNull {
                                    it.stemId == Stem.STEM_ID_VOICE_ALL
                                } ?: speakers.first()
                                speakers.size == 1 -> speakers.first()
                                else -> null
                            }
                            if (voiceStem == null) {
                                setRemovalProgress(
                                    clipId,
                                    BgmRemovalProgress.Failed("voice stem missing"),
                                )
                                return@collect
                            }
                            val absUrl = if (voiceStem.url.startsWith("http")) voiceStem.url
                                else "$baseTrim/${voiceStem.url.trimStart('/')}"
                            // 최신 clip 다시 가져오기 — 사용자가 분리 진행 중 clip 의 다른 속성
                            // (volume/startMs 등) 을 바꿨을 수 있어 stale copy 로 덮어쓰면 안 됨.
                            val latest = _uiState.value.bgmClips.firstOrNull { it.id == clipId }
                                ?: return@collect
                            bgmClipRepository.updateClip(
                                latest.copy(
                                    sourceUri = absUrl,
                                    originalSourceUri = originalUri,
                                    voiceOnlyUri = absUrl,
                                )
                            )
                            clearRemovalProgress(clipId)
                            pushUndoState()
                        }
                        is SeparationStatus.Failed -> setRemovalProgress(
                            clipId,
                            BgmRemovalProgress.Failed(status.progressReason ?: ERROR_SEPARATION_GENERIC),
                        )
                    }
                }
            } catch (e: Exception) {
                setRemovalProgress(
                    clipId,
                    BgmRemovalProgress.Failed(e.message ?: ERROR_SEPARATION_GENERIC),
                )
            }
        }
    }

    private fun setRemovalProgress(clipId: String, progress: BgmRemovalProgress) {
        _uiState.update { st ->
            st.copy(bgmBackgroundRemovalProgress = st.bgmBackgroundRemovalProgress + (clipId to progress))
        }
    }

    private fun clearRemovalProgress(clipId: String) {
        _uiState.update { st ->
            st.copy(bgmBackgroundRemovalProgress = st.bgmBackgroundRemovalProgress - clipId)
        }
    }

    fun onDeleteBgmClip(clipId: String) {
        viewModelScope.launch {
            bgmClipRepository.deleteClip(clipId)
            if (_uiState.value.selectedBgmClipId == clipId) {
                _uiState.value = _uiState.value.copy(selectedBgmClipId = null)
            }
            pushUndoState()
        }
    }

    /** 클립의 startMs 를 현재 playhead 로 재고정 — "삽입" 버튼이 호출. */
    fun onAnchorBgmAtPlayhead(clipId: String) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, startMs = _uiState.value.playbackPositionMs.coerceAtLeast(0L))
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    // --- Audio separation (per-segment voice/background split) ---

    /**
     * 영속화된 음성분리 잡을 클리어 (FAILED 후 "다시 시도" / 사용자 취소). EditProject separation* 초기화 +
     * audioSeparation 리셋. 진행 중인 [processingSeparations] entry 들은 취소하지 않음 — "다시 시도"는
     * 실패 상태를 비울 뿐, 정상 진행 중인 다른 잡들은 보존해야 한다.
     */
    fun onClearSeparation() {
        viewModelScope.launch {
            editProjectRepository.getProject(projectId)?.let { p ->
                editProjectRepository.updateProject(p.clearSeparation())
            }
            separationGate = TriggerGate.ARMED
            _uiState.value = _uiState.value.copy(audioSeparation = null, showAudioSeparationSheet = false)
        }
    }

    /**
     * 기존 SeparationDirective 의 selections 로 audioSeparation 을 PICK_STEMS 로 hydrate. 사용자가
     * 이미 만든 분리 구간을 다시 편집 (stem 포함/제외, 볼륨 조절) 가능하게.
     * 새 commit 시 기존 directive 는 삭제 + 새 directive 추가.
     */
    fun onEditExistingSeparation(directiveId: String) {
        val directive = _uiState.value.separationDirectives.firstOrNull { it.id == directiveId } ?: return
        // audioUrl 이 만료되었거나 빈 selection 도 stem 으로 노출 — 사용자가 볼륨 조정/삭제는 가능해야
        // 하므로 silent return 하지 않는다. 미리듣기는 url 빈 stem 에 대해선 자동 no-op.
        val stems = directive.selections.map { sel ->
            val kind = com.vibi.shared.domain.model.Stem.kindFromId(sel.stemId)
            val speakerIdx = com.vibi.shared.domain.model.Stem.speakerIndexFromId(sel.stemId)
            com.vibi.shared.domain.model.Stem(
                stemId = sel.stemId,
                label = "",
                url = sel.audioUrl.orEmpty(),
                kind = kind,
                speakerIndex = speakerIdx,
            )
        }
        // selections 가 비어도 (망가진 directive) sheet 는 열어 — 최소한 삭제 버튼은 노출돼야 함.
        val volumeByStem = directive.selections.associate { it.stemId to it.volume }
        val selectedByStem = directive.selections.associate { it.stemId to it.selected }
        val selections = stems.associate { stem ->
            stem.stemId to StemSelectionUi(
                stemId = stem.stemId,
                selected = selectedByStem[stem.stemId] ?: true,
                volume = volumeByStem[stem.stemId] ?: 1.0f,
            )
        }
        _uiState.value = _uiState.value.copy(
            audioSeparation = AudioSeparationUiState(
                segmentId = "",
                step = AudioSeparationStep.PICK_STEMS,
                jobId = "edit-${directive.id}",
                stems = stems,
                selections = selections,
                muteOriginalSegmentAudio = directive.muteOriginalSegmentAudio,
            ),
            showAudioSeparationSheet = true,
        )
        // 편집 진입한 directive id — commit 시 기존 directive 삭제 + 새 directive 등록 분기.
        editingDirectiveId = directive.id
    }

    private var editingDirectiveId: String? = null

    /** [onStartSeparation] 동기 재진입 가드 — 병합 prefix 의 suspend 창에서 중복 제출(더블탭) 차단. */
    private var separationStarting = false

    /**
     * 음원분리 target 이 video segment 가 아닌 BgmClip 일 때 그 id 보존. onConfirmStemMix 가 분기:
     * non-null 이면 BGM 교체 path, null 이면 video segment directive path.
     */
    private var bgmSeparationTargetId: String? = null

    /**
     * BgmClip 음원분리 — 추가된 음원에 대해 Perso AUDIO 분리 호출. 시트는 동일 [AudioSeparationSheet]
     * 재사용 — onConfirmStemMix 가 [bgmSeparationTargetId] 보고 BGM 교체 path 로 분기.
     *
     * 결과: 사용자가 picked stem 들로 원본 BGM 을 N 개의 stem BGM 클립으로 대체.
     */
    fun onStartBgmSeparation(bgmClipId: String) {
        if (!audioExtractor.isSupported) return
        val state = _uiState.value
        val bgm = state.bgmClips.firstOrNull { it.id == bgmClipId } ?: return
        bgmSeparationTargetId = bgm.id
        editingDirectiveId = null  // video directive flow 와 충돌 방지
        _uiState.value = state.copy(
            audioSeparation = AudioSeparationUiState(
                segmentId = "",
                step = AudioSeparationStep.PROCESSING,
                numberOfSpeakers = 2,
            ),
            showAudioSeparationSheet = true,
            isPlaying = false,
            // 분리 시작 시 해당 BGM 선택 해제 — 선택된 채 멈춰 trim 핸들이 떠 있던 것 방지.
            selectedBgmClipId = null,
        )
        bgmSeparationJob?.cancel()
        bgmSeparationJob = viewModelScope.launch {
            val result = startAudioSeparation(
                sourceUri = bgm.sourceUri,
                sourceKind = AudioSourceKind.AUDIO_COMPATIBLE,
            )
            val jobId = result.getOrElse { err ->
                updateSeparation { it.copy(step = AudioSeparationStep.FAILED, errorMessage = err.message) }
                return@launch
            }
            updateSeparation { it.copy(jobId = jobId) }
            pollBgmSeparationFlow(jobId)
        }
    }

    /**
     * BGM 분리 polling — audioSeparation singular state 갱신 (sheet 가 진행 중 노출되어야 하므로).
     * Ready 시 [onConfirmStemMix] → [onConfirmBgmStemMix] 분기로 BGM 클립 교체. video segment
     * 동시 분리 잡과 무관 — [pollSeparationFlow] 는 별도 token 기반 path.
     */
    private suspend fun pollBgmSeparationFlow(jobId: String) {
        try {
            pollSeparation(jobId).collect { status ->
                when (status) {
                    is SeparationStatus.Processing -> updateSeparation {
                        it.copy(progress = status.progress, progressReason = status.progressReason)
                    }
                    is SeparationStatus.Ready -> {
                        val baseTrim = bffBaseUrl.trimEnd('/')
                        val absStems = status.stems.map { stem ->
                            if (stem.url.startsWith("http")) stem
                            else stem.copy(url = "$baseTrim/${stem.url.trimStart('/')}")
                        }
                        updateSeparation {
                            // BGM 분리: 배경음 stem + VOICE_ALL ("모든 화자") 제외하고 화자별 SPEAKER stem 만 default 선택.
                            // VOICE_ALL 은 화자별 stem 으로 중복이라 mix 에 포함되면 보컬이 두 번 들림.
                            val defaults = absStems.associate { stem ->
                                stem.stemId to StemSelectionUi(
                                    stem.stemId,
                                    selected = stem.stemId != Stem.STEM_ID_BACKGROUND &&
                                        stem.stemId != Stem.STEM_ID_VOICE_ALL,
                                    volume = 1.0f,
                                )
                            }
                            it.copy(
                                step = AudioSeparationStep.PICK_STEMS,
                                progress = 100,
                                progressReason = null,
                                stems = absStems,
                                selections = defaults,
                            )
                        }
                        onConfirmStemMix()
                    }
                    is SeparationStatus.Failed -> updateSeparation {
                        it.copy(
                            step = AudioSeparationStep.FAILED,
                            errorMessage = status.progressReason ?: ERROR_SEPARATION_GENERIC,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            updateSeparation { it.copy(step = AudioSeparationStep.FAILED, errorMessage = e.message) }
        }
    }

    fun onShowAudioSeparationSheet(segmentId: String) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        // range mode 에서 진입한 케이스 — pendingRangeStartMs/EndMs 를 audioSeparation 에 반영.
        val rangeStart = state.pendingRangeStartMs.takeIf { state.pendingRangeEndMs > it }
        val rangeEnd = state.pendingRangeEndMs.takeIf { it > state.pendingRangeStartMs }
        // 새 SETUP sheet 는 항상 신선한 audioSeparation 으로 시작 — 이전 PROCESSING/PICK_STEMS/FAILED
        // 잔재가 sheet 에 노출되는 사고 방지. editingDirectiveId / bgmSeparationTargetId 도 함께 클리어.
        editingDirectiveId = null
        bgmSeparationTargetId = null
        _uiState.value = state.copy(
            audioSeparation = AudioSeparationUiState(
                segmentId = segmentId,
                rangeStartMs = rangeStart,
                rangeEndMs = rangeEnd,
            ),
            showAudioSeparationSheet = true,
            isPlaying = false,
        )
        // sheet 열린 직후 비용 견적 prefetch — 사용자가 Start 누르기 전에 "X 크레딧 사용,
        // 잔액 Y" 가 표시되도록. fire-and-forget — 실패해도 sheet 진입 자체는 막지 않고,
        // BFF 가 startSeparation 단계에서 권위 검증 (insufficient 시 402 매핑) 으로 폴백.
        prefetchSeparationCost(seg, rangeStart, rangeEnd)
    }

    /**
     * 분리 비용 미리보기 — BFF `/credits/cost` 호출 후 [AudioSeparationUiState.costPreview] 갱신.
     * trim 윈도우가 있으면 그 길이, 없으면 segment 의 effective duration 사용 (BFF 와 동일 산정 방식).
     */
    private fun prefetchSeparationCost(
        segment: Segment,
        rangeStartMs: Long?,
        rangeEndMs: Long?,
    ) {
        val durationMs = when {
            rangeStartMs != null && rangeEndMs != null -> (rangeEndMs - rangeStartMs).coerceAtLeast(0L)
            segment.trimStartMs > 0L || segment.trimEndMs > 0L ->
                (segment.effectiveTrimEndMs - segment.trimStartMs).coerceAtLeast(0L)
            else -> segment.durationMs.coerceAtLeast(0L)
        }
        viewModelScope.launch {
            audioSeparationRepository.getCost(durationMs)
                .onSuccess { cost ->
                    _uiState.update { current ->
                        // sheet 가 그새 닫혔거나 다른 segment 로 바뀌었으면 무시 (stale fetch).
                        val sep = current.audioSeparation
                        if (sep == null || sep.segmentId != segment.id) return@update current
                        current.copy(
                            audioSeparation = sep.copy(
                                costPreview = CreditCostPreview(
                                    durationMs = cost.durationMs,
                                    credits = cost.credits,
                                    balance = cost.balance,
                                    sufficient = cost.sufficient,
                                ),
                            )
                        )
                    }
                }
            // 실패는 silent — 사용자에게 미리보기는 부가 정보, BFF 권위 검증이 폴백.
        }
    }

    /**
     * 잔액 부족 FAILED 상태에서 사용자가 "충전하기" 누르면 emit — UI 가 collect 해 UserMenu 진입.
     */
    fun onRequestBuyCredits() {
        viewModelScope.launch { _navigateToBuyCredits.emit(Unit) }
    }

    /**
     * 현재 편집 중인 directive 를 삭제. mock 또는 기존 directive — editingDirectiveId 우선,
     * 없으면 audioSeparation.jobId 또는 모든 directive (단일 mock) 정리.
     * 영상 전체 음소거 해제 (audio 복원). sheet 닫음.
     */
    fun onDeleteCurrentSeparation() {
        viewModelScope.launch {
            // BGM 분리 진행/대기 중 취소 — sheet 닫고 target 만 클리어 (BGM clip 자체는 보존).
            if (bgmSeparationTargetId != null) {
                bgmSeparationTargetId = null
                bgmSeparationJob?.cancel()
                bgmSeparationJob = null
                _uiState.value = _uiState.value.copy(
                    audioSeparation = null,
                    showAudioSeparationSheet = false,
                )
                return@launch
            }
            val targetId = editingDirectiveId
                ?: _uiState.value.separationDirectives.firstOrNull()?.id
            if (targetId != null) {
                separationDirectiveRepository.delete(targetId)
            }
            editingDirectiveId = null
            // 모든 video segment audio 복원 (분리 시 mute 했던 거 되돌림).
            _uiState.value.segments.filter { it.type == SegmentType.VIDEO }
                .forEach { updateSegmentVolume(it.id, 1f) }
            _uiState.value = _uiState.value.copy(
                audioSeparation = null,
                showAudioSeparationSheet = false,
            )
        }
    }

    fun onDismissAudioSeparationSheet() {
        // 폴링은 백그라운드 viewModelScope 에서 계속 — sheet 만 닫음.
        // 진행 상태(audioSeparation) 도 그대로 보존해 다음 재진입 시 이어 보여줌.
        // userDismissed=true 로 표시 → 백그라운드 FAILED 도착해도 sheet 자동 재오픈 안 함.
        val sep = _uiState.value.audioSeparation
        _uiState.value = _uiState.value.copy(
            showAudioSeparationSheet = false,
            audioSeparation = sep?.copy(userDismissed = true),
        )
    }

    // 자막/더빙 localization 패널 메서드 제거.

    /**
     * BGM 분리 polling Job — 단일 잡 (BGM 한 번에 1개만). video segment 분리 다중 잡과 분리.
     */
    private var bgmSeparationJob: kotlinx.coroutines.Job? = null

    /**
     * 진행 중인 video segment 음원분리 polling Job 들. clientToken 으로 식별.
     * processingSeparations 리스트의 각 entry 가 자기 Job 을 갖고 동시 진행.
     */
    private val separationJobs: MutableMap<String, kotlinx.coroutines.Job> = mutableMapOf()

    /**
     * Directive commit 직렬화 락. submit-흐름 폴링과 화면 재진입 resume 폴링이 같은 잡을 동시에
     * `Ready` 로 받아 둘 다 commit 하면, 각자 `getByProject()` 로 기존 row 를 못 본 채(아직 상대가
     * insert 전) 둘 다 새 UUID 를 박는 race 가 있었다 — DB 레벨 find→insert 가 atomic 하지 않기 때문.
     * commit 전체를 이 락으로 감싸 한 번에 하나만 진행하게 해, 두 번째 commit 은 첫 commit 의 row 를
     * jobId 로 보고 같은 id 로 upsert 하도록 한다.
     */
    private val separationCommitMutex = Mutex()

    private fun addProcessingSeparationEntry(entry: ProcessingSeparation) {
        _uiState.update { it.copy(processingSeparations = it.processingSeparations + entry) }
    }

    private fun removeProcessingSeparationEntry(clientToken: String) {
        _uiState.update {
            it.copy(processingSeparations = it.processingSeparations.filter { p -> p.clientToken != clientToken })
        }
        separationJobs.remove(clientToken)
    }

    private fun updateProcessingSeparationEntry(
        clientToken: String,
        transform: (ProcessingSeparation) -> ProcessingSeparation,
    ) {
        _uiState.update { state ->
            val updated = state.processingSeparations.map { p ->
                if (p.clientToken == clientToken) transform(p) else p
            }
            state.copy(processingSeparations = updated)
        }
    }

    /**
     * 진행 중 음원분리 취소 (진행 바 탭 → "음원분리 취소"). 서버 잡은 멈출 수 없으므로 **클라가
     * 폴링만 중단**하고 처리 entry 를 정리한다 — directive 를 만들지 않으니 해당 구간은 원본 음원을
     * 그대로 쓰고, [TimelineUiState.processingSeparations] 가 비면 점유/export 잠금도 자동 해제된다.
     * 소모된 크레딧은 환불하지 않는다 (서버 작업이 계속 진행됨 — UI 가 사전 경고창으로 안내).
     */
    fun onCancelProcessingSeparation(clientToken: String) {
        val entry = _uiState.value.processingSeparations.firstOrNull { it.clientToken == clientToken }
            ?: return
        // (1) 폴링 코루틴 취소 — removeProcessingSeparationEntry 는 map 에서 빼기만 하므로 먼저 cancel.
        separationJobs[clientToken]?.cancel()
        // (2) in-memory entry 제거 → 타임라인 오버레이 사라지고 점유/잠금 해제 (directive 미생성).
        removeProcessingSeparationEntry(clientToken)
        // (3) 영속화에서도 해당 잡 제거 + legacy 단일 슬롯 정리 — 재진입/앱 재시작 시 resume 폴링 방지.
        viewModelScope.launch {
            editProjectRepository.getProject(projectId)?.let { p ->
                val cleaned = entry.jobId?.let { p.removeProcessingSeparation(it) } ?: p
                editProjectRepository.updateProject(cleaned.clearSeparation(), touchActivity = false)
            }
        }
        // 다음 분리 흐름을 위해 gate 재무장 (commit 과 동일).
        separationGate = TriggerGate.ARMED
    }

    /** "음원분리 취소" 경고 다이얼로그를 건너뛸지 — 사용자가 "다시 보지 않기" 체크 시 true 로 영속. */
    val skipSeparationCancelWarning: Boolean
        get() = settings.getBoolean(KEY_SKIP_SEPARATION_CANCEL_WARNING, false)

    /** "다시 보지 않기" 토글 영속. UI 경고 다이얼로그의 체크박스에서 호출. */
    fun setSkipSeparationCancelWarning(skip: Boolean) {
        settings.putBoolean(KEY_SKIP_SEPARATION_CANCEL_WARNING, skip)
    }

    fun onStartSeparation() {
        if (!audioExtractor.isSupported) return
        val sep0 = _uiState.value.audioSeparation ?: return
        if (_uiState.value.segments.none { it.id == sep0.segmentId }) return
        if (separationStarting) return   // 동기 재진입 가드 (병합 prefix 의 suspend 창 보호)
        separationStarting = true
        viewModelScope.launch {
            try {
                // 음원분리도 복제/삭제처럼 선택 구간 경계에서 세그먼트를 **split** 해 분리 구간을 독립 블록으로
                // 만든다 → directive 가 그 세그먼트에 1:1 앵커. 범위가 여러 세그먼트에 걸치면 split 된 가운데
                // 조각들을 하나로 **병합**해 단일 분리 세그먼트로. 분리 대상 segmentId 만 그 세그먼트로 교체하고
                // 범위([rs,re])는 유지(= 그 세그먼트의 timeline 범위와 동일).
                val rs = sep0.rangeStartMs
                val re = sep0.rangeEndMs
                var structureChanged = false
                if (rs != null && re != null) {
                    val slices = sliceGlobalRange(rs, re).sortedByDescending { it.order }
                    if (slices.isNotEmpty()) {
                        val middleIds = mutableListOf<String>()
                        var primaryMidId: String? = null
                        slices.forEach { s ->
                            val r = runCatching { splitSegment(s.segmentId, s.localStart, s.localEnd) }.getOrNull()
                            if (r != null) {
                                middleIds += r.middle.id
                                // 사용자가 분리를 연 세그먼트에서 잘려나온 in-range 조각을 기억(아래 run 선택용).
                                if (s.segmentId == sep0.segmentId) primaryMidId = r.middle.id
                                if (r.pre != null || r.post != null) structureChanged = true
                            }
                        }
                        if (middleIds.isNotEmpty()) {
                            refreshSegmentsStateFromDb()
                            // 격리된 in-range 조각들을 최대 병합 가능 run 으로 분할한다. 범위가 다른 source/비연속
                            // 경계(서로 다른 영상에 걸친 선택 등)를 포함해도 임의 첫 조각만 분리하고 나머지를 조용히
                            // 누락하던 갭 제거 — 사용자가 분리를 연 세그먼트를 포함하는 run(없으면 가장 큰 run)을
                            // 합쳐 분리 대상으로 삼는다. (같은 source 단일 run 이면 종전과 동일하게 전부 병합.)
                            val midSegs = _uiState.value.segments
                                .filter { it.id in middleIds.toSet() }
                                .sortedBy { it.order }
                            val runs = mutableListOf<MutableList<Segment>>()
                            midSegs.forEach { seg ->
                                val cur = runs.lastOrNull()
                                val joinable = cur != null &&
                                    cur.last().isContiguousMergeableWith(seg) &&
                                    cur.last().duplicatedFromId == null && seg.duplicatedFromId == null
                                if (joinable) cur!!.add(seg) else runs.add(mutableListOf(seg))
                            }
                            val chosen = runs.firstOrNull { run -> run.any { it.id == primaryMidId } }
                                ?: runs.maxByOrNull { it.size }
                            val targetId = when {
                                chosen == null -> middleIds.first()
                                chosen.size > 1 ->
                                    mergeSegments(chosen.map { it.id })?.also { structureChanged = true }?.id
                                        ?: chosen.first().id
                                else -> chosen.first().id
                            }
                            reanchorDirectiveCache()
                            _uiState.update { st ->
                                st.copy(audioSeparation = st.audioSeparation?.copy(segmentId = targetId))
                            }
                        }
                    }
                }
                // 구조가 바뀌었으면 undo 스냅샷 — 분리가 실패해도 split/병합을 되돌릴 수 있게.
                // (분리 성공 시 commit 이 undo 스택을 clear 하고 새 baseline 을 push 하므로 중복 없음.)
                if (structureChanged) pushUndoState()
                startSeparationResolved()
            } finally {
                separationStarting = false
            }
        }
    }

    private fun startSeparationResolved() {
        val state = _uiState.value
        val sep = state.audioSeparation ?: return
        val segment = state.segments.firstOrNull { it.id == sep.segmentId } ?: return
        // range 우선 — sheet 열 때 pendingRangeStartMs/EndMs 가 audioSeparation 에 들어옴.
        // 없으면 segment 의 trim 사용 (legacy/compat).
        val (effStart, effEnd) = when {
            sep.rangeStartMs != null && sep.rangeEndMs != null ->
                sep.rangeStartMs to sep.rangeEndMs
            segment.trimStartMs > 0L || segment.trimEndMs > 0L ->
                segment.trimStartMs to segment.effectiveTrimEndMs
            else -> null to null
        }
        // 같은 segment + 같은 range 가 이미 in-flight 면 중복 시작 차단 — UI 더블탭이나
        // sheet/range 버튼 경로 양쪽 어디서 들어와도 한 구간당 1잡 보장. editingDirectiveId
        // 케이스는 기존 directive 가 separationDirectives 에 있고 processingSeparations 엔
        // 없으므로 본 가드를 통과 (의도된 재처리).
        val alreadyProcessing = state.processingSeparations.any { p ->
            p.segmentId == sep.segmentId &&
                p.rangeStartMs == effStart &&
                p.rangeEndMs == effEnd
        }
        if (alreadyProcessing) {
            _uiState.value = state.copy(
                showAudioSeparationSheet = false,
                audioSeparation = null,
                isRangeSelecting = false,
                rangeTargetSegmentId = null,
                pendingRangeStartMs = 0L,
                pendingRangeEndMs = 0L,
            )
            return
        }
        val clientToken = Uuid.random().toString()
        val processingEntry = ProcessingSeparation(
            clientToken = clientToken,
            jobId = null,
            segmentId = sep.segmentId,
            rangeStartMs = effStart,
            rangeEndMs = effEnd,
            numberOfSpeakers = sep.numberOfSpeakers,
            muteOriginalSegmentAudio = sep.muteOriginalSegmentAudio,
            editingDirectiveId = editingDirectiveId,
        )
        // 분리 시작 즉시 sheet 닫고 range 선택 모드도 종료 → neutral 복귀. isRangeSelecting 이 남아 있으면
        // 분리 후에도 showRange=true 라 세그먼트 재정렬 게이트(showSegments || !showRange)가 꺼져 이동 불가했음.
        _uiState.value = _uiState.value.copy(
            showAudioSeparationSheet = false,
            audioSeparation = null,
            processingSeparations = _uiState.value.processingSeparations + processingEntry,
            isRangeSelecting = false,
            rangeTargetSegmentId = null,
            pendingRangeStartMs = 0L,
            pendingRangeEndMs = 0L,
        )
        // entry 에 보존했으므로 ViewModel-level 변수 초기화 — 다음 분리 흐름과 충돌 방지.
        editingDirectiveId = null
        val job = viewModelScope.launch {
            // 분리는 단일 segment + trim range 만 필요 — BFF /render (전체 타임라인 합성)
            // 거치는 건 과함. segment 원본 영상 그대로 올리고 trim 은 spec 으로 위임 →
            // BFF 가 ffmpeg 으로 audio 추출 + cut 후 Perso 호출. 속도/볼륨 편집은 분리된
            // stem 에 playback/export 단에서 적용.
            val startResult = startAudioSeparation(
                sourceUri = segment.sourceUri,
                sourceKind = AudioSourceKind.VIDEO,
                trimStartMs = effStart,
                trimEndMs = effEnd,
            )
            val jobId = startResult.getOrElse { err ->
                // 잔액 부족 (402) 은 일반 에러와 분리 — UI 가 "충전 필요" 분기로 정확한
                // 다음 단계 안내. 다른 에러는 generic 메시지.
                if (err is com.vibi.shared.domain.error.InsufficientCreditsException) {
                    handleSeparationInsufficientCredits(clientToken, err)
                } else {
                    handleSeparationFailure(clientToken, ERROR_SEPARATION_GENERIC)
                }
                return@launch
            }
            updateProcessingSeparationEntry(clientToken) { it.copy(jobId = jobId) }
            // 잡 ID 받자마자 EditProject 에 영속화 — 화면 떠나거나 앱 재실행 후 재진입해도 모든 잡 재개.
            // processingSeparations 리스트가 SSOT. legacy 단일 슬롯은 FAILED 상태 propagate 용도로만 유지.
            editProjectRepository.getProject(projectId)?.let { p ->
                val persistedJob = PersistedSeparationJob(
                    jobId = jobId,
                    segmentId = sep.segmentId,
                    rangeStartMs = effStart,
                    rangeEndMs = effEnd,
                    numberOfSpeakers = sep.numberOfSpeakers,
                    muteOriginalSegmentAudio = sep.muteOriginalSegmentAudio,
                )
                editProjectRepository.updateProject(
                    p.addProcessingSeparation(persistedJob).copy(
                        separationJobId = jobId,
                        separationSegmentId = sep.segmentId,
                        separationNumberOfSpeakers = sep.numberOfSpeakers,
                        separationMuteOriginal = sep.muteOriginalSegmentAudio,
                        separationStatus = AutoJobStatus.RUNNING,
                        separationError = null,
                    ),
                    touchActivity = false,
                )
            }
            separationGate = TriggerGate.FIRED
            pollSeparationFlow(clientToken, jobId)
        }
        separationJobs[clientToken] = job
    }

    /**
     * 잔액 부족 (402) 으로 분리 시작이 거부됐을 때 처리. handleSeparationFailure 와 동일한 sheet
     * reopen 패턴이지만 `insufficientCredits=true` 플래그로 UI 가 "충전 필요" 분기 + "Buy credits"
     * 버튼을 표시하도록 한다. EditProject 의 separationStatus 는 FAILED 로 마킹하되 error 텍스트는
     * 별도 — 일반 실패 (Perso 5xx 등) 와 추적 분리.
     *
     * costPreview 도 BFF 응답 값으로 갱신 — 사용자가 충전 후 돌아왔을 때 fresh balance 가 즉시
     * 보이도록.
     */
    private suspend fun handleSeparationInsufficientCredits(
        clientToken: String,
        cause: com.vibi.shared.domain.error.InsufficientCreditsException,
    ) {
        val entry = _uiState.value.processingSeparations.firstOrNull { it.clientToken == clientToken }
        removeProcessingSeparationEntry(clientToken)
        _uiState.update { state ->
            state.copy(
                audioSeparation = AudioSeparationUiState(
                    segmentId = entry?.segmentId ?: "",
                    step = AudioSeparationStep.FAILED,
                    numberOfSpeakers = entry?.numberOfSpeakers ?: 2,
                    muteOriginalSegmentAudio = entry?.muteOriginalSegmentAudio ?: true,
                    rangeStartMs = entry?.rangeStartMs,
                    rangeEndMs = entry?.rangeEndMs,
                    jobId = entry?.jobId,
                    errorMessage = ERROR_INSUFFICIENT_CREDITS,
                    insufficientCredits = true,
                    costPreview = CreditCostPreview(
                        durationMs = entry?.let { (it.rangeEndMs ?: 0L) - (it.rangeStartMs ?: 0L) } ?: 0L,
                        credits = cause.required,
                        balance = cause.balance,
                        sufficient = false,
                    ),
                ),
                showAudioSeparationSheet = true,
            )
        }
        editProjectRepository.getProject(projectId)?.let { p ->
            val cleaned = entry?.jobId?.let { p.removeProcessingSeparation(it) } ?: p
            editProjectRepository.updateProject(
                cleaned.copy(separationStatus = AutoJobStatus.FAILED, separationError = ERROR_INSUFFICIENT_CREDITS),
                touchActivity = false,
            )
        }
    }

    /**
     * 분리 잡 실패 처리 — entry 제거 + EditProject FAILED 마킹 + audioSeparation 으로 sheet reopen.
     * 사용자에게 에러 노출 위해 reopen 강제 (entry-level userDismissed 미지원).
     */
    private suspend fun handleSeparationFailure(clientToken: String, reason: String) {
        val entry = _uiState.value.processingSeparations.firstOrNull { it.clientToken == clientToken }
        removeProcessingSeparationEntry(clientToken)
        _uiState.update { state ->
            state.copy(
                audioSeparation = AudioSeparationUiState(
                    segmentId = entry?.segmentId ?: "",
                    step = AudioSeparationStep.FAILED,
                    numberOfSpeakers = entry?.numberOfSpeakers ?: 2,
                    muteOriginalSegmentAudio = entry?.muteOriginalSegmentAudio ?: true,
                    rangeStartMs = entry?.rangeStartMs,
                    rangeEndMs = entry?.rangeEndMs,
                    jobId = entry?.jobId,
                    errorMessage = reason,
                ),
                showAudioSeparationSheet = true,
            )
        }
        editProjectRepository.getProject(projectId)?.let { p ->
            val cleaned = entry?.jobId?.let { p.removeProcessingSeparation(it) } ?: p
            editProjectRepository.updateProject(
                cleaned.copy(separationStatus = AutoJobStatus.FAILED, separationError = reason),
                touchActivity = false,
            )
        }
    }

    /**
     * Video segment 분리 폴링 — [processingSeparations] 의 entry 를 token 으로 식별해 진행률/완료 반영.
     * Ready 시 자동 directive commit 후 entry 제거. Failed/Consumed 는 [handleSeparationFailure] 로.
     * BGM 분리 폴링은 [pollBgmSeparationFlow] 가 audioSeparation singular state 를 갱신.
     */
    private suspend fun pollSeparationFlow(clientToken: String, jobId: String) {
        try {
            pollSeparation(jobId).collect { status ->
                when (status) {
                    is SeparationStatus.Processing -> updateProcessingSeparationEntry(clientToken) {
                        it.copy(progress = status.progress, progressReason = status.progressReason)
                    }
                    is SeparationStatus.Ready -> {
                        // BFF 응답 stem.url 이 path-only (`/api/v2/...`) — iOS AVAudioPlayer 가
                        // host 없는 URL silent fail. 여기서 base URL prepend 해 absolute 로.
                        val baseTrim = bffBaseUrl.trimEnd('/')
                        val absStems = status.stems.map { stem ->
                            if (stem.url.startsWith("http")) stem
                            else stem.copy(url = "$baseTrim/${stem.url.trimStart('/')}")
                        }
                        // 모든 stem default 선택 — 단, VOICE_ALL ("모든 화자") 은 화자별 SPEAKER stem 으로
                        // 분리되므로 중복 → default 비선택. 사용자가 directive 막대 탭으로 사후 편집 가능.
                        // EditProject 의 separationStatus=READY 중간 write 는 곧바로 clearSeparation 으로
                        // 덮이므로 생략 — commit 이 단일 write 로 처리.
                        val defaults = absStems.associate { stem ->
                            val isVoiceAll = stem.stemId == Stem.STEM_ID_VOICE_ALL
                            stem.stemId to StemSelectionUi(stem.stemId, selected = !isVoiceAll, volume = 1.0f)
                        }
                        commitProcessingSeparationToDirective(
                            clientToken, absStems, defaults, status.actualDurationMs,
                        )
                    }
                    is SeparationStatus.Failed ->
                        handleSeparationFailure(clientToken, status.progressReason ?: ERROR_SEPARATION_GENERIC)
                }
            }
        } catch (e: CancellationException) {
            // 사용자 취소 (onCancelProcessingSeparation) 로 코루틴이 cancel 된 경우 — 실패가 아니므로
            // 에러 시트를 띄우지 않고 그대로 전파. entry 정리는 취소 호출부가 이미 처리.
            throw e
        } catch (e: Exception) {
            handleSeparationFailure(clientToken, ERROR_SEPARATION_GENERIC)
        }
    }

    /**
     * Processing entry → SeparationDirective 영속화. polling Ready 시 자동 호출.
     * entry 의 range / numberOfSpeakers / editingDirectiveId 그대로 사용. 기존 directive 의 좌표나
     * editingDirectiveId 가 일치하면 upsert (id 보존) — TimelineScreen 의 stemMixer group 끊김 방지.
     *
     * [actualDurationMs] 가 사용자 선택 길이와 ±SEPARATION_DURATION_SNAP_TOLERANCE_MS 이내면 rangeEndMs
     * 를 실측값으로 보정해 timeline 막대 길이를 stem 파일과 1:1 매칭. 차이가 그보다 크면 서버 측 측정
     * 이상 신호로 보고 사용자 선택값 유지.
     */
    private suspend fun commitProcessingSeparationToDirective(
        clientToken: String,
        stems: List<Stem>,
        selections: Map<String, StemSelectionUi>,
        actualDurationMs: Long? = null,
    ) {
        val entry = _uiState.value.processingSeparations.firstOrNull { it.clientToken == clientToken }
            ?: return
        try {
            // 같은 잡을 두 폴링 흐름이 동시에 commit 하는 race 를 막기 위해 find→add 를 단일 락으로
            // 직렬화. 두 번째 commit 은 첫 commit 이 박아둔 row 를 jobId 로 보고 같은 id 로 upsert.
            separationCommitMutex.withLock {
                val state = _uiState.value
                val segment = state.segments.firstOrNull { it.id == entry.segmentId }
                val urlByStemId = stems.associate { it.stemId to it.url }
                val selectionList = selections.values
                    .map { StemSelection(it.stemId, it.volume, urlByStemId[it.stemId], it.selected) }
                if (selectionList.none { it.selected }) {
                    removeProcessingSeparationEntry(clientToken)
                    // 영속화에서도 제거.
                    editProjectRepository.getProject(projectId)?.let { p ->
                        entry.jobId?.let {
                            editProjectRepository.updateProject(p.removeProcessingSeparation(it))
                        }
                    }
                    return
                }
                val isWholeVideo = segment == null
                val segStart = segment?.let { s -> segmentStartOffsetMs(state.segments, s.id) } ?: 0L
                val directiveStart = when {
                    entry.rangeStartMs != null -> entry.rangeStartMs
                    isWholeVideo -> 0L
                    else -> segStart + segment.trimStartMs
                }
                val requestedEnd = when {
                    entry.rangeEndMs != null -> entry.rangeEndMs
                    isWholeVideo -> state.videoDurationMs.coerceAtLeast(1L)
                    else -> directiveStart + (
                        if (segment.trimEndMs > 0L) segment.trimEndMs - segment.trimStartMs else segment.durationMs
                    )
                }
                val requestedDuration = (requestedEnd - directiveStart).coerceAtLeast(0L)
                val directiveEnd = if (
                    actualDurationMs != null &&
                    kotlin.math.abs(actualDurationMs - requestedDuration) <= SEPARATION_DURATION_SNAP_TOLERANCE_MS
                ) {
                    directiveStart + actualDurationMs
                } else {
                    requestedEnd
                }
                // Room Flow 첫 emission 이 uiState 에 도달하기 전에 commit 이 돌면 in-memory 가 비어
                // existing=null → 새 UUID 로 중복 directive 가 생기는 race 가 있었음. repository 직접
                // 조회로 권위 원본 (Room) 의 최신 상태를 본다.
                // dedup 우선순위: ① editingDirectiveId (명시적 재편집 대상) → ② jobId (같은 분리 잡의
                // 재-commit — range 스냅으로 좌표가 흔들려도 안정적으로 같은 row 매칭) → ③ range 일치
                // (jobId 가 없던 legacy row 호환 fallback).
                val persisted = separationDirectiveRepository.getByProject(projectId)
                val existing = entry.editingDirectiveId?.let { id -> persisted.firstOrNull { it.id == id } }
                    ?: entry.jobId?.let { jid -> persisted.firstOrNull { it.jobId == jid } }
                    ?: persisted.firstOrNull {
                        it.rangeStartMs == directiveStart && it.rangeEndMs == directiveEnd
                    }
                val effectiveStart = existing?.rangeStartMs ?: directiveStart
                val effectiveEnd = existing?.rangeEndMs ?: directiveEnd
                // 기존 directive 재편집(선택/볼륨만 변경)이면 그 앵커를 보존 — sep.segmentId 로 재계산하면
                // 생성 이후 분할/이동된 경우 잘못된 세그먼트로 재앵커될 수 있다. 신규면 현재 위치로 앵커.
                val (anchorSeg, anchorLocalStart, anchorLocalEnd) =
                    if (existing?.isAnchored == true)
                        Triple(existing.segmentId, existing.localStartMs, existing.localEndMs)
                    else directiveAnchor(segment, segStart, effectiveStart, effectiveEnd)
                separationDirectiveRepository.add(
                    SeparationDirective(
                        id = existing?.id ?: Uuid.random().toString(),
                        projectId = projectId,
                        rangeStartMs = effectiveStart,
                        rangeEndMs = effectiveEnd,
                        numberOfSpeakers = entry.numberOfSpeakers,
                        muteOriginalSegmentAudio = true,
                        selections = selectionList,
                        createdAt = existing?.createdAt ?: currentTimeMillis(),
                        jobId = entry.jobId ?: existing?.jobId,
                        segmentId = anchorSeg,
                        localStartMs = anchorLocalStart,
                        localEndMs = anchorLocalEnd,
                    )
                )
                removeProcessingSeparationEntry(clientToken)
                // 영속화 리스트에서도 해당 entry 제거 + legacy 단일 슬롯 클리어.
                editProjectRepository.getProject(projectId)?.let { p ->
                    val cleaned = entry.jobId?.let { p.removeProcessingSeparation(it) } ?: p
                    editProjectRepository.updateProject(cleaned.clearSeparation())
                }
                separationGate = TriggerGate.ARMED
                mainUndoManager.clear()
                // 방금 add 한 directive 를 observe Flow emit 전에 _uiState 로 동기 반영 — 스냅샷 정합.
                refreshDirectivesStateFromDb()
                pushUndoState()
            }
        } catch (e: Exception) {
            handleSeparationFailure(clientToken, ERROR_SEPARATION_GENERIC)
        }
    }

    fun onToggleStemSelection(stemId: String) {
        val sep = _uiState.value.audioSeparation ?: return
        val current = sep.selections[stemId] ?: return
        val newSelected = !current.selected
        val next = sep.selections + (stemId to current.copy(selected = newSelected))
        updateSeparation { it.copy(selections = next) }
        // 편집 모드 — 토글 즉시 directive 에 반영. 볼륨 슬라이더와 동일 패턴.
        val directiveId = editingDirectiveId ?: return
        viewModelScope.launch {
            val existing = _uiState.value.separationDirectives.firstOrNull { it.id == directiveId } ?: return@launch
            val updatedSelections = existing.selections.map { sel ->
                if (sel.stemId == stemId) sel.copy(selected = newSelected) else sel
            }
            separationDirectiveRepository.add(existing.copy(selections = updatedSelections))
        }
    }

    fun onUpdateStemVolume(stemId: String, volume: Float) {
        val sep = _uiState.value.audioSeparation ?: return
        val current = sep.selections[stemId] ?: return
        val clamped = volume.coerceIn(0f, 2f)
        val next = sep.selections + (stemId to current.copy(volume = clamped))
        updateSeparation { it.copy(selections = next) }
        // 편집 모드 — slider 드래그마다 directive 자체에도 즉시 반영. BGM 의 onUpdateBgmVolume 과 동등한
        // 동작 (별도 "적용" 누르지 않아도 영속). 새 분리 작업(directive 미생성) 흐름에선 no-op.
        val directiveId = editingDirectiveId ?: return
        viewModelScope.launch {
            val existing = _uiState.value.separationDirectives.firstOrNull { it.id == directiveId } ?: return@launch
            val updatedSelections = existing.selections.map { sel ->
                if (sel.stemId == stemId) sel.copy(volume = clamped) else sel
            }
            separationDirectiveRepository.add(existing.copy(selections = updatedSelections))
        }
    }

    fun onToggleMuteOriginalSegmentAudio() {
        updateSeparation { it.copy(muteOriginalSegmentAudio = !it.muteOriginalSegmentAudio) }
    }

    /**
     * SoundDeck 카드용 — directive 를 id 로 직접 지정해 stem 한 개의 selected/volume 갱신.
     * [onToggleStemSelection] / [onUpdateStemVolume] 는 sheet edit-mode (editingDirectiveId set)
     * 전제라 카드처럼 sheet 없이 직접 조작하려면 본 helper 가 필요.
     */
    private fun updateDirectiveStemSelection(
        directiveId: String,
        stemId: String,
        transform: (StemSelection) -> StemSelection,
    ) {
        viewModelScope.launch {
            val existing = _uiState.value.separationDirectives.firstOrNull { it.id == directiveId }
                ?: return@launch
            val updated = existing.selections.map { sel ->
                if (sel.stemId == stemId) transform(sel) else sel
            }
            separationDirectiveRepository.add(existing.copy(selections = updated))
        }
    }

    fun onSetStemSelectionForDirective(directiveId: String, stemId: String, selected: Boolean) {
        updateDirectiveStemSelection(directiveId, stemId) { it.copy(selected = selected) }
    }

    fun onSetStemVolumeForDirective(directiveId: String, stemId: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 2f)
        updateDirectiveStemSelection(directiveId, stemId) { it.copy(volume = clamped) }
    }

    /**
     * Sound Deck A/B 미리듣기 토글 — 원본(영상 audio 만) ↔ 내 믹스(분리 stem + directive 적용).
     * 화면측에서 stem mixer / video segment volume 가 본 필드를 보고 합쳐 적용.
     */
    fun onTogglePreviewMode() {
        val next = when (_uiState.value.previewMode) {
            PreviewMode.MIX -> PreviewMode.ORIGINAL
            PreviewMode.ORIGINAL -> PreviewMode.MIX
        }
        _uiState.update { it.copy(previewMode = next) }
    }

    /**
     * directive range 진입/이탈 시점에 TimelineScreen 이 호출 — range 안에서만 video player 원본 audio
     * mute. ephemeral UiState 플래그만 토글 (DB write 없음). VideoPlayer 가 [runtimeVideoMutedForDirective]
     * 를 보고 최종 volume 에 곱해 적용한다. segment.volumeScale 은 사용자 설정값 그대로 보존.
     */
    fun muteVideoSegmentsForDirective(mute: Boolean) {
        if (_uiState.value.runtimeVideoMutedForDirective == mute) return
        _uiState.update { it.copy(runtimeVideoMutedForDirective = mute) }
    }

    fun onConfirmStemMix() {
        // BGM 음원분리 path 분기 — onStartBgmSeparation 으로 진입한 경우.
        val bgmTargetId = bgmSeparationTargetId
        if (bgmTargetId != null) {
            onConfirmBgmStemMix(bgmTargetId)
            return
        }
        val state = _uiState.value
        val sep = state.audioSeparation ?: return
        val segment = state.segments.firstOrNull { it.id == sep.segmentId }
        // stem URL 까지 함께 보존해야 export 시점에 BFF render 가 amix 합성 가능.
        // 별도 mix 산출(mp3) 단계는 폐기 — preview/export 둘 다 stem 리스트로 직접 처리.
        val urlByStemId = sep.stems.associate { it.stemId to it.url }
        // 선택 안 된 stem 도 directive 에 보존 (selected = false) — 사용자가 sheet 재진입 시 다시
        // 토글 가능. preview/render 는 selected 플래그 보고 mute / 제외.
        val selections = sep.selections.values
            .map { StemSelection(it.stemId, it.volume, urlByStemId[it.stemId], it.selected) }
        if (selections.none { it.selected }) return
        viewModelScope.launch {
            val isWholeVideo = segment == null
            val segStart = segment?.let { s -> segmentStartOffsetMs(state.segments, s.id) } ?: 0L
            // 사용자 지정 range 우선 — sheet 진입 시 pendingRangeStartMs/EndMs 가 sep 에 들어감.
            // 없으면 segment 전체 / 영상 전체.
            val uiRangeStart = sep.rangeStartMs
            val uiRangeEnd = sep.rangeEndMs
            val directiveStart = when {
                uiRangeStart != null -> uiRangeStart
                isWholeVideo -> 0L
                else -> segStart + segment.trimStartMs
            }
            val directiveEnd = when {
                uiRangeEnd != null -> uiRangeEnd
                isWholeVideo -> state.videoDurationMs.coerceAtLeast(1L)
                else -> directiveStart + (
                    if (segment.trimEndMs > 0L) segment.trimEndMs - segment.trimStartMs else segment.durationMs
                )
            }
            try {
                // editingDirectiveId 또는 같은 range 의 기존 directive 가 있으면 그 id 그대로 upsert.
                // stable id 유지로 TimelineScreen 의 stemMixer 가 같은 group 으로 인식해 reload/다운로드
                // 방지 (사용자가 selection / volume 여러 번 조정해도 끊김 없음).
                val existing = editingDirectiveId?.let { id ->
                    _uiState.value.separationDirectives.firstOrNull { it.id == id }
                } ?: _uiState.value.separationDirectives.firstOrNull {
                    it.rangeStartMs == directiveStart && it.rangeEndMs == directiveEnd
                }
                val effectiveStart = existing?.rangeStartMs ?: directiveStart
                val effectiveEnd = existing?.rangeEndMs ?: directiveEnd
                editingDirectiveId = null
                // 기존 directive 재편집이면 앵커 보존(생성 후 분할/이동 시 sep.segmentId 재계산은 부정확).
                val (anchorSeg, anchorLocalStart, anchorLocalEnd) =
                    if (existing?.isAnchored == true)
                        Triple(existing.segmentId, existing.localStartMs, existing.localEndMs)
                    else directiveAnchor(segment, segStart, effectiveStart, effectiveEnd)
                separationDirectiveRepository.add(
                    SeparationDirective(
                        id = existing?.id ?: Uuid.random().toString(),
                        projectId = projectId,
                        rangeStartMs = effectiveStart,
                        rangeEndMs = effectiveEnd,
                        numberOfSpeakers = sep.numberOfSpeakers,
                        muteOriginalSegmentAudio = true,  // 항상 음소거 (사용자 정책).
                        selections = selections,
                        createdAt = existing?.createdAt ?: currentTimeMillis(),
                        // 기존 directive 편집이면 그 jobId 보존 (dedup 키 유지). 신규면 null.
                        jobId = existing?.jobId,
                        segmentId = anchorSeg,
                        localStartMs = anchorLocalStart,
                        localEndMs = anchorLocalEnd,
                    )
                )
                // 음성분리 구간의 원본 음성은 directive range 안에서만 mute — TimelineScreen 의
                // stemMixer effect 가 진입/이탈 시점에 [muteVideoSegmentsForDirective] 호출.
                // 여기서 영구 mute 하면 range 밖에서도 원본 audio 안 들려 사용자 의도 ("음원분리 안
                // 시킨 부분은 원본 음원") 에 맞지 않음.
                // 적용 즉시 sheet 닫음 — DONE 단계의 "완료" 팝업 노출 안 함.
                _uiState.value = _uiState.value.copy(
                    audioSeparation = null,
                    showAudioSeparationSheet = false,
                )
                // commit 완료 → EditProject 의 separation* 모두 IDLE 로 클리어. 다음 음성분리 새로 가능.
                editProjectRepository.getProject(projectId)?.let { p ->
                    editProjectRepository.updateProject(p.clearSeparation())
                }
                separationGate = TriggerGate.ARMED
                mainUndoManager.clear()
                // 방금 add 한 directive 를 observe Flow emit 전에 _uiState 로 동기 반영 — 스냅샷 정합.
                refreshDirectivesStateFromDb()
                pushUndoState()
            } catch (e: Exception) {
                updateSeparation {
                    it.copy(step = AudioSeparationStep.FAILED, errorMessage = e.message)
                }
            }
        }
    }

    /**
     * BGM 음원분리 confirm — 원본 BgmClip 삭제 후 선택된 stem 마다 새 BgmClip 추가.
     * stem audio 는 BFF signed URL — 모바일에서 그대로 sourceUri 로 사용 (Ktor Client 가 streaming).
     */
    private fun onConfirmBgmStemMix(bgmTargetId: String) {
        val state = _uiState.value
        val sep = state.audioSeparation ?: return
        val original = state.bgmClips.firstOrNull { it.id == bgmTargetId } ?: return
        val urlByStemId = sep.stems.associate { it.stemId to it.url }
        val selectedStems = sep.selections.values.filter { it.selected }
        if (selectedStems.isEmpty()) return
        viewModelScope.launch {
            try {
                bgmClipRepository.deleteClip(original.id)
                selectedStems.forEach { sel ->
                    val url = urlByStemId[sel.stemId]?.takeIf { it.isNotBlank() } ?: return@forEach
                    val info = runCatching { audioMetadataExtractor.extract(url) }.getOrNull()
                    val durMs = info?.durationMs?.takeIf { it > 0L } ?: original.sourceDurationMs
                    addBgmClip(
                        projectId = projectId,
                        sourceUri = url,
                        sourceDurationMs = durMs,
                        startMs = original.startMs,
                        volumeScale = sel.volume,
                    )
                }
                bgmSeparationTargetId = null
                _uiState.value = _uiState.value.copy(
                    audioSeparation = null,
                    showAudioSeparationSheet = false,
                )
                pushUndoState()
            } catch (e: Exception) {
                updateSeparation { it.copy(step = AudioSeparationStep.FAILED, errorMessage = e.message) }
            }
        }
    }

    private fun updateSeparation(transform: (AudioSeparationUiState) -> AudioSeparationUiState) {
        val current = _uiState.value.audioSeparation ?: return
        val next = transform(current)
        // 분리는 시작 직후 sheet 를 닫지만 FAILED 가 되면 사용자에게 즉시 사유를 보여줄
        // 채널이 사라짐 — 다시 열어 errorMessage 노출. 단, 사용자가 진행 중 sheet 를
        // 명시적으로 닫았다면(userDismissed) 의사 존중하고 자동 reopen 안 함.
        val reopen = next.step == AudioSeparationStep.FAILED &&
            current.step != AudioSeparationStep.FAILED &&
            !next.userDismissed
        _uiState.value = _uiState.value.copy(
            audioSeparation = next,
            showAudioSeparationSheet = if (reopen) true else _uiState.value.showAudioSeparationSheet,
        )
    }

    companion object {
        const val MIN_RANGE_MS = SplitSegmentUseCase.MIN_RANGE_MS
        private const val HTTP_NOT_FOUND = 404
        private const val ERROR_SEPARATION_GENERIC = "Separation failed"
        // sheet 의 FAILED 단계가 [AudioSeparationUiState.insufficientCredits]=true 일 때 표시할
        // 메시지. UI 가 추가로 "Buy credits" 버튼을 렌더해 충전 화면으로 분기 시킨다.
        private const val ERROR_INSUFFICIENT_CREDITS = "Not enough credits"
        /** 사용자 선택 trim 길이 ↔ stem 실측 길이의 허용 오차. 이 범위 밖이면 보정하지 않고
         * 사용자 선택값을 그대로 유지 (서버 측 측정 이상 가드). BFF TRIM_DURATION_TOLERANCE_MS
         * (100ms) 의 ~2배. */
        private const val SEPARATION_DURATION_SNAP_TOLERANCE_MS = 200L

        /** 프로젝트 제목 / 음원·녹음 이름 입력 길이 상한 — 비정상 길이 입력 방어. */
        private const val MAX_DISPLAY_NAME_LEN = 80

        /** "음원분리 취소 경고 다시 보지 않기" 영속 키 (Settings). */
        private const val KEY_SKIP_SEPARATION_CANCEL_WARNING = "separation.cancel.skipWarning"
    }

    fun onOpenExportOptionsSheet() {
        _uiState.value = _uiState.value.copy(showExportOptionsSheet = true)
    }

    fun onCloseExportOptionsSheet() {
        _uiState.value = _uiState.value.copy(showExportOptionsSheet = false)
    }

}
