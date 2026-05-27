@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.vibi.shared.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.platform.currentTimeMillis
import kotlin.uuid.Uuid
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.StemKind
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.PersistedSeparationJob
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.addProcessingSeparation
import com.vibi.shared.domain.model.removeProcessingSeparation
import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.platform.generateId
import com.vibi.shared.domain.model.TextOverlay
import com.vibi.shared.domain.model.clearSeparation
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.repository.SeparationStatus
import com.vibi.shared.domain.repository.StemSelection
import com.vibi.shared.domain.repository.TextOverlayRepository
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.AudioSourceKind
import com.vibi.shared.domain.usecase.bgm.AddBgmClipUseCase
import com.vibi.shared.domain.usecase.bgm.UpdateBgmClipUseCase
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import com.vibi.shared.domain.usecase.input.SetProjectFrameUseCase
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.domain.usecase.save.ExportVariant
import com.vibi.shared.domain.usecase.save.SaveAllVariantsUseCase
import com.vibi.shared.domain.usecase.separation.PollSeparationUseCase
import com.vibi.shared.domain.usecase.separation.StartAudioSeparationUseCase
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import com.vibi.shared.domain.util.UndoRedoManager
import com.vibi.shared.domain.usecase.text.AddTextOverlayUseCase
import com.vibi.shared.domain.usecase.text.DuplicateTextOverlayUseCase
import com.vibi.shared.domain.usecase.text.UpdateTextOverlayUseCase
import com.vibi.shared.domain.usecase.timeline.AddVideoSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.DuplicateSegmentRangeUseCase
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
import kotlinx.coroutines.launch

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
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = EditProject.DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f,
    val showFrameSheet: Boolean = false,
    val pendingFrameWidth: String = "",
    val pendingFrameHeight: String = "",
    val pendingBackgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val pendingVideoScale: Float = EditProject.DEFAULT_VIDEO_SCALE,
    val pendingVideoOffsetXPct: Float = 0f,
    val pendingVideoOffsetYPct: Float = 0f,
    val frameError: String? = null,
    val textOverlays: List<TextOverlay> = emptyList(),
    val selectedTextOverlayId: String? = null,
    val showTextOverlaySheet: Boolean = false,
    val editingTextOverlayId: String? = null,
    val pendingOverlayText: String = "",
    val pendingOverlayFontFamily: String = TextOverlay.DEFAULT_FONT_FAMILY,
    val pendingOverlayFontSizeSp: Float = TextOverlay.DEFAULT_FONT_SIZE_SP,
    val pendingOverlayColorHex: String = TextOverlay.DEFAULT_COLOR_HEX,
    val pendingOverlayStartMs: Long = 0L,
    val pendingOverlayEndMs: Long = 0L,
    val textOverlayError: String? = null,
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
    val frameAspectRatio: Float
        get() = if (frameWidth > 0 && frameHeight > 0) {
            frameWidth.toFloat() / frameHeight.toFloat()
        } else 0f
}

data class TimelineSnapshot(
    val segments: List<Segment>,
    val textOverlays: List<TextOverlay> = emptyList(),
    val bgmClips: List<BgmClip> = emptyList(),
    val separationDirectives: List<SeparationDirective> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val backgroundColorHex: String = EditProject.DEFAULT_BACKGROUND_COLOR_HEX,
    val videoScale: Float = EditProject.DEFAULT_VIDEO_SCALE,
    val videoOffsetXPct: Float = 0f,
    val videoOffsetYPct: Float = 0f
)

class TimelineViewModel constructor(
    private val projectId: String,
    private val segmentRepository: SegmentRepository,
    private val editProjectRepository: EditProjectRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val updateSegmentTrim: UpdateSegmentTrimUseCase,
    private val addVideoSegment: AddVideoSegmentUseCase,
    private val removeSegment: RemoveSegmentUseCase,
    private val splitSegment: SplitSegmentUseCase,
    private val duplicateSegmentRange: DuplicateSegmentRangeUseCase,
    private val removeSegmentRange: RemoveSegmentRangeUseCase,
    private val updateSegmentVolume: UpdateSegmentVolumeUseCase,
    private val updateSegmentSpeed: UpdateSegmentSpeedUseCase,
    private val setProjectFrame: SetProjectFrameUseCase,
    private val addTextOverlay: AddTextOverlayUseCase,
    private val updateTextOverlay: UpdateTextOverlayUseCase,
    private val duplicateTextOverlay: DuplicateTextOverlayUseCase,
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

    init {
        loadSegments()
        observeProject()
        observeTextOverlays()
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
                            frameWidth = project.frameWidth,
                            frameHeight = project.frameHeight,
                            backgroundColorHex = project.backgroundColorHex,
                            videoScale = project.videoScale,
                            videoOffsetXPct = project.videoOffsetXPct,
                            videoOffsetYPct = project.videoOffsetYPct,
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
        separationJobs[clientToken]?.cancel()
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

    private fun observeTextOverlays() {
        viewModelScope.launch {
            textOverlayRepository.observeOverlays(projectId).collect { overlays ->
                // atomic CAS — 다른 observer / mutation 핸들러와 race 시 stale state read-modify-write
                // 차단 (다른 observe* 들과 동일 패턴).
                _uiState.update { it.copy(textOverlays = overlays) }
            }
        }
    }

    private fun loadSegments() {
        viewModelScope.launch {
            segmentRepository.observeByProjectId(projectId).collect { segments ->
                val first = segments.firstOrNull()
                val total = segments.sumOf { it.effectiveDurationMs }
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

    private fun segmentStartOffsetMs(segments: List<Segment>, segmentId: String): Long {
        var acc = 0L
        for (seg in segments) {
            if (seg.id == segmentId) return acc
            acc += seg.effectiveDurationMs
        }
        return acc
    }

    /**
     * In-memory snapshot — `_uiState.value` 가 이미 모든 repository 의 최신 상태를 반영하고 있으므로
     * 다시 7-async fan-out 으로 repository 를 .first() 하지 않는다. frame/background 등 EditProject
     * 필드도 _uiState 에 미러링돼 있어 동기 접근 가능. (observeProject 가 이미 이 필드들을 hydrate)
     *
     * 효과: pushUndoState 가 hot-path (드래그·슬라이더·복제 등) 에서 호출돼도 zero round-trip.
     * 이전 fan-out 은 매 mutation 마다 6-7개 .first() 콜드 콜렉트 → dispatcher hop 비용이 측정됐다.
     */
    private fun buildSnapshot(): TimelineSnapshot {
        val s = _uiState.value
        return TimelineSnapshot(
            segments = s.segments,
            textOverlays = s.textOverlays,
            bgmClips = s.bgmClips,
            separationDirectives = s.separationDirectives,
            frameWidth = s.frameWidth,
            frameHeight = s.frameHeight,
            backgroundColorHex = s.backgroundColorHex,
            videoScale = s.videoScale,
            videoOffsetXPct = s.videoOffsetXPct,
            videoOffsetYPct = s.videoOffsetYPct,
        )
    }

    private fun pushUndoState() {
        mainUndoManager.pushState(buildSnapshot())
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        val mgr = mainUndoManager
        _uiState.value = _uiState.value.copy(
            canUndo = mgr.canUndo,
            canRedo = mgr.canRedo
        )
    }

    fun onUpdatePlaybackPosition(positionMs: Long) {
        val s = _uiState.value
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
        _uiState.value = s.copy(playbackPositionMs = clamped)
    }

    fun onTogglePlayback() {
        _uiState.value = _uiState.value.copy(isPlaying = !_uiState.value.isPlaying)
    }

    /**
     * Single-selection model: at any moment at most one of segment / text-overlay / bgm
     * may be selected. This helper applies a tap-toggle on the chosen target while clearing
     * every other selected*Id.
     */
    private enum class SelectionTarget {
        Segment, TextOverlay, Bgm
    }

    private fun selectExclusively(target: SelectionTarget, id: String?) {
        val state = _uiState.value
        val current = when (target) {
            SelectionTarget.Segment -> state.selectedSegmentId
            SelectionTarget.TextOverlay -> state.selectedTextOverlayId
            SelectionTarget.Bgm -> state.selectedBgmClipId
        }
        val next = if (id != null && id == current) null else id
        // BGM 을 새로 선택(next != null) 한 경우엔 영상 다듬기/range 선택 모드를 함께 종료. 그래야
        // 하단 액션 토글이 Video 에서 Bgm 으로 교체된다 — 안 그러면 BGM 을 골랐는데 영상 토글이
        // 그대로 남아 사용자가 어느 트랙을 편집하는지 혼선.
        val switchingToBgm = target == SelectionTarget.Bgm && next != null
        _uiState.value = state.copy(
            selectedSegmentId = if (target == SelectionTarget.Segment) next else null,
            selectedTextOverlayId = if (target == SelectionTarget.TextOverlay) next else null,
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

    /**
     * Pick the lowest lane number free of time-overlap with existing text overlays —
     * they share lanes on the merged overlay track.
     */
    private fun pickFreeOverlayLane(startMs: Long, endMs: Long): Int {
        val state = _uiState.value
        return com.vibi.shared.domain.util.pickLowestFreeLane(
            existing = state.textOverlays,
            startMs = startMs,
            endMs = endMs,
            laneOf = { it.lane },
            startOf = { it.startMs },
            endOf = { it.endMs }
        )
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
        textOverlayRepository.deleteAllByProjectId(projectId)
        textOverlayRepository.addOverlays(snapshot.textOverlays)
        bgmClipRepository.deleteAllByProjectId(projectId)
        bgmClipRepository.addClips(snapshot.bgmClips)
        separationDirectiveRepository.deleteByProject(projectId)
        if (snapshot.separationDirectives.isNotEmpty()) {
            separationDirectiveRepository.addAll(snapshot.separationDirectives)
        }
        if (snapshot.frameWidth > 0 && snapshot.frameHeight > 0) {
            // Frame is restored directly via the repository (not SetProjectFrameUseCase)
            // because snapshot values already passed validation when first applied;
            // re-validating could reject a legitimate prior state if validation rules
            // change in the future.
            val current = editProjectRepository.getProject(projectId)
            if (current != null) {
                editProjectRepository.updateProject(
                    current.copy(
                        frameWidth = snapshot.frameWidth,
                        frameHeight = snapshot.frameHeight,
                        backgroundColorHex = snapshot.backgroundColorHex,
                        videoScale = snapshot.videoScale,
                        videoOffsetXPct = snapshot.videoOffsetXPct,
                        videoOffsetYPct = snapshot.videoOffsetYPct,
                        updatedAt = currentTimeMillis()
                    )
                )
            }
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
        _uiState.value = _uiState.value.copy(isTrimming = false, isVideoSelected = false)
    }

    /** Timeline 헤더 "저장" 버튼 — 원본 영상 렌더 + 갤러리 저장. */
    fun onSaveAllVariants() {
        if (_uiState.value.saveStatus is SaveStatus.RUNNING) return
        val s = _uiState.value
        _uiState.value = s.copy(
            saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
            shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
        )
        viewModelScope.launch { runSaveAllVariants() }
    }

    private suspend fun runSaveAllVariants() {
        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.RUNNING(0))
        val result = saveAllVariants(
            projectId = projectId,
            onProgress = { percent ->
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.RUNNING(percent))
            },
        )
        result.fold(
            onSuccess = {
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.DONE)
                runCatching { editProjectRepository.deleteProject(projectId) }
                _navigateBackHome.emit(Unit)
            },
            onFailure = { e ->
                com.vibi.shared.platform.logError("TimelineVM", "save failed", e)
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.FAILED("Save failed"))
            }
        )
    }

    fun onClearSaveStatus() {
        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.IDLE)
    }

    /** Timeline 헤더 "공유" 버튼 — 원본 영상을 렌더 후 share sheet. */
    fun onShareExport() {
        if (_uiState.value.shareStatus is ShareStatus.RUNNING) return
        if (_uiState.value.segments.isEmpty()) return
        val s = _uiState.value
        _uiState.value = s.copy(
            saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
            shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
        )
        viewModelScope.launch { runShareExport() }
    }

    private suspend fun runShareExport() {
        _uiState.value = _uiState.value.copy(shareStatus = ShareStatus.RUNNING(0))
        val result = saveAllVariants(
            projectId = projectId,
            onProgress = { percent ->
                _uiState.value = _uiState.value.copy(shareStatus = ShareStatus.RUNNING(percent))
            },
            saveToGallery = false,
        )
        result.fold(
            onSuccess = { variants ->
                val paths = variants.map { it.outputPath }
                if (paths.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        shareStatus = ShareStatus.FAILED("Nothing to share")
                    )
                    return@fold
                }
                shareSheetLauncher.shareVideos(
                    sourcePaths = paths,
                    mimeType = "video/mp4",
                    title = "vibi",
                ).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(shareStatus = ShareStatus.DONE)
                    },
                    onFailure = { e ->
                        com.vibi.shared.platform.logError("TimelineVM", "share sheet failed", e)
                        _uiState.value = _uiState.value.copy(
                            shareStatus = ShareStatus.FAILED("Share failed")
                        )
                    }
                )
            },
            onFailure = { e ->
                com.vibi.shared.platform.logError("TimelineVM", "share render failed", e)
                _uiState.value = _uiState.value.copy(
                    shareStatus = ShareStatus.FAILED("Share failed")
                )
            }
        )
    }

    fun onClearShareStatus() {
        _uiState.value = _uiState.value.copy(shareStatus = ShareStatus.IDLE)
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
            removeSegment(segmentId)
            _uiState.value = _uiState.value.copy(
                selectedSegmentId = null,
                isPlaying = false,
                playbackPositionMs = 0L
            )
            pushUndoState()
        }
    }

    /**
     * 분리 directive 또는 진행 중인 분리 잡 (processingSeparations) 과 겹치지 않는 자유 구간들 —
     * segment 영역 [segStart, segEnd] 안에서. 빈 리스트면 segment 전체가 이미 점유됨 (range 진입 X).
     *
     * 진행 중 잡도 occupied 로 취급해야 사용자가 같은 구간을 중복 분리 요청하거나 진행 중 구간 위로
     * range 슬라이더를 겹쳐 잡지 못한다.
     */
    private fun freeIntervalsInSegment(segStart: Long, segEnd: Long): List<LongRange> {
        val state = _uiState.value
        val committed = state.separationDirectives.map { it.rangeStartMs..it.rangeEndMs }
        // rangeStart/End 가 null 이면 전체 영상 분리 — segStart..segEnd 전체를 점유.
        val processing = state.processingSeparations.map { p ->
            val s = p.rangeStartMs ?: segStart
            val e = p.rangeEndMs ?: segEnd
            s..e
        }
        val occupied = (committed + processing)
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

    /** point 가 속한 자유 구간 (없으면 null — directive 안에 있음). */
    private fun freeIntervalContaining(point: Long): LongRange? {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(0L)
        val all = freeIntervalsInSegment(0L, total)
        return all.firstOrNull { point in it }
    }

    fun onEnterRangeMode(segmentId: String) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        // Default range covers the full tapped segment in GLOBAL timeline ms
        // (TimelineScreen passes identity offsets). Using segment-local ms
        // here would place the range at 0 instead of the segment's on-screen
        // position after deletes/splits shift later segments rightward.
        val segStart = segmentStartOffsetMs(state.segments, seg.id)
        val segEnd = segStart + seg.effectiveDurationMs
        // 이미 분리된 directive 와 겹치지 않는 첫 자유 구간을 default 로 사용. 자유 구간 없으면 진입 거부.
        val free = freeIntervalsInSegment(segStart, segEnd)
        val defaultRange = free.firstOrNull() ?: return
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
    ) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, seg.id)
        val segEnd = segStart + seg.effectiveDurationMs
        // segment 편집은 directive 영역과 무관 — 전체 segment 를 default 로.
        // 진입 시 원본 영상으로 강제 reset — 사용자는 편집 결과를 원본 영상에서 확인.
        // 진입 시 음성분리/자막더빙 sheet 들도 같이 닫음 — 영상편집 중엔 노출 금지.
        _uiState.value = state.copy(
            isRangeSelecting = true,
            isSegmentEditMode = true,
            editTargets = targets.ifEmpty { setOf(EditTarget.Video) },
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            // 영상 편집 모드 진입 = 단일 선택 모델상 BGM/텍스트 선택 해제. 안 그러면 deck
            // 카드 highlight 또는 잠재적 BGM 하단바가 영상 모드와 동시에 잡힘.
            selectedBgmClipId = null,
            selectedTextOverlayId = null,
            pendingRangeStartMs = segStart,
            pendingRangeEndMs = segEnd,
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
    fun onSelectSegmentInEdit(segmentId: String) {
        val snapshot = _uiState.value
        if (!snapshot.isSegmentEditMode) return
        // 같은 segment 재탭이면서 range 가 선택돼 있으면 deselect 토글.
        if (snapshot.selectedSegmentId == segmentId &&
            snapshot.pendingRangeEndMs > snapshot.pendingRangeStartMs
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
        selectSegmentInEditInternal(segmentId)
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
                playbackPositionMs = segStart,
                // 영상 segment 탭 → editTargets 를 Video 로 (BGM 모드였다면 자동 해제).
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
     * 영상편집 모드의 ✓(체크) — 편집 확정. 영상 segment 자체는 그대로 두고, 자막·더빙·음원분리·BGM
     * 결과와 메인 timeline undo 스택을 모두 초기화 (사용자에게 안내문구로 명시).
     * BFF 호출 없음 — 단순 로컬 상태 리셋만.
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
        mainUndoManager.clear()
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            editTargets = setOf(EditTarget.Video),
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
            selectedSegmentId = null,
        )
        pushUndoState()
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
        // 새 range 안으로 재생 marker clamp — 음원분리 모드에서 marker 는 항상 선택 구간 안.
        val clampedPlayback = state.playbackPositionMs.coerceIn(s, e)
        if (state.isRangeSelecting) {
            _uiState.value = state.copy(
                pendingRangeStartMs = s,
                pendingRangeEndMs = e,
                playbackPositionMs = clampedPlayback,
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
                playbackPositionMs = clampedPlayback,
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
     * 영상편집 모드: 전체 timeline [0, totalMs] — sliceGlobalRange 가 다중 segment 자동 처리.
     * 음원분리 모드: directive-free interval 안으로 clamp.
     */
    private fun rangeBoundsForCurrentMode(): Pair<Long, Long> {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(0L)
        if (state.isSegmentEditMode) {
            return 0L to total
        }
        val freeFromEnd = freeIntervalContaining(state.pendingRangeEndMs)
        return (freeFromEnd?.first ?: 0L) to (freeFromEnd?.last ?: total)
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

    fun onDuplicateRange() {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
        val applyToVideo = state.editTargets.hasVideo()
        val applyToBgm = state.editTargets.hasBgm()
        val slices = if (applyToVideo) sliceGlobalRange(start, end).sortedByDescending { it.order } else emptyList()
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            var lastDuplicated: Segment? = null
            slices.forEach { s ->
                lastDuplicated = duplicateSegmentRange(s.segmentId, s.localStart, s.localEnd)
            }
            if (applyToBgm) applyBgmRangeDuplicate(start, end)
            if (applyToVideo) {
                // directive ripple — 순서 중요: shift 가 inside directive 의 rangeStart 를 안 옮긴 상태에서
                // duplicate 가 안 directive 를 새 위치(+width)에 복제. 둘이 합쳐져 원본 + 복제 모두 보존.
                applyDirectiveShiftAfter(after = end, deltaMs = end - start)
                applyDirectiveDuplicateInside(start, end)
            }
            refreshSegmentsStateFromDb()
            if (wasSegmentEdit) {
                lastDuplicated?.id?.let { selectSegmentInEditInternal(it) }
            }
            pushUndoState()
        }
    }

    fun onDeleteRange() {
        val state = _uiState.value
        val start = state.pendingRangeStartMs
        val end = state.pendingRangeEndMs
        if (end - start < MIN_RANGE_MS) return
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
            // volume 은 timeline 길이를 바꾸지 않아 directive ripple 필요 없음.
            refreshSegmentsStateFromDb()
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
                    )
                )
            }
        }
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
     * 구간 `[start, end]` 에 완전히 포함된 directive 를 새 id 로 복제해 `[ds+width, de+width]` 에 삽입.
     * stem audio URL · sourceOffsetMs 보존 — 같은 audio 가 두 위치에서 재생. 각 piece 는 독립 directive
     * id 라 stem mixer 의 activeGroup 모델과 호환 (playback 위치가 한 번에 하나의 directive 안).
     *
     * 부분 겹침 directive 는 복제 대상에서 제외 — 자른 stem 의 부분 재생은 의미상 모호하고, 사용자가
     * 의도한 "이 구간 그대로 한 번 더" 를 위반.
     */
    private suspend fun applyDirectiveDuplicateInside(start: Long, end: Long) {
        val width = end - start
        if (width <= 0L) return
        val clones = _uiState.value.separationDirectives
            .filter { it.rangeStartMs >= start && it.rangeEndMs <= end }
            .map { it.copy(
                id = generateId(),
                rangeStartMs = it.rangeStartMs + width,
                rangeEndMs = it.rangeEndMs + width,
            ) }
        separationDirectiveRepository.addAll(clones)
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

    fun segmentStartMs(segmentId: String): Long {
        val segments = _uiState.value.segments
        var acc = 0L
        for (seg in segments) {
            if (seg.id == segmentId) return acc
            acc += seg.effectiveDurationMs
        }
        return acc
    }

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

    fun onShowFrameSheet() {
        val state = _uiState.value
        // Seed width/height with a sensible default when the project has no
        // frame yet so Apply doesn't fail with "Width/Height required" if the
        // user tapped Apply without touching a preset.
        val basis = state.frameLongestSidePx().coerceAtLeast(MIN_FRAME_DIMENSION)
        val (defaultW, defaultH) = FramePreset.LANDSCAPE_16_9.toDimensions(basis)
        _uiState.value = state.copy(
            showFrameSheet = true,
            pendingFrameWidth = if (state.frameWidth > 0) state.frameWidth.toString()
                else defaultW.toString(),
            pendingFrameHeight = if (state.frameHeight > 0) state.frameHeight.toString()
                else defaultH.toString(),
            pendingBackgroundColorHex = state.backgroundColorHex,
            pendingVideoScale = state.videoScale,
            pendingVideoOffsetXPct = state.videoOffsetXPct,
            pendingVideoOffsetYPct = state.videoOffsetYPct,
            frameError = null
        )
    }

    fun onConfirmFrameWithPlacement(
        scale: Float,
        offsetXPct: Float,
        offsetYPct: Float
    ) {
        // Single write path: validate + persist in one atomic state update so
        // UI never observes a half-applied placement (old confirm() re-read
        // _uiState.value and implicitly depended on copy ordering).
        val state = _uiState.value
        val width = state.pendingFrameWidth.toIntOrNull()
        val height = state.pendingFrameHeight.toIntOrNull()
        if (width == null || width <= 0 || height == null || height <= 0) {
            _uiState.value = state.copy(frameError = "Width and height must be positive integers")
            return
        }
        if (width > MAX_FRAME_DIMENSION || height > MAX_FRAME_DIMENSION) {
            _uiState.value = state.copy(frameError = "Max ${MAX_FRAME_DIMENSION}px")
            return
        }
        val color = state.pendingBackgroundColorHex.trim()
        val clampedScale = scale.coerceIn(
            EditProject.MIN_VIDEO_SCALE,
            EditProject.MAX_VIDEO_SCALE
        )
        val clampedX = offsetXPct.coerceIn(
            -EditProject.MAX_VIDEO_OFFSET_PCT,
            EditProject.MAX_VIDEO_OFFSET_PCT
        )
        val clampedY = offsetYPct.coerceIn(
            -EditProject.MAX_VIDEO_OFFSET_PCT,
            EditProject.MAX_VIDEO_OFFSET_PCT
        )
        viewModelScope.launch {
            try {
                setProjectFrame(
                    projectId = projectId,
                    width = width,
                    height = height,
                    backgroundColorHex = color,
                    videoScale = clampedScale,
                    videoOffsetXPct = clampedX,
                    videoOffsetYPct = clampedY
                )
                _uiState.value = _uiState.value.copy(
                    showFrameSheet = false,
                    pendingVideoScale = clampedScale,
                    pendingVideoOffsetXPct = clampedX,
                    pendingVideoOffsetYPct = clampedY,
                    frameError = null
                )
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(frameError = "Invalid frame")
            }
        }
    }

    fun onDismissFrameSheet() {
        _uiState.value = _uiState.value.copy(showFrameSheet = false, frameError = null)
    }

    fun onFrameWidthInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingFrameWidth = value, frameError = null)
    }

    fun onFrameHeightInputChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingFrameHeight = value, frameError = null)
    }

    fun onFrameBackgroundColorChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingBackgroundColorHex = value, frameError = null)
    }

    fun onApplyFramePreset(preset: FramePreset) {
        val state = _uiState.value
        val basis = state.frameLongestSidePx().coerceAtLeast(MIN_FRAME_DIMENSION)
        val (w, h) = preset.toDimensions(basis)
        _uiState.value = state.copy(
            pendingFrameWidth = w.toString(),
            pendingFrameHeight = h.toString(),
            frameError = null
        )
    }

    fun onConfirmFrame() {
        val state = _uiState.value
        val width = state.pendingFrameWidth.toIntOrNull()
        val height = state.pendingFrameHeight.toIntOrNull()
        if (width == null || width <= 0 || height == null || height <= 0) {
            _uiState.value = state.copy(frameError = "Width and height must be positive integers")
            return
        }
        if (width > MAX_FRAME_DIMENSION || height > MAX_FRAME_DIMENSION) {
            _uiState.value = state.copy(frameError = "Max ${MAX_FRAME_DIMENSION}px")
            return
        }
        val color = state.pendingBackgroundColorHex.trim()
        viewModelScope.launch {
            try {
                setProjectFrame(
                    projectId = projectId,
                    width = width,
                    height = height,
                    backgroundColorHex = color,
                    videoScale = state.pendingVideoScale,
                    videoOffsetXPct = state.pendingVideoOffsetXPct,
                    videoOffsetYPct = state.pendingVideoOffsetYPct
                )
                _uiState.value = _uiState.value.copy(showFrameSheet = false, frameError = null)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                // SetProjectFrameUseCase is the source of truth for color/dimension
                // validation; keep its message as-is for the UI.
                _uiState.value = _uiState.value.copy(frameError = "Invalid frame")
            }
        }
    }

    private fun TimelineUiState.frameLongestSidePx(): Int {
        val segMax = segments.firstOrNull()?.let { maxOf(it.width, it.height) } ?: 0
        return maxOf(frameWidth, frameHeight, segMax)
    }

    fun onShowTextOverlaySheetForNew() {
        val state = _uiState.value
        val total = state.videoDurationMs.coerceAtLeast(1L)
        val playhead = state.playbackPositionMs.coerceIn(0L, (total - 1L).coerceAtLeast(0L))
        // Avoid stacking on top of an existing overlay at the same start time.
        // Shift the new clip rightward in 250ms steps until a free spot opens
        // (or until we run out of timeline).
        val taken = state.textOverlays.map { it.startMs }.toSet()
        var start = playhead
        while (start in taken && start < total - 1L) start += 250L
        val end = (start + DEFAULT_OVERLAY_DURATION_MS).coerceAtMost(total)
        _uiState.value = state.copy(
            showTextOverlaySheet = true,
            editingTextOverlayId = null,
            pendingOverlayText = "",
            pendingOverlayFontFamily = TextOverlay.DEFAULT_FONT_FAMILY,
            pendingOverlayFontSizeSp = TextOverlay.DEFAULT_FONT_SIZE_SP,
            pendingOverlayColorHex = TextOverlay.DEFAULT_COLOR_HEX,
            pendingOverlayStartMs = start,
            pendingOverlayEndMs = end,
            textOverlayError = null
        )
    }

    fun onShowTextOverlaySheetForEdit(overlayId: String) {
        val overlay = _uiState.value.textOverlays.firstOrNull { it.id == overlayId } ?: return
        _uiState.value = _uiState.value.copy(
            showTextOverlaySheet = true,
            editingTextOverlayId = overlay.id,
            pendingOverlayText = overlay.text,
            pendingOverlayFontFamily = overlay.fontFamily,
            pendingOverlayFontSizeSp = overlay.fontSizeSp,
            pendingOverlayColorHex = overlay.colorHex,
            pendingOverlayStartMs = overlay.startMs,
            pendingOverlayEndMs = overlay.endMs,
            textOverlayError = null
        )
    }

    fun onDismissTextOverlaySheet() {
        _uiState.value = _uiState.value.copy(
            showTextOverlaySheet = false,
            textOverlayError = null
        )
    }

    fun onTextOverlayTextChanged(value: String) {
        _uiState.value = _uiState.value.copy(pendingOverlayText = value, textOverlayError = null)
    }

    fun onTextOverlayFontFamilyChanged(family: String) {
        _uiState.value = _uiState.value.copy(pendingOverlayFontFamily = family, textOverlayError = null)
    }

    fun onTextOverlayFontSizeChanged(sizeSp: Float) {
        _uiState.value = _uiState.value.copy(
            pendingOverlayFontSizeSp = sizeSp.coerceIn(
                TextOverlay.MIN_FONT_SIZE_SP, TextOverlay.MAX_FONT_SIZE_SP
            ),
            textOverlayError = null
        )
    }

    fun onTextOverlayColorChanged(colorHex: String) {
        _uiState.value = _uiState.value.copy(pendingOverlayColorHex = colorHex, textOverlayError = null)
    }

    fun onConfirmTextOverlay() {
        val state = _uiState.value
        val text = state.pendingOverlayText.trim()
        if (text.isEmpty()) {
            _uiState.value = state.copy(textOverlayError = "Text cannot be empty")
            return
        }
        if (state.pendingOverlayEndMs <= state.pendingOverlayStartMs) {
            _uiState.value = state.copy(textOverlayError = "End must be greater than Start")
            return
        }
        viewModelScope.launch {
            try {
                if (state.editingTextOverlayId == null) {
                    addPendingTextOverlay(state, text)
                } else {
                    updatePendingTextOverlay(state, state.editingTextOverlayId, text)
                }
                _uiState.value = _uiState.value.copy(
                    showTextOverlaySheet = false,
                    textOverlayError = null
                )
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    textOverlayError = "Invalid text overlay"
                )
            }
        }
    }

    private suspend fun addPendingTextOverlay(state: TimelineUiState, text: String) {
        addTextOverlay(
            projectId = projectId,
            text = text,
            startMs = state.pendingOverlayStartMs,
            endMs = state.pendingOverlayEndMs,
            fontFamily = state.pendingOverlayFontFamily,
            fontSizeSp = state.pendingOverlayFontSizeSp,
            colorHex = state.pendingOverlayColorHex,
            lane = pickFreeOverlayLane(state.pendingOverlayStartMs, state.pendingOverlayEndMs)
        )
    }

    private suspend fun updatePendingTextOverlay(
        state: TimelineUiState,
        editId: String,
        text: String
    ) {
        updateTextOverlay(
            overlayId = editId,
            text = text,
            fontFamily = state.pendingOverlayFontFamily,
            fontSizeSp = state.pendingOverlayFontSizeSp,
            colorHex = state.pendingOverlayColorHex,
            startMs = state.pendingOverlayStartMs,
            endMs = state.pendingOverlayEndMs
        )
    }

    fun onSelectTextOverlay(overlayId: String?) =
        selectExclusively(SelectionTarget.TextOverlay, overlayId)

    fun onUpdateTextOverlayScreenPosition(overlayId: String, xPct: Float, yPct: Float) {
        viewModelScope.launch {
            try {
                updateTextOverlay(overlayId, xPct = xPct, yPct = yPct)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onUpdateTextOverlayFontSize(overlayId: String, fontSizeSp: Float) {
        viewModelScope.launch {
            try {
                updateTextOverlay(overlayId, fontSizeSp = fontSizeSp)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onMoveTextOverlay(overlayId: String, newStartMs: Long) {
        viewModelScope.launch {
            val overlay = _uiState.value.textOverlays.firstOrNull { it.id == overlayId } ?: return@launch
            val duration = overlay.endMs - overlay.startMs
            val total = _uiState.value.videoDurationMs
            val maxStart = (total - duration).coerceAtLeast(0L)
            val coercedStart = newStartMs.coerceIn(0L, maxStart)
            try {
                updateTextOverlay(
                    overlayId,
                    startMs = coercedStart,
                    endMs = coercedStart + duration
                )
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onResizeTextOverlay(overlayId: String, newEndMs: Long) {
        viewModelScope.launch {
            try {
                updateTextOverlay(overlayId, endMs = newEndMs)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(textOverlayError = e.message)
            }
        }
    }

    fun onDuplicateTextOverlay(overlayId: String) {
        viewModelScope.launch {
            val src = textOverlayRepository.getOverlay(overlayId) ?: return@launch
            // Place duplicate at the same time position; AddTextOverlayUseCase
            // auto-picks the lowest free lane so the copy lands directly below.
            try {
                addTextOverlay(
                    projectId = projectId,
                    text = src.text,
                    startMs = src.startMs,
                    endMs = src.endMs,
                    fontFamily = src.fontFamily,
                    fontSizeSp = src.fontSizeSp,
                    colorHex = src.colorHex,
                    xPct = src.xPct,
                    yPct = src.yPct,
                    lane = pickFreeOverlayLane(src.startMs, src.endMs)
                )
                pushUndoState()
            } catch (_: IllegalArgumentException) {
                // overlay disappeared between selection and action; safe to ignore
            }
        }
    }

    fun onChangeTextOverlayLane(overlayId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val overlay = textOverlayRepository.getOverlay(overlayId) ?: return@launch
            val newLane = (overlay.lane + delta).coerceAtLeast(0)
            if (newLane == overlay.lane) return@launch
            try {
                updateTextOverlay(overlayId, lane = newLane)
                pushUndoState()
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    fun onDeleteTextOverlay(overlayId: String) {
        viewModelScope.launch {
            textOverlayRepository.deleteOverlay(overlayId)
            if (_uiState.value.selectedTextOverlayId == overlayId) {
                _uiState.value = _uiState.value.copy(selectedTextOverlayId = null)
            }
            pushUndoState()
        }
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
                val startMs = state.playbackPositionMs
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
                                insertStartMs = startMs,
                                rangeStartMs = 0L,
                                rangeEndMs = state.videoDurationMs,
                            ),
                        )
                    }
                    return@launch
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
                        startMs = req.insertStartMs,
                        volumeScale = 1.0f,
                    )
                } else {
                    addBgmClip(
                        projectId = projectId,
                        sourceUri = req.sourceUri,
                        sourceDurationMs = req.sourceDurationMs,
                        startMs = req.insertStartMs,
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

    fun onUpdateBgmStartMs(clipId: String, newStartMs: Long) {
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

    fun onStartSeparation() {
        if (!audioExtractor.isSupported) return
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
        // 분리 시작 즉시 sheet 닫음 — 결과는 timeline directive 막대로 알림.
        _uiState.value = _uiState.value.copy(
            showAudioSeparationSheet = false,
            audioSeparation = null,
            processingSeparations = _uiState.value.processingSeparations + processingEntry,
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
            val existing = entry.editingDirectiveId?.let { id ->
                _uiState.value.separationDirectives.firstOrNull { it.id == id }
            } ?: _uiState.value.separationDirectives.firstOrNull {
                it.rangeStartMs == directiveStart && it.rangeEndMs == directiveEnd
            }
            val effectiveStart = existing?.rangeStartMs ?: directiveStart
            val effectiveEnd = existing?.rangeEndMs ?: directiveEnd
            separationDirectiveRepository.add(
                SeparationDirective(
                    id = existing?.id ?: Uuid.random().toString(),
                    projectId = projectId,
                    rangeStartMs = effectiveStart,
                    rangeEndMs = effectiveEnd,
                    numberOfSpeakers = entry.numberOfSpeakers,
                    muteOriginalSegmentAudio = true,
                    selections = selectionList,
                    createdAt = existing?.createdAt ?: currentTimeMillis()
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
            pushUndoState()
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
                separationDirectiveRepository.add(
                    SeparationDirective(
                        id = existing?.id ?: Uuid.random().toString(),
                        projectId = projectId,
                        rangeStartMs = effectiveStart,
                        rangeEndMs = effectiveEnd,
                        numberOfSpeakers = sep.numberOfSpeakers,
                        muteOriginalSegmentAudio = true,  // 항상 음소거 (사용자 정책).
                        selections = selections,
                        createdAt = existing?.createdAt ?: currentTimeMillis()
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
        const val MIN_FRAME_DIMENSION = 16
        const val MAX_FRAME_DIMENSION = 7680
        const val DEFAULT_OVERLAY_DURATION_MS = 3_000L
        private const val HTTP_NOT_FOUND = 404
        private const val ERROR_SEPARATION_GENERIC = "Separation failed"
        // sheet 의 FAILED 단계가 [AudioSeparationUiState.insufficientCredits]=true 일 때 표시할
        // 메시지. UI 가 추가로 "Buy credits" 버튼을 렌더해 충전 화면으로 분기 시킨다.
        private const val ERROR_INSUFFICIENT_CREDITS = "Not enough credits"
        /** 사용자 선택 trim 길이 ↔ stem 실측 길이의 허용 오차. 이 범위 밖이면 보정하지 않고
         * 사용자 선택값을 그대로 유지 (서버 측 측정 이상 가드). BFF TRIM_DURATION_TOLERANCE_MS
         * (100ms) 의 ~2배. */
        private const val SEPARATION_DURATION_SNAP_TOLERANCE_MS = 200L
    }

    fun onOpenExportOptionsSheet() {
        _uiState.value = _uiState.value.copy(showExportOptionsSheet = true)
    }

    fun onCloseExportOptionsSheet() {
        _uiState.value = _uiState.value.copy(showExportOptionsSheet = false)
    }

}

enum class FramePreset(val ratioW: Int, val ratioH: Int, val label: String) {
    PORTRAIT_9_16(9, 16, "9:16"),
    SQUARE_1_1(1, 1, "1:1"),
    LANDSCAPE_16_9(16, 9, "16:9"),
    PORTRAIT_4_5(4, 5, "4:5");

    fun toDimensions(longestSide: Int): Pair<Int, Int> {
        val maxRatio = maxOf(ratioW, ratioH)
        val unit = (longestSide.toDouble() / maxRatio).coerceAtLeast(1.0)
        val w = (unit * ratioW).toInt().coerceAtLeast(1)
        val h = (unit * ratioH).toInt().coerceAtLeast(1)
        return w to h
    }
}
