@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.dubcast.shared.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dubcast.shared.platform.currentTimeMillis
import kotlin.uuid.Uuid
import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.model.EditProject
import com.dubcast.shared.domain.model.ImageClip
import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.model.SeparationDirective
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.model.SubtitlePosition
import com.dubcast.shared.domain.model.TargetLanguage
import com.dubcast.shared.domain.model.TextOverlay
import com.dubcast.shared.domain.model.Voice
import com.dubcast.shared.domain.repository.BgmClipRepository
import com.dubcast.shared.domain.repository.DubClipRepository
import com.dubcast.shared.domain.repository.EditProjectRepository
import com.dubcast.shared.domain.repository.ImageClipRepository
import com.dubcast.shared.domain.repository.SegmentRepository
import com.dubcast.shared.domain.repository.SeparationDirectiveRepository
import com.dubcast.shared.domain.repository.SeparationStatus
import com.dubcast.shared.domain.repository.StemSelection
import com.dubcast.shared.domain.repository.SubtitleClipRepository
import com.dubcast.shared.domain.repository.TextOverlayRepository
import com.dubcast.shared.domain.repository.TtsRepository
import com.dubcast.shared.domain.model.SeparationMediaType
import com.dubcast.shared.domain.usecase.image.AddImageClipUseCase
import com.dubcast.shared.domain.usecase.image.DeleteImageClipUseCase
import com.dubcast.shared.domain.usecase.image.UpdateImageClipUseCase
import com.dubcast.shared.domain.usecase.bgm.AddBgmClipUseCase
import com.dubcast.shared.domain.usecase.bgm.DeleteBgmClipUseCase
import com.dubcast.shared.domain.usecase.bgm.UpdateBgmClipUseCase
import com.dubcast.shared.domain.usecase.input.AudioMetadataExtractor
import com.dubcast.shared.domain.usecase.input.ImageMetadataExtractor
import com.dubcast.shared.domain.usecase.input.SetProjectFrameUseCase
import com.dubcast.shared.domain.usecase.input.VideoMetadataExtractor
import com.dubcast.shared.domain.usecase.separation.PollSeparationUseCase
import com.dubcast.shared.domain.usecase.separation.StartAudioSeparationUseCase
import com.dubcast.shared.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.dubcast.shared.domain.usecase.subtitle.DeleteSubtitleClipUseCase
import com.dubcast.shared.domain.usecase.subtitle.GenerateAutoDubUseCase
import com.dubcast.shared.domain.usecase.subtitle.GenerateAutoSubtitlesUseCase
import com.dubcast.shared.domain.usecase.subtitle.UndoRedoManager
import com.dubcast.shared.domain.usecase.text.AddTextOverlayUseCase
import com.dubcast.shared.domain.usecase.text.DeleteTextOverlayUseCase
import com.dubcast.shared.domain.usecase.text.DuplicateTextOverlayUseCase
import com.dubcast.shared.domain.usecase.text.UpdateTextOverlayUseCase
import com.dubcast.shared.domain.usecase.timeline.AddImageSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.AddVideoSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.DeleteDubClipUseCase
import com.dubcast.shared.domain.usecase.timeline.DuplicateSegmentRangeUseCase
import com.dubcast.shared.domain.usecase.timeline.MoveDubClipUseCase
import com.dubcast.shared.domain.usecase.timeline.RemoveSegmentRangeUseCase
import com.dubcast.shared.domain.usecase.timeline.RemoveSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.SplitSegmentUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateSegmentSpeedUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateSegmentTrimUseCase
import com.dubcast.shared.domain.usecase.timeline.UpdateSegmentVolumeUseCase
import com.dubcast.shared.domain.usecase.tts.GetVoiceListUseCase
import com.dubcast.shared.domain.usecase.tts.SynthesizeDubClipUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewDubClip(
    val text: String,
    val voiceId: String,
    val voiceName: String,
    val audioFilePath: String,
    val durationMs: Long
)

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
    val dubClips: List<DubClip> = emptyList(),
    val subtitleClips: List<SubtitleClip> = emptyList(),
    val imageClips: List<ImageClip> = emptyList(),
    val playbackPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedDubClipId: String? = null,
    val selectedSubtitleClipId: String? = null,
    val selectedImageClipId: String? = null,
    val voices: List<Voice> = emptyList(),
    val isVoicesLoading: Boolean = false,
    val showDubbingSheet: Boolean = false,
    val showSubtitleSheet: Boolean = false,
    /** my_plan: 편집 화면에 자막을 띄울지. */
    val showSubtitlesOnPreview: Boolean = true,
    /** my_plan: 편집 화면에 더빙을 띄울지. */
    val showDubbingOnPreview: Boolean = true,
    val isSynthesizing: Boolean = false,
    val synthError: String? = null,
    val previewClip: PreviewDubClip? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isVideoSelected: Boolean = false,
    val videoVolume: Float = 1.0f,
    val showVideoVolumeSlider: Boolean = false,
    val showDubVolumeSlider: Boolean = false,
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
    /**
     * 영상 위 우상단 연필 버튼 진입 — segment 편집(복제/삭제/볼륨/속도) 전용 mode.
     * `isRangeSelecting` 와 동시에 true 가 되며, range slider 확정 시 액션 시트(복제/삭제/속도/볼륨)를
     * 띄움. 음성분리 진입과 명확히 분리하기 위한 플래그.
     */
    val isSegmentEditMode: Boolean = false,
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
    val audioSeparation: AudioSeparationUiState? = null,
    /** AudioSeparationSheet 표시 여부 — audioSeparation (데이터) 과 분리해 자동 팝업 회피. */
    val showAudioSeparationSheet: Boolean = false,
    /** Phase 1 commit 후 timeline 재생 시 stem mixer 가 사용. */
    val separationDirectives: List<com.dubcast.shared.domain.model.SeparationDirective> = emptyList(),
    /** EditProject.separationStatus 미러 — 백그라운드 진행/완료 상태 표면화. */
    val separationStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val targetLanguageCode: String = TargetLanguage.CODE_ORIGINAL,
    val enableAutoDubbing: Boolean = false,
    val enableAutoSubtitles: Boolean = false,
    val numberOfSpeakers: Int = 1,
    val showExportOptionsSheet: Boolean = false,
    val autoSubtitleStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val autoDubStatus: AutoJobStatus = AutoJobStatus.IDLE,
    /** my_plan: 언어별 자동 더빙 상태 (en→RUNNING, jp→READY 등). */
    val autoDubStatusByLang: Map<String, AutoJobStatus> = emptyMap(),
    val targetLanguageCodes: List<String> = emptyList(),
    val autoSubtitleError: String? = null,
    val autoDubError: String? = null,
    /** Phase B: 사용자 수정된 자막 → 다른 언어 재생성 진행 상태. */
    val regenerateSubtitleStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val regenerateSubtitleError: String? = null,
    val showRegenerateSubtitleSheet: Boolean = false,
    /** 자막 텍스트 편집 sheet 활성 여부 + 선택된 언어. */
    val showSubtitleEditSheet: Boolean = false,
    val subtitleEditLang: String? = null,
    /** 상세 편집 모드 — timeline 위에 자막 cue list + 스타일 panel 노출. */
    val showDetailEdit: Boolean = false,
    val detailEditLang: String? = null,
    // ── 자막/더빙 생성 패널 ─────────────────────────────────────────────────
    val localizationOpen: Boolean = false,
    /** "subtitle" | "dub" */
    val localizationMode: String = "subtitle",
    val localizationLangs: Set<String> = emptySet(),
    /** 자막 생성 전 STT 스크립트 검토·수정 단계 활성화 여부. dub 모드는 미지원 (BFF 추가 필요). */
    val reviewScriptBeforeGenerate: Boolean = false,
    /** STT only 결과 cue 들. 검토 sheet 의 데이터 source. null = STT 미실행 또는 검토 완료. */
    val pendingReviewCues: List<com.dubcast.shared.domain.usecase.subtitle.ParsedSrtCue>? = null,
    /** 검토 후 진행할 target 언어 코드들 — STT 시작 시 사용자 선택값을 보존. */
    val pendingReviewTargetLangs: List<String> = emptyList(),
    val showScriptReviewSheet: Boolean = false,
    /** STT only 진행 상태 — RUNNING 시 chip spinner. */
    val sttPreflightStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val sttPreflightError: String? = null,
    /** null = 원본, 그 외 = 미리보기로 볼 언어 코드. 비디오 소스 swap 은 미구현 (UI 선택만). */
    val previewLangCode: String? = null,
    /** 언어 코드 → 더빙된 audio mp3 local path. (legacy / export 합성용) */
    val dubbedAudioPaths: Map<String, String> = emptyMap(),
    /** 언어 코드 → BFF 가 video+dubAudio mux 한 mp4 local path. 미리보기 swap source. */
    val dubbedVideoPaths: Map<String, String> = emptyMap(),
    /** Timeline 헤더 "저장" 버튼이 트리거하는 multi-variant 갤러리 저장의 진행 상태. */
    val saveStatus: SaveStatus = SaveStatus.IDLE
) {
    val effectiveTrimEndMs: Long get() = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
    val frameAspectRatio: Float
        get() = if (frameWidth > 0 && frameHeight > 0) {
            frameWidth.toFloat() / frameHeight.toFloat()
        } else 0f
}

data class TimelineSnapshot(
    val segments: List<Segment>,
    val dubClips: List<DubClip>,
    val subtitleClips: List<SubtitleClip>,
    val imageClips: List<ImageClip>,
    val textOverlays: List<TextOverlay> = emptyList(),
    val bgmClips: List<BgmClip> = emptyList(),
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
    private val dubClipRepository: DubClipRepository,
    private val subtitleClipRepository: SubtitleClipRepository,
    private val imageClipRepository: ImageClipRepository,
    private val editProjectRepository: EditProjectRepository,
    private val textOverlayRepository: TextOverlayRepository,
    private val bgmClipRepository: BgmClipRepository,
    private val ttsRepository: TtsRepository,
    private val synthesizeDubClip: SynthesizeDubClipUseCase,
    private val getVoiceList: GetVoiceListUseCase,
    private val moveDubClip: MoveDubClipUseCase,
    private val deleteDubClip: DeleteDubClipUseCase,
    private val addSubtitleClip: AddSubtitleClipUseCase,
    private val deleteSubtitleClip: DeleteSubtitleClipUseCase,
    private val addImageClip: AddImageClipUseCase,
    private val updateImageClip: UpdateImageClipUseCase,
    private val deleteImageClip: DeleteImageClipUseCase,
    private val updateSegmentTrim: UpdateSegmentTrimUseCase,
    private val addVideoSegment: AddVideoSegmentUseCase,
    private val addImageSegment: AddImageSegmentUseCase,
    private val removeSegment: RemoveSegmentUseCase,
    private val updateImageSegmentDuration: UpdateImageSegmentDurationUseCase,
    private val updateImageSegmentPosition: UpdateImageSegmentPositionUseCase,
    private val splitSegment: SplitSegmentUseCase,
    private val duplicateSegmentRange: DuplicateSegmentRangeUseCase,
    private val removeSegmentRange: RemoveSegmentRangeUseCase,
    private val updateSegmentVolume: UpdateSegmentVolumeUseCase,
    private val updateSegmentSpeed: UpdateSegmentSpeedUseCase,
    private val setProjectFrame: SetProjectFrameUseCase,
    private val addTextOverlay: AddTextOverlayUseCase,
    private val updateTextOverlay: UpdateTextOverlayUseCase,
    private val deleteTextOverlay: DeleteTextOverlayUseCase,
    private val duplicateTextOverlay: DuplicateTextOverlayUseCase,
    private val addBgmClip: AddBgmClipUseCase,
    private val updateBgmClip: UpdateBgmClipUseCase,
    private val deleteBgmClip: DeleteBgmClipUseCase,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    private val imageMetadataExtractor: ImageMetadataExtractor,
    private val audioMetadataExtractor: AudioMetadataExtractor,
    private val startAudioSeparation: StartAudioSeparationUseCase,
    private val pollSeparation: PollSeparationUseCase,
    private val generateAutoSubtitles: GenerateAutoSubtitlesUseCase,
    private val regenerateSubtitles: com.dubcast.shared.domain.usecase.subtitle.RegenerateSubtitlesUseCase,
    private val generateOriginalScript: com.dubcast.shared.domain.usecase.subtitle.GenerateOriginalScriptUseCase,
    private val generateAutoDub: GenerateAutoDubUseCase,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
    private val bffBaseUrl: String,
    private val bffApi: com.dubcast.shared.data.remote.api.BffApi,
    private val saveAllVariants: com.dubcast.shared.domain.usecase.save.SaveAllVariantsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState(projectId = projectId))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    /**
     * 저장 완료 후 InputScreen 으로 돌아가는 1회성 신호. UI 가 collect 해 nav stack pop.
     * SaveStatus.DONE 만으로 navigation 트리거하면 재진입 시 즉시 또 pop 되는 사고가 나므로 분리.
     */
    private val _navigateBackHome = MutableSharedFlow<Unit>()
    val navigateBackHome: SharedFlow<Unit> = _navigateBackHome.asSharedFlow()

    private val undoRedoManager = UndoRedoManager<TimelineSnapshot>(maxHistory = 50)
    /**
     * 영상편집 모드 전용 undo 스택 — 메인 timeline 스택과 분리. 모드 진입 시 비우고 baseline 푸시,
     * 모드 종료 (commit/cancel) 시 다시 비운다. 사용자는 영상편집 안에서 한 변경만 영상편집 모드의
     * undo/redo 로 다룰 수 있음.
     */
    private val editModeUndoRedoManager = UndoRedoManager<TimelineSnapshot>(maxHistory = 50)
    /** 영상편집 진입 시 스냅샷 — cancel 시 즉시 복원용. */
    private var preEditBaseline: TimelineSnapshot? = null
    private var hasSeededUndoSnapshot = false

    private fun activeUndoManager(): UndoRedoManager<TimelineSnapshot> =
        if (_uiState.value.isSegmentEditMode) editModeUndoRedoManager else undoRedoManager

    // Auto-trigger gates: prevent re-firing background pipelines on every
    // project emission. ARMED → eligible to fire; FIRED → already running
    // or finished. Reset to ARMED on explicit retry, or on a failure /
    // cancellation that left the project FAILED so the user can retry.
    private enum class TriggerGate { ARMED, FIRED }
    private var subtitleGate = TriggerGate.ARMED
    private var dubGate = TriggerGate.ARMED
    private var separationGate = TriggerGate.ARMED
    private var reviewSheetGate = TriggerGate.ARMED

    init {
        loadSegments()
        // ElevenLabs voices fetch 는 현재 미사용 — 호출 시 500 발생. 로직(loadVoices/getVoiceList)
        // 은 추후 재사용 가능하게 남겨두고 init 단의 호출만 제거.
        // loadVoices()
        observeClips()
        observeProject()
        observeTextOverlays()
        observeBgmClips()
        observeSeparationDirectives()
    }

    private fun observeBgmClips() {
        viewModelScope.launch {
            bgmClipRepository.observeClips(projectId).collect { clips ->
                _uiState.value = _uiState.value.copy(bgmClips = clips)
            }
        }
    }

    private fun observeSeparationDirectives() {
        viewModelScope.launch {
            separationDirectiveRepository.observe(projectId).collect { directives ->
                _uiState.value = _uiState.value.copy(separationDirectives = directives)
            }
        }
    }

    private fun observeProject() {
        viewModelScope.launch {
            editProjectRepository.observeProject(projectId).collect { project ->
                if (project != null) {
                    _uiState.value = _uiState.value.copy(
                        frameWidth = project.frameWidth,
                        frameHeight = project.frameHeight,
                        backgroundColorHex = project.backgroundColorHex,
                        videoScale = project.videoScale,
                        videoOffsetXPct = project.videoOffsetXPct,
                        videoOffsetYPct = project.videoOffsetYPct,
                        targetLanguageCode = project.targetLanguageCode,
                        enableAutoDubbing = project.enableAutoDubbing,
                        enableAutoSubtitles = project.enableAutoSubtitles,
                        numberOfSpeakers = project.numberOfSpeakers,
                        autoSubtitleStatus = project.autoSubtitleStatus,
                        autoDubStatus = project.autoDubStatus,
                        autoDubStatusByLang = project.autoDubStatusByLang,
                        targetLanguageCodes = project.effectiveTargetLanguages
                            .filter { it != TargetLanguage.CODE_ORIGINAL && it.isNotBlank() },
                        autoSubtitleError = project.autoSubtitleError,
                        autoDubError = project.autoDubError,
                        dubbedAudioPaths = project.dubbedAudioPaths,
                        dubbedVideoPaths = project.dubbedVideoPaths,
                        separationStatus = project.separationStatus,
                    )
                    if (!hasSeededUndoSnapshot) {
                        hasSeededUndoSnapshot = true
                        pushUndoState()
                    }
                    maybeTriggerAutoPipelines(project)
                }
            }
        }
    }

    /**
     * Kicks off the BFF subtitle / dub jobs the first time we observe a
     * project that has them enabled but in IDLE state. Each pipeline is
     * gated by an in-memory flag so the trigger does not re-fire when the
     * project flow re-emits during normal use.
     */
    private fun maybeTriggerAutoPipelines(project: EditProject) {
        if (shouldTriggerAutoSubtitle(project)) {
            subtitleGate = TriggerGate.FIRED
            launchAutoSubtitle(project)
        }
        if (shouldTriggerAutoDub(project)) {
            dubGate = TriggerGate.FIRED
            launchAutoDub(project)
        }
        if (shouldResumeSeparation(project)) {
            separationGate = TriggerGate.FIRED
            resumeSeparationPolling(project)
        }
        if (shouldShowPendingReview(project)) {
            reviewSheetGate = TriggerGate.FIRED
            val targets = project.pendingReviewTargetLangsCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            _uiState.value = _uiState.value.copy(
                pendingReviewTargetLangs = targets,
                showScriptReviewSheet = true,
            )
        }
    }

    private fun shouldShowPendingReview(project: EditProject): Boolean =
        reviewSheetGate == TriggerGate.ARMED &&
            !project.pendingReviewTargetLangsCsv.isNullOrBlank() &&
            project.autoSubtitleStatus == AutoJobStatus.READY

    private fun shouldResumeSeparation(project: EditProject): Boolean =
        separationGate == TriggerGate.ARMED &&
            project.separationStatus == AutoJobStatus.RUNNING &&
            !project.separationJobId.isNullOrBlank()

    /**
     * 화면 재진입 또는 앱 재실행 시 영속화된 separationJobId 로 폴링 재개. sheet 는 hidden 으로
     * 두고, 사용자가 "음성 분리" 버튼(상태별 라벨 변경) 으로 결과 확인하러 들어올 수 있게 함.
     */
    private fun resumeSeparationPolling(project: EditProject) {
        val jobId = project.separationJobId ?: return
        val segmentId = project.separationSegmentId ?: return
        // sheet 가 닫혀 있으면 상단 chip spinner 만 — 사용자가 버튼 누르면 hydrate.
        // audioSeparation 이 아직 null 이면 PROCESSING step 으로만 임시 hydrate (UI 표면화는
        // onResumeSeparationSheet 에서).
        if (_uiState.value.audioSeparation == null) {
            _uiState.value = _uiState.value.copy(
                audioSeparation = AudioSeparationUiState(
                    segmentId = segmentId,
                    step = AudioSeparationStep.PROCESSING,
                    numberOfSpeakers = project.separationNumberOfSpeakers,
                    muteOriginalSegmentAudio = project.separationMuteOriginal,
                    jobId = jobId,
                )
            )
        }
        separationJob?.cancel()
        separationJob = viewModelScope.launch { pollSeparationFlow(jobId) }
    }

    private fun shouldTriggerAutoSubtitle(project: EditProject): Boolean =
        subtitleGate == TriggerGate.ARMED &&
            project.enableAutoSubtitles &&
            project.autoSubtitleStatus == AutoJobStatus.IDLE

    private fun shouldTriggerAutoDub(project: EditProject): Boolean =
        dubGate == TriggerGate.ARMED &&
            project.enableAutoDubbing &&
            project.autoDubStatus == AutoJobStatus.IDLE

    private fun launchAutoSubtitle(project: EditProject) {
        val source = _uiState.value.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) {
            // Segments load asynchronously; re-arm so the next project
            // emission with hydrated segments retries.
            subtitleGate = TriggerGate.ARMED
            return
        }
        val sourceLang = "auto"
        val targetLang = project.targetLanguageCode
            .takeIf { it != TargetLanguage.CODE_ORIGINAL }
        viewModelScope.launch {
            try {
                val result = generateAutoSubtitles(
                    projectId = projectId,
                    sourceUri = source,
                    mediaType = "VIDEO",
                    sourceLanguageCode = sourceLang,
                    targetLanguageCodes = listOfNotNull(targetLang),
                    numberOfSpeakers = project.numberOfSpeakers
                )
                // The use case wrote FAILED on its own; re-arm so a fresh
                // retry path (button or status reset) can trigger again.
                if (result.isFailure) subtitleGate = TriggerGate.ARMED
            } catch (e: kotlinx.coroutines.CancellationException) {
                subtitleGate = TriggerGate.ARMED
                throw e
            }
        }
    }

    private fun launchAutoDub(project: EditProject) {
        val source = _uiState.value.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) {
            dubGate = TriggerGate.ARMED
            return
        }
        // my_plan: 다중 더빙 — 사용자가 선택한 N개 언어 각각에 대해 자동 더빙 잡 발행.
        // 결과 mp3 경로는 GenerateAutoDubUseCase 가 EditProject.dubbedAudioPath 단일 필드에
        // 저장하지만 (legacy 호환), 향후 Map<lang, path> 갱신은 별도 작업.
        val targets = project.effectiveTargetLanguages
            .filter { it != TargetLanguage.CODE_ORIGINAL && it.isNotBlank() }
        if (targets.isEmpty()) {
            dubGate = TriggerGate.ARMED
            return
        }
        viewModelScope.launch {
            var anyFailure = false
            targets.forEach { lang ->
                try {
                    val result = generateAutoDub(
                        projectId = projectId,
                        sourceUri = source,
                        mediaType = "VIDEO",
                        sourceLanguageCode = "auto",
                        targetLanguageCode = lang,
                        numberOfSpeakers = project.numberOfSpeakers
                    )
                    if (result.isFailure) anyFailure = true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    dubGate = TriggerGate.ARMED
                    throw e
                }
            }
            if (anyFailure) dubGate = TriggerGate.ARMED
        }
    }

    fun onRetryAutoSubtitles() {
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            // Reset to IDLE so the trigger gate sees a fresh chance.
            editProjectRepository.updateProject(
                project.copy(autoSubtitleStatus = AutoJobStatus.IDLE, autoSubtitleError = null)
            )
            subtitleGate = TriggerGate.ARMED
        }
    }

    fun onRetryAutoDub() {
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            editProjectRepository.updateProject(
                project.copy(autoDubStatus = AutoJobStatus.IDLE, autoDubError = null)
            )
            dubGate = TriggerGate.ARMED
        }
    }

    private fun observeTextOverlays() {
        viewModelScope.launch {
            textOverlayRepository.observeOverlays(projectId).collect { overlays ->
                _uiState.value = _uiState.value.copy(textOverlays = overlays)
            }
        }
    }

    private fun loadSegments() {
        viewModelScope.launch {
            segmentRepository.observeByProjectId(projectId).collect { segments ->
                val first = segments.firstOrNull()
                val total = segments.sumOf { it.effectiveDurationMs }
                val currentSelectedId = _uiState.value.selectedSegmentId
                // Only keep an existing selection if it still references a real
                // segment. No fallback to `first?.id` — the screen should open
                // with nothing selected so the inline edit panel stays hidden
                // until the user actually taps a segment.
                val selectedId = currentSelectedId?.takeIf { id -> segments.any { it.id == id } }
                val selected = segments.firstOrNull { it.id == selectedId }
                val (globalTrimStart, globalTrimEnd) = selectedSegmentGlobalTrim(segments, selected)
                _uiState.value = _uiState.value.copy(
                    segments = segments,
                    selectedSegmentId = selectedId,
                    videoUri = first?.sourceUri.orEmpty(),
                    videoDurationMs = total,
                    videoWidth = first?.width ?: 0,
                    videoHeight = first?.height ?: 0,
                    trimStartMs = globalTrimStart,
                    trimEndMs = globalTrimEnd
                )
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

    private fun loadVoices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVoicesLoading = true)
            val result = getVoiceList()
            val voices = result.getOrDefault(emptyList()).ifEmpty { DEFAULT_VOICES }
            _uiState.value = _uiState.value.copy(
                voices = voices,
                isVoicesLoading = false
            )
        }
    }

    private fun observeClips() {
        viewModelScope.launch {
            combine(
                dubClipRepository.observeClips(projectId),
                subtitleClipRepository.observeClips(projectId),
                imageClipRepository.observeClips(projectId)
            ) { dubs, subs, images -> Triple(dubs, subs, images) }
                .collect { (dubs, subs, images) ->
                    _uiState.value = _uiState.value.copy(
                        dubClips = dubs,
                        subtitleClips = subs,
                        imageClips = images
                    )
                }
        }
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
            dubClips = s.dubClips,
            subtitleClips = s.subtitleClips,
            imageClips = s.imageClips,
            textOverlays = s.textOverlays,
            bgmClips = s.bgmClips,
            frameWidth = s.frameWidth,
            frameHeight = s.frameHeight,
            backgroundColorHex = s.backgroundColorHex,
            videoScale = s.videoScale,
            videoOffsetXPct = s.videoOffsetXPct,
            videoOffsetYPct = s.videoOffsetYPct,
        )
    }

    private fun pushUndoState() {
        activeUndoManager().pushState(buildSnapshot())
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        val mgr = activeUndoManager()
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

    /** my_plan: 편집 화면에 자막 오버레이 표시 토글. */
    fun onToggleSubtitlesOnPreview() {
        _uiState.value = _uiState.value.copy(showSubtitlesOnPreview = !_uiState.value.showSubtitlesOnPreview)
    }

    /** my_plan: 편집 화면에 더빙 표시 토글. */
    fun onToggleDubbingOnPreview() {
        _uiState.value = _uiState.value.copy(showDubbingOnPreview = !_uiState.value.showDubbingOnPreview)
    }

    fun onShowDubbingSheet() {
        _uiState.value = _uiState.value.copy(showDubbingSheet = true, synthError = null)
    }

    fun onDismissDubbingSheet() {
        _uiState.value = _uiState.value.copy(
            showDubbingSheet = false,
            synthError = null,
            previewClip = null
        )
    }

    fun onShowSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleSheet = true)
    }

    fun onDismissSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
    }

    fun onSynthesize(text: String, voiceId: String, voiceName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSynthesizing = true, synthError = null, previewClip = null)

            val result = ttsRepository.synthesize(text, voiceId)

            result.fold(
                onSuccess = { ttsResult ->
                    _uiState.value = _uiState.value.copy(
                        isSynthesizing = false,
                        previewClip = PreviewDubClip(
                            text = text,
                            voiceId = voiceId,
                            voiceName = voiceName,
                            audioFilePath = ttsResult.localAudioPath,
                            durationMs = ttsResult.durationMs
                        )
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSynthesizing = false,
                        synthError = error.message
                    )
                }
            )
        }
    }

    fun onInsertPreviewClip() {
        val preview = _uiState.value.previewClip ?: return
        viewModelScope.launch {
            val dubClipId = Uuid.random().toString()
            val startMs = _uiState.value.playbackPositionMs
            val clip = DubClip(
                id = dubClipId,
                projectId = projectId,
                text = preview.text,
                voiceId = preview.voiceId,
                voiceName = preview.voiceName,
                audioFilePath = preview.audioFilePath,
                startMs = startMs,
                durationMs = preview.durationMs
            )
            dubClipRepository.addClip(clip)

            _uiState.value = _uiState.value.copy(
                showDubbingSheet = false,
                previewClip = null
            )
            pushUndoState()
        }
    }

    fun onShowRegenerateSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showRegenerateSubtitleSheet = true)
    }

    fun onDismissRegenerateSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showRegenerateSubtitleSheet = false)
    }

    fun onToggleDetailEdit() {
        val s = _uiState.value
        val nextOpen = !s.showDetailEdit
        val firstLang = if (nextOpen) {
            s.detailEditLang ?: s.subtitleClips.map { it.languageCode }
                .firstOrNull { it.isNotBlank() }
        } else null
        _uiState.value = s.copy(
            showDetailEdit = nextOpen,
            detailEditLang = firstLang,
            // 상세 편집 닫을 때 선택 해제.
            selectedSubtitleClipId = if (nextOpen) s.selectedSubtitleClipId else null,
        )
    }

    fun onSetDetailEditLang(lang: String) {
        _uiState.value = _uiState.value.copy(
            detailEditLang = lang,
            selectedSubtitleClipId = null,
        )
    }

    fun onShowSubtitleEditSheet(initialLang: String? = null) {
        val firstLang = initialLang
            ?: _uiState.value.subtitleClips.map { it.languageCode }.firstOrNull { it.isNotBlank() }
        _uiState.value = _uiState.value.copy(
            showSubtitleEditSheet = true,
            subtitleEditLang = firstLang,
        )
    }

    fun onDismissSubtitleEditSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleEditSheet = false)
    }

    fun onSetSubtitleEditLang(lang: String) {
        _uiState.value = _uiState.value.copy(subtitleEditLang = lang)
    }

    /**
     * 자막 cue 의 텍스트를 사용자가 inline 편집한 결과 저장. EditSubtitleClipUseCase 로 위임.
     * 빈 텍스트는 무시 (clip 자체 삭제는 별도 메서드에서 처리).
     */
    fun onUpdateSubtitleText(clipId: String, newText: String) {
        viewModelScope.launch {
            val clip = subtitleClipRepository.getClip(clipId) ?: return@launch
            val trimmed = newText.trim()
            if (trimmed.isEmpty() || trimmed == clip.text.trim()) return@launch
            subtitleClipRepository.updateClip(clip.copy(text = trimmed))
            pushUndoState()
        }
    }

    /**
     * 사용자가 수정한 source 언어 자막을 기반으로 다른 언어 자막 재생성. BFF 가 Gemini 호출 →
     * SRT 다운로드 → 해당 lang 자막 덮어쓰기. 진행률은 `regenerateSubtitleStatus` 로 표면화.
     */
    fun onRegenerateOtherLanguages(sourceLanguageCode: String, targetLanguageCodes: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                regenerateSubtitleStatus = AutoJobStatus.RUNNING,
                regenerateSubtitleError = null,
                showRegenerateSubtitleSheet = false,
            )
            // EditProject.autoSubtitleStatus 도 같이 RUNNING — 미리보기 chip 등 모든 자막 진행 indicator 일관.
            editProjectRepository.getProject(projectId)?.let { p ->
                editProjectRepository.updateProject(
                    p.copy(autoSubtitleStatus = AutoJobStatus.RUNNING, autoSubtitleError = null)
                )
            }
            val result = regenerateSubtitles(
                projectId = projectId,
                sourceLanguageCode = sourceLanguageCode,
                targetLanguageCodes = targetLanguageCodes,
            ) { progress, _ ->
                // 진행률 자체는 chip spinner 만 — 세부 % 노출 안 함.
                if (progress >= 100) Unit
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(regenerateSubtitleStatus = AutoJobStatus.IDLE)
                    editProjectRepository.getProject(projectId)?.let { p ->
                        editProjectRepository.updateProject(
                            p.copy(autoSubtitleStatus = AutoJobStatus.READY, autoSubtitleError = null)
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        regenerateSubtitleStatus = AutoJobStatus.FAILED,
                        regenerateSubtitleError = err.message,
                    )
                    editProjectRepository.getProject(projectId)?.let { p ->
                        editProjectRepository.updateProject(
                            p.copy(autoSubtitleStatus = AutoJobStatus.FAILED, autoSubtitleError = err.message)
                        )
                    }
                }
            )
        }
    }

    fun onAddSubtitle(
        text: String,
        startMs: Long,
        endMs: Long,
        position: SubtitlePosition,
        fontFamily: String = com.dubcast.shared.domain.model.SubtitleClip.DEFAULT_FONT_FAMILY,
        fontSizeSp: Float = com.dubcast.shared.domain.model.SubtitleClip.DEFAULT_FONT_SIZE_SP,
        colorHex: String = com.dubcast.shared.domain.model.SubtitleClip.DEFAULT_COLOR_HEX,
        backgroundColorHex: String = com.dubcast.shared.domain.model.SubtitleClip.DEFAULT_BACKGROUND_COLOR_HEX,
    ) {
        viewModelScope.launch {
            addSubtitleClip(
                projectId, text, startMs, endMs, position,
                fontFamily, fontSizeSp, colorHex, backgroundColorHex,
            )
            _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
            pushUndoState()
        }
    }

    fun onMoveDubClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            moveDubClip(clip, newStartMs, _uiState.value.videoDurationMs)
            pushUndoState()
        }
    }

    /**
     * Single-selection model: at any moment at most one of segment / dub /
     * subtitle / image / text-overlay / bgm may be selected. This helper
     * applies a tap-toggle on the chosen target while clearing every other
     * selected*Id, so each handler is a one-line wrapper.
     */
    private enum class SelectionTarget {
        Segment, Dub, Subtitle, Image, TextOverlay, Bgm
    }

    private fun selectExclusively(target: SelectionTarget, id: String?) {
        val state = _uiState.value
        // Tap-toggle: tapping the already-selected element clears it.
        val current = when (target) {
            SelectionTarget.Segment -> state.selectedSegmentId
            SelectionTarget.Dub -> state.selectedDubClipId
            SelectionTarget.Subtitle -> state.selectedSubtitleClipId
            SelectionTarget.Image -> state.selectedImageClipId
            SelectionTarget.TextOverlay -> state.selectedTextOverlayId
            SelectionTarget.Bgm -> state.selectedBgmClipId
        }
        val next = if (id != null && id == current) null else id
        _uiState.value = state.copy(
            selectedSegmentId = if (target == SelectionTarget.Segment) next else null,
            selectedDubClipId = if (target == SelectionTarget.Dub) next else null,
            selectedSubtitleClipId = if (target == SelectionTarget.Subtitle) next else null,
            selectedImageClipId = if (target == SelectionTarget.Image) next else null,
            selectedTextOverlayId = if (target == SelectionTarget.TextOverlay) next else null,
            selectedBgmClipId = if (target == SelectionTarget.Bgm) next else null,
            isVideoSelected = false,
            showVideoVolumeSlider = false,
            showDubVolumeSlider = false
        )
    }

    fun onSelectDubClip(clipId: String?) = selectExclusively(SelectionTarget.Dub, clipId)

    fun onSelectSubtitleClip(clipId: String?) = selectExclusively(SelectionTarget.Subtitle, clipId)

    fun onSelectImageClip(clipId: String?) = selectExclusively(SelectionTarget.Image, clipId)

    fun onUpdateDubClipVolume(clipId: String, volume: Float) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            val clamped = volume.coerceIn(0f, 2f)
            if (clamped == clip.volume) return@launch
            dubClipRepository.updateClip(clip.copy(volume = clamped))
            pushUndoState()
        }
    }

    fun onDeleteSelectedClip() {
        viewModelScope.launch {
            val state = _uiState.value
            state.selectedDubClipId?.let { dubClipId ->
                deleteDubClip(dubClipId)
                subtitleClipRepository.deleteClipsBySourceDubClipId(dubClipId)
            }
            state.selectedSubtitleClipId?.let { deleteSubtitleClip(it) }
            state.selectedImageClipId?.let { deleteImageClip(it) }
            _uiState.value = _uiState.value.copy(
                selectedDubClipId = null,
                selectedSubtitleClipId = null,
                selectedImageClipId = null
            )
            pushUndoState()
        }
    }

    fun onInsertImage(uri: String, defaultDurationMs: Long = 3000L) {
        viewModelScope.launch {
            val state = _uiState.value
            val videoDurationMs = state.videoDurationMs
            val maxStart = if (videoDurationMs > 0L) (videoDurationMs - 500L).coerceAtLeast(0L) else Long.MAX_VALUE
            // Avoid stacking new sticker on top of an existing one at the same
            // start time — shift right in 250ms increments until a free spot.
            val taken = state.imageClips.map { it.startMs }.toSet()
            var startMs = state.playbackPositionMs.coerceIn(0L, maxStart)
            while (startMs in taken && startMs < maxStart) startMs = (startMs + 250L).coerceAtMost(maxStart)
            val maxEnd = if (videoDurationMs > 0L) videoDurationMs else (startMs + defaultDurationMs)
            val endMs = (startMs + defaultDurationMs)
                .coerceAtMost(maxEnd)
                .coerceAtLeast(startMs + 500L)
            addImageClip(
                projectId = projectId,
                imageUri = uri,
                startMs = startMs,
                endMs = endMs,
                lane = pickFreeOverlayLane(startMs, endMs)
            )
            pushUndoState()
        }
    }

    fun onMoveImageClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.imageClips.find { it.id == clipId } ?: return@launch
            val duration = clip.endMs - clip.startMs
            val videoDuration = _uiState.value.videoDurationMs
            val coercedStart = newStartMs.coerceAtLeast(0L).let {
                if (videoDuration > 0L) it.coerceAtMost((videoDuration - duration).coerceAtLeast(0L)) else it
            }
            updateImageClip(clip.copy(startMs = coercedStart, endMs = coercedStart + duration))
            pushUndoState()
        }
    }

    /**
     * Pick the lowest lane number free of time-overlap with both image clips
     * AND text overlays — they share lanes on the merged overlay track.
     */
    private fun pickFreeOverlayLane(startMs: Long, endMs: Long): Int {
        val state = _uiState.value
        // Treat both lists as one virtual collection of (lane, start, end)
        // tuples and reuse the shared lane-packing helper.
        data class LaneItem(val lane: Int, val start: Long, val end: Long)
        val combined = state.imageClips.map { LaneItem(it.lane, it.startMs, it.endMs) } +
            state.textOverlays.map { LaneItem(it.lane, it.startMs, it.endMs) }
        return com.dubcast.shared.domain.util.pickLowestFreeLane(
            existing = combined,
            startMs = startMs,
            endMs = endMs,
            laneOf = { it.lane },
            startOf = { it.start },
            endOf = { it.end }
        )
    }

    fun onChangeImageClipLane(clipId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val clip = imageClipRepository.getClip(clipId) ?: return@launch
            val newLane = (clip.lane + delta).coerceAtLeast(0)
            if (newLane == clip.lane) return@launch
            updateImageClip(clip.copy(lane = newLane))
            pushUndoState()
        }
    }

    fun onDuplicateImageClip(clipId: String) {
        viewModelScope.launch {
            val clip = imageClipRepository.getClip(clipId) ?: return@launch
            // Place the duplicate at the same time position so it sits directly
            // beside the source on the timeline. AddImageClipUseCase auto-picks
            // the lowest free lane that doesn't time-conflict, which pushes the
            // copy to the row right below the original.
            addImageClip(
                projectId = projectId,
                imageUri = clip.imageUri,
                startMs = clip.startMs,
                endMs = clip.endMs,
                xPct = clip.xPct,
                yPct = clip.yPct,
                widthPct = clip.widthPct,
                heightPct = clip.heightPct,
                lane = pickFreeOverlayLane(clip.startMs, clip.endMs)
            )
            pushUndoState()
        }
    }

    fun onResizeImageClipDuration(clipId: String, newEndMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.imageClips.find { it.id == clipId } ?: return@launch
            val minEnd = clip.startMs + 500L
            val videoDuration = _uiState.value.videoDurationMs
            val coercedEnd = newEndMs.coerceAtLeast(minEnd).let {
                if (videoDuration > 0L) it.coerceAtMost(videoDuration) else it
            }
            updateImageClip(clip.copy(endMs = coercedEnd))
            pushUndoState()
        }
    }

    fun onUpdateImageClipPosition(
        clipId: String,
        xPct: Float,
        yPct: Float,
        widthPct: Float,
        heightPct: Float
    ) {
        viewModelScope.launch {
            val clip = imageClipRepository.getClip(clipId) ?: return@launch
            updateImageClip(
                clip.copy(xPct = xPct, yPct = yPct, widthPct = widthPct, heightPct = heightPct)
            )
            pushUndoState()
        }
    }

    fun onUpdateSubtitlePosition(
        clipId: String,
        xPct: Float,
        yPct: Float,
        widthPct: Float,
        heightPct: Float
    ) {
        viewModelScope.launch {
            val clip = subtitleClipRepository.getClip(clipId) ?: return@launch
            subtitleClipRepository.updateClip(
                clip.copy(xPct = xPct, yPct = yPct, widthPct = widthPct, heightPct = heightPct)
            )
            pushUndoState()
        }
    }

    /**
     * 한 언어의 모든 자막에 스타일 일괄 적용. 사용자가 편집 sheet 의 스타일 슬라이더/chip 을
     * 조작했을 때 호출. applyToAllLanguages=true 면 같은 cue 시점의 다른 언어 자막에도 동일 스타일
     * 미러 (다국어 일관성).
     */
    fun onUpdateSubtitleStyleForLanguage(
        lang: String,
        fontFamily: String? = null,
        fontSizeSp: Float? = null,
        colorHex: String? = null,
        backgroundColorHex: String? = null,
        applyToAllLanguages: Boolean = true,
    ) {
        viewModelScope.launch {
            val all = _uiState.value.subtitleClips
            val targetClips = if (applyToAllLanguages) all else all.filter { it.languageCode == lang }
            // applyToAllLanguages=false 면 lang 만, true 면 전체 — UX 단순화 차원에서 같은 시점 매칭
            // 안 하고 그냥 전체 자막을 동일 스타일로 통일.
            for (clip in targetClips) {
                subtitleClipRepository.updateClip(
                    clip.copy(
                        fontFamily = fontFamily ?: clip.fontFamily,
                        fontSizeSp = fontSizeSp ?: clip.fontSizeSp,
                        colorHex = colorHex ?: clip.colorHex,
                        backgroundColorHex = backgroundColorHex ?: clip.backgroundColorHex,
                    )
                )
            }
            pushUndoState()
        }
    }

    /**
     * 자막 스타일(폰트/크기/색/배경색) 업데이트. 단일 필드만 바꾸려면 다른 인자에 null 전달.
     * 다국어 동시 편집 정책: 같은 cue 시점의 다른 언어 자막에도 동일 스타일 일괄 적용 (UX 일관성).
     */
    fun onUpdateSubtitleStyle(
        clipId: String,
        fontFamily: String? = null,
        fontSizeSp: Float? = null,
        colorHex: String? = null,
        backgroundColorHex: String? = null,
        applyToAllLanguages: Boolean = true,
    ) {
        viewModelScope.launch {
            val clip = subtitleClipRepository.getClip(clipId) ?: return@launch
            val updated = clip.copy(
                fontFamily = fontFamily ?: clip.fontFamily,
                fontSizeSp = fontSizeSp ?: clip.fontSizeSp,
                colorHex = colorHex ?: clip.colorHex,
                backgroundColorHex = backgroundColorHex ?: clip.backgroundColorHex,
            )
            subtitleClipRepository.updateClip(updated)
            // 같은 cue 시점의 다른 언어 자막도 같이 갱신 — 사용자가 언어별로 따로 스타일 맞추는 일은 드묾.
            if (applyToAllLanguages) {
                val all = _uiState.value.subtitleClips.filter { sib ->
                    sib.id != clip.id &&
                        sib.startMs == clip.startMs && sib.endMs == clip.endMs
                }
                all.forEach { sib ->
                    subtitleClipRepository.updateClip(
                        sib.copy(
                            fontFamily = updated.fontFamily,
                            fontSizeSp = updated.fontSizeSp,
                            colorHex = updated.colorHex,
                            backgroundColorHex = updated.backgroundColorHex,
                        )
                    )
                }
            }
            pushUndoState()
        }
    }

    fun onUndo() {
        viewModelScope.launch {
            val snapshot = activeUndoManager().undo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
        }
    }

    fun onRedo() {
        viewModelScope.launch {
            val snapshot = activeUndoManager().redo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
        }
    }

    private suspend fun restoreSnapshot(snapshot: TimelineSnapshot) {
        dubClipRepository.deleteAllClips(projectId)
        for (clip in snapshot.dubClips) {
            dubClipRepository.addClip(clip)
        }
        subtitleClipRepository.deleteAllClips(projectId)
        for (clip in snapshot.subtitleClips) {
            subtitleClipRepository.addClip(clip)
        }
        imageClipRepository.deleteAllClips(projectId)
        for (clip in snapshot.imageClips) {
            imageClipRepository.addClip(clip)
        }
        segmentRepository.deleteAllByProjectId(projectId)
        for (seg in snapshot.segments) {
            segmentRepository.addSegment(seg)
        }
        textOverlayRepository.deleteAllByProjectId(projectId)
        for (overlay in snapshot.textOverlays) {
            textOverlayRepository.addOverlay(overlay)
        }
        bgmClipRepository.deleteAllByProjectId(projectId)
        for (bgm in snapshot.bgmClips) {
            bgmClipRepository.addClip(bgm)
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
                isVideoSelected = true,
                selectedDubClipId = null,
                selectedSubtitleClipId = null,
                selectedImageClipId = null
            )
        }
    }

    fun onDeselectVideo() {
        _uiState.value = _uiState.value.copy(isVideoSelected = false, showVideoVolumeSlider = false)
    }

    fun onToggleVideoVolumeSlider() {
        _uiState.value = _uiState.value.copy(showVideoVolumeSlider = !_uiState.value.showVideoVolumeSlider)
    }

    fun onToggleDubVolumeSlider() {
        _uiState.value = _uiState.value.copy(showDubVolumeSlider = !_uiState.value.showDubVolumeSlider)
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

    /**
     * Timeline 헤더 "저장" 버튼 — 모든 variant (`original` + 자막/더빙 lang 들) 를 BG 에서 렌더한 뒤
     * 갤러리에 저장. 완료 후 EditProject 삭제 + InputScreen 으로 navigate.
     *
     * 진행 상태는 [TimelineUiState.saveStatus] 로 노출 (UI 에서 snackbar / 버튼 라벨).
     */
    fun onSaveAllVariants() {
        if (_uiState.value.saveStatus is SaveStatus.RUNNING) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.RUNNING(0))
            val result = saveAllVariants(projectId) { percent ->
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.RUNNING(percent))
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.DONE)
                    // 저장 성공 — EditProject 삭제 후 InputScreen 복귀 신호. drafts 가 사라졌으므로
                    // 사용자는 새 영상 선택부터 다시.
                    runCatching { editProjectRepository.deleteProject(projectId) }
                    _navigateBackHome.emit(Unit)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        saveStatus = SaveStatus.FAILED(e.message ?: "저장 실패")
                    )
                }
            )
        }
    }

    /** Snackbar 닫기 등 — UI 에서 호출 후 idle 로 복귀. */
    fun onClearSaveStatus() {
        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.IDLE)
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

    fun onAppendImageSegment(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showAppendSheet = false, isPlaying = false)
            val info = imageMetadataExtractor.extract(uri) ?: return@launch
            addImageSegment(projectId = projectId, imageInfo = info)
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

    fun onUpdateImageSegmentDuration(segmentId: String, durationMs: Long) {
        viewModelScope.launch {
            updateImageSegmentDuration(segmentId, durationMs)
            pushUndoState()
        }
    }

    fun onResizeImageSegmentByDrag(segmentId: String, requestedDurationMs: Long) {
        viewModelScope.launch {
            val seg = _uiState.value.segments.firstOrNull { it.id == segmentId } ?: return@launch
            if (seg.type != SegmentType.IMAGE) return@launch
            val clamped = requestedDurationMs.coerceIn(MIN_IMAGE_DURATION_MS, MAX_IMAGE_DURATION_MS)
            if (clamped == seg.durationMs) return@launch
            updateImageSegmentDuration(segmentId, clamped)
            pushUndoState()
        }
    }

    fun onUpdateImageSegmentPosition(
        segmentId: String,
        xPct: Float,
        yPct: Float,
        widthPct: Float,
        heightPct: Float
    ) {
        viewModelScope.launch {
            updateImageSegmentPosition(segmentId, xPct, yPct, widthPct, heightPct)
            pushUndoState()
        }
    }

    /**
     * 분리 directive 와 겹치지 않는 자유 구간들 — segment 영역 [segStart, segEnd] 안에서.
     * 빈 리스트면 segment 전체가 이미 분리됨 (range 진입 X).
     */
    private fun freeIntervalsInSegment(segStart: Long, segEnd: Long): List<LongRange> {
        val occupied = _uiState.value.separationDirectives
            .map { it.rangeStartMs..it.rangeEndMs }
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
     * 진입 즉시 현 timeline 스냅샷을 [preEditBaseline] 에 저장 — 취소(X) 시 즉시 복원.
     * 영상편집 모드의 undo 스택은 새로 시드되어 메인 timeline 스택과 분리된다.
     */
    fun onEnterSegmentEditMode(segmentId: String) {
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
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            pendingRangeStartMs = segStart,
            pendingRangeEndMs = segEnd,
            showRangeActionSheet = false,
            pendingRangeVolume = seg.volumeScale,
            pendingRangeSpeed = seg.speedScale,
            isPlaying = false,
            previewLangCode = null,
            showAudioSeparationSheet = false,
            showSubtitleSheet = false,
            showDubbingSheet = false,
            showSubtitleEditSheet = false,
            showRegenerateSubtitleSheet = false,
            showScriptReviewSheet = false,
            localizationOpen = false,
            showDetailEdit = false,
            showAppendSheet = false,
        )
        // buildSnapshot 는 non-suspend (in-memory) — 즉시 baseline 시드.
        preEditBaseline = buildSnapshot()
        editModeUndoRedoManager.clear()
        editModeUndoRedoManager.pushState(preEditBaseline!!)
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
        val state = _uiState.value
        if (!state.isSegmentEditMode) return
        if (state.selectedSegmentId == segmentId &&
            state.pendingRangeEndMs > state.pendingRangeStartMs
        ) {
            _uiState.value = state.copy(
                selectedSegmentId = null,
                rangeTargetSegmentId = null,
                pendingRangeStartMs = 0L,
                pendingRangeEndMs = 0L,
            )
            return
        }
        selectSegmentInEditInternal(segmentId)
    }

    /**
     * 토글 없이 강제 select — 시스템에서 호출 (apply/duplicate/delete 직후 새 middle 로 reselect).
     * 사용자 탭 동작과 분리해 race 가 deselect 로 빠지지 않게 한다.
     */
    private fun selectSegmentInEditInternal(segmentId: String) {
        val state = _uiState.value
        if (!state.isSegmentEditMode) return
        val seg = state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        val segStart = segmentStartOffsetMs(state.segments, seg.id)
        val segEnd = segStart + seg.effectiveDurationMs
        _uiState.value = state.copy(
            rangeTargetSegmentId = seg.id,
            selectedSegmentId = seg.id,
            pendingRangeStartMs = segStart,
            pendingRangeEndMs = segEnd,
            pendingRangeVolume = seg.volumeScale,
            pendingRangeSpeed = seg.speedScale,
            playbackPositionMs = segStart,
        )
    }

    /**
     * range 모드 (음원분리/영상편집) 안에서 "구간 선택만" 비움 — 모드 자체는 유지.
     * 사용자가 다른 segment/free interval 을 탭하면 다시 selection 생성.
     */
    fun onClearRangeSelection() {
        val state = _uiState.value
        if (!state.isRangeSelecting) return
        _uiState.value = state.copy(
            selectedSegmentId = null,
            rangeTargetSegmentId = null,
            pendingRangeStartMs = 0L,
            pendingRangeEndMs = 0L,
        )
    }

    fun onCancelSegmentEditMode() {
        _uiState.value = _uiState.value.copy(
            isRangeSelecting = false,
            isSegmentEditMode = false,
            rangeTargetSegmentId = null,
            showRangeActionSheet = false,
        )
        editModeUndoRedoManager.clear()
        preEditBaseline = null
        updateUndoRedoState()
    }

    /**
     * 영상편집 모드의 X(취소) — 편집 진입 직전 스냅샷으로 즉시 복원하고 모드 종료.
     * 사용자가 영상편집 중 적용한 복제/삭제/볼륨/속도 변경을 모두 무효화.
     */
    fun onCancelSegmentEditChanges() {
        val baseline = preEditBaseline
        viewModelScope.launch {
            if (baseline != null) {
                restoreSnapshot(baseline)
                // restoreSnapshot 은 Room 만 복원 → state.segments 는 collector emit 에 의존하는데
                // emit 지연 시 사용자가 변경 후 segments 를 그대로 보게 되어 "적용된 것처럼" 보임.
                // 즉시 fetch 로 강제 동기화.
                refreshSegmentsStateFromDb()
            }
            preEditBaseline = null
            editModeUndoRedoManager.clear()
            _uiState.update {
                it.copy(
                    isRangeSelecting = false,
                    isSegmentEditMode = false,
                    rangeTargetSegmentId = null,
                    showRangeActionSheet = false,
                    selectedSegmentId = null,
                )
            }
            updateUndoRedoState()
        }
    }

    /**
     * 영상편집 모드의 ✓(체크) — 편집 확정. 영상 segment 자체는 그대로 두고, 음원분리·자막·더빙
     * 결과와 메인 timeline undo 스택을 모두 초기화 (사용자에게 안내문구로 명시).
     * BFF 호출 없음 — 단순 로컬 상태 리셋만.
     */
    fun onCommitSegmentEdit() {
        viewModelScope.launch {
            resetTimelineDerivedResults()
            editModeUndoRedoManager.clear()
            preEditBaseline = null
            undoRedoManager.clear()
            _uiState.value = _uiState.value.copy(
                isRangeSelecting = false,
                isSegmentEditMode = false,
                rangeTargetSegmentId = null,
                showRangeActionSheet = false,
                selectedSegmentId = null,
                previewLangCode = null,
            )
            // 메인 스택 baseline 재시드.
            pushUndoState()
        }
    }

    /**
     * onCommitSegmentEdit 시 호출되는 reset — 영상편집 결과로 음원분리/자막/더빙이 timeline 과
     * 어긋나므로 모두 초기화. 사용자는 다시 음원분리/자막/더빙 생성 단계를 거쳐야 한다.
     */
    private suspend fun resetTimelineDerivedResults() {
        // 1) 음원분리 directive 모두 삭제 + 영상 전체 음소거 해제
        _uiState.value.separationDirectives.forEach { separationDirectiveRepository.delete(it.id) }
        _uiState.value.segments.filter { it.type == SegmentType.VIDEO }
            .forEach { updateSegmentVolume(it.id, 1f) }
        // 2) 자막 / 더빙 클립 모두 삭제
        subtitleClipRepository.deleteAllClips(projectId)
        dubClipRepository.deleteAllClips(projectId)
        // 3) 진행 중 잡 폴링 취소
        separationJob?.cancel()
        separationJob = null
        // 4) EditProject 의 자동 잡/더빙 결과 필드 초기화
        editProjectRepository.getProject(projectId)?.let { p ->
            editProjectRepository.updateProject(
                p.copy(
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
                    separationJobId = null,
                    separationSegmentId = null,
                    separationStatus = AutoJobStatus.IDLE,
                    separationError = null,
                    pendingReviewTargetLangsCsv = null,
                )
            )
        }
        // 5) auto-trigger 게이트 재무장 (다음 자막/더빙 생성 시 정상 트리거)
        subtitleGate = TriggerGate.ARMED
        dubGate = TriggerGate.ARMED
        separationGate = TriggerGate.ARMED
        reviewSheetGate = TriggerGate.ARMED
        // 6) UI 상태 — sheet 닫고 audioSeparation hydrate 데이터도 클리어
        _uiState.value = _uiState.value.copy(
            audioSeparation = null,
            showAudioSeparationSheet = false,
            sttPreflightStatus = AutoJobStatus.IDLE,
            sttPreflightError = null,
            regenerateSubtitleStatus = AutoJobStatus.IDLE,
            regenerateSubtitleError = null,
            pendingReviewCues = null,
            pendingReviewTargetLangs = emptyList(),
            showScriptReviewSheet = false,
            showSubtitleEditSheet = false,
            showRegenerateSubtitleSheet = false,
            showDetailEdit = false,
        )
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
            val speed = if (seg.speedScale > 0f) seg.speedScale else 1f
            val localStart = seg.trimStartMs + ((overlapStart - segGlobalStart) * speed).toLong()
            val localEnd = seg.trimStartMs + ((overlapEnd - segGlobalStart) * speed).toLong()
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
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            var lastDuplicated: com.dubcast.shared.domain.model.Segment? = null
            slices.forEach { s ->
                lastDuplicated = duplicateSegmentRange(s.segmentId, s.localStart, s.localEnd)
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
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            slices.forEach { s -> removeSegmentRange(s.segmentId, s.localStart, s.localEnd) }
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
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            var lastMiddleId: String? = null
            slices.forEach { s ->
                val r = splitSegment(s.segmentId, s.localStart, s.localEnd)
                updateSegmentVolume(r.middle.id, value)
                lastMiddleId = r.middle.id
            }
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
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        val wasSegmentEdit = state.isSegmentEditMode
        val newSpeed = if (value > 0f) value else 1f
        resetRangeMode()
        viewModelScope.launch {
            var lastMiddleId: String? = null
            slices.forEach { s ->
                // 사용자가 글로벌 timeline 에서 선택한 영역 = 새 speedScale 적용 후에도 동일 글로벌 길이
                // 유지. sliceGlobalRange 는 현재 segment.speedScale 반영해 source 좌표로 변환했으므로
                // origLocalLen = (글로벌 길이) × (현재 speed). 새 speed 적용 후 글로벌 길이를
                // 그대로 두려면 source 영역을 (newSpeed / curSpeed) 배 확장해야.
                // 즉 사용자 직관 = "선택 구간만 빠르게/느리게" 와 일치.
                val parent = _uiState.value.segments.firstOrNull { it.id == s.segmentId }
                val curSpeed = parent?.speedScale?.takeIf { it > 0f } ?: 1f
                val parentTrimEnd = parent?.let {
                    if (it.trimEndMs > 0L) it.trimEndMs else it.durationMs
                } ?: s.localEnd
                val origLocalLen = s.localEnd - s.localStart
                val targetLocalLen = (origLocalLen.toDouble() * newSpeed / curSpeed).toLong()
                    .coerceAtLeast(origLocalLen)
                val expandedLocalEnd = (s.localStart + targetLocalLen).coerceAtMost(parentTrimEnd)
                val r = splitSegment(s.segmentId, s.localStart, expandedLocalEnd)
                updateSegmentSpeed(r.middle.id, value)
                lastMiddleId = r.middle.id
            }
            refreshSegmentsStateFromDb()
            if (wasSegmentEdit) {
                lastMiddleId?.let { selectSegmentInEditInternal(it) }
            }
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
            _uiState.value = state.copy(frameError = "Width와 Height는 양의 정수")
            return
        }
        if (width > MAX_FRAME_DIMENSION || height > MAX_FRAME_DIMENSION) {
            _uiState.value = state.copy(frameError = "최대 ${MAX_FRAME_DIMENSION}px")
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
                _uiState.value = _uiState.value.copy(frameError = e.message ?: "Invalid frame")
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
            _uiState.value = state.copy(frameError = "Width와 Height는 양의 정수")
            return
        }
        if (width > MAX_FRAME_DIMENSION || height > MAX_FRAME_DIMENSION) {
            _uiState.value = state.copy(frameError = "최대 ${MAX_FRAME_DIMENSION}px")
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
                _uiState.value = _uiState.value.copy(frameError = e.message ?: "Invalid frame")
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
            _uiState.value = state.copy(textOverlayError = "Text는 비어있을 수 없음")
            return
        }
        if (state.pendingOverlayEndMs <= state.pendingOverlayStartMs) {
            _uiState.value = state.copy(textOverlayError = "End는 Start보다 커야 함")
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
                    textOverlayError = e.message ?: "Invalid text overlay"
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
            deleteTextOverlay(overlayId)
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
                        bgmError = "오디오 메타데이터를 읽지 못함"
                    )
                    return@launch
                }
                val startMs = _uiState.value.playbackPositionMs
                addBgmClip(
                    projectId = projectId,
                    sourceUri = uri,
                    sourceDurationMs = info.durationMs,
                    startMs = startMs,
                    volumeScale = 1.0f
                )
                _uiState.value = _uiState.value.copy(isAddingBgm = false)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(
                    isAddingBgm = false,
                    bgmError = e.message ?: "BGM을 추가하지 못함"
                )
            }
        }
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
     * BGM 클립을 source 로 자막 자동 생성. 영상 segment 흐름과 동일한 GenerateAutoSubtitlesUseCase
     * 재사용 — mediaType=AUDIO 만 다름. invalidation 책임은 use case 가 EditProject autoSubtitleStatus
     * 기록 + 자막 클립 갱신으로 이미 처리.
     */
    fun onGenerateAutoSubtitlesForBgmClip(clipId: String, targetLanguageCodes: List<String>) {
        viewModelScope.launch {
            val bgm = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return@launch
            val r = generateAutoSubtitles(
                projectId = projectId,
                sourceUri = bgm.sourceUri,
                mediaType = "AUDIO",
                sourceLanguageCode = "auto",
                targetLanguageCodes = targetLanguageCodes,
                numberOfSpeakers = 1,
            )
            if (r.isFailure) {
                _uiState.value = _uiState.value.copy(
                    autoSubtitleError = r.exceptionOrNull()?.message ?: "자막 생성 실패",
                )
            }
        }
    }

    /**
     * BGM 클립을 source 로 더빙 자동 생성. mediaType=AUDIO. 단일 lang per call (영상 흐름과 동등).
     */
    fun onGenerateAutoDubForBgmClip(clipId: String, targetLanguageCode: String) {
        viewModelScope.launch {
            val bgm = _uiState.value.bgmClips.firstOrNull { it.id == clipId } ?: return@launch
            val r = generateAutoDub(
                projectId = projectId,
                sourceUri = bgm.sourceUri,
                mediaType = "AUDIO",
                sourceLanguageCode = "auto",
                targetLanguageCode = targetLanguageCode,
                numberOfSpeakers = 1,
            )
            if (r.isFailure) {
                _uiState.value = _uiState.value.copy(
                    autoDubError = r.exceptionOrNull()?.message ?: "더빙 생성 실패",
                )
            }
        }
    }

    fun onUpdateBgmSpeed(clipId: String, newSpeed: Float) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, speedScale = newSpeed)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    fun onUpdateBgmVolume(clipId: String, newVolume: Float) {
        viewModelScope.launch {
            try {
                updateBgmClip(clipId, volumeScale = newVolume)
                pushUndoState()
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(bgmError = e.message)
            }
        }
    }

    fun onDeleteBgmClip(clipId: String) {
        viewModelScope.launch {
            deleteBgmClip(clipId)
            if (_uiState.value.selectedBgmClipId == clipId) {
                _uiState.value = _uiState.value.copy(selectedBgmClipId = null)
            }
            pushUndoState()
        }
    }

    // --- Audio separation (per-segment voice/background split) ---

    /**
     * 영속화된 음성분리 잡을 클리어 (FAILED 후 "다시 시도" / 사용자 취소). 폴링 중지 + EditProject
     * separation* 초기화 + audioSeparation 도 SETUP 으로 리셋.
     */
    fun onClearSeparation() {
        separationJob?.cancel()
        separationJob = null
        viewModelScope.launch {
            editProjectRepository.getProject(projectId)?.let { p ->
                editProjectRepository.updateProject(
                    p.copy(
                        separationJobId = null,
                        separationSegmentId = null,
                        separationStatus = AutoJobStatus.IDLE,
                        separationError = null,
                    )
                )
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
            val kind = com.dubcast.shared.domain.model.Stem.kindFromId(sel.stemId)
            val speakerIdx = com.dubcast.shared.domain.model.Stem.speakerIndexFromId(sel.stemId)
            com.dubcast.shared.domain.model.Stem(
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
     * 임시 — testdata 의 mock stem mp3 들로 음성분리 결과를 즉시 hydrate. 실제 BFF /separate 호출 없이
     * PICK_STEMS 단계로 바로 진입해 stem 선택/볼륨 UI + StemMixer 동작 확인용. release 전 제거.
     */
    /**
     * mock 은 "영상 전체" 분리로 가정 — segmentId 를 빈 문자열로 두어 commit 시 directive range 가
     * 0 ~ totalDurationMs (모든 segment 의 합) 로 잡힘. 모든 video segment 의 원본 audio 도 mute.
     */
    fun onMockSeparationReady() {
        // BFF testdata/separation/list 호출 → 폴더별 (startSec-endSec) directive 생성.
        // 실패 (BFF down / IP mismatch / 폴더 빈 list) 시 separationError 에 메시지 노출.
        viewModelScope.launch {
            val base = bffBaseUrl.trimEnd('/')
            val state = _uiState.value
            state.separationDirectives.forEach { separationDirectiveRepository.delete(it.id) }
            val foldersResult = runCatching { bffApi.listSeparationTestdata() }
            val folders = foldersResult.getOrNull()
            if (folders == null) {
                val err = foldersResult.exceptionOrNull()
                setSeparationFailed("분리 mock 실패 (BFF=$base): ${err?.message ?: "알 수 없는 오류"}")
                return@launch
            }
            if (folders.isEmpty()) {
                setSeparationFailed("분리 mock testdata 폴더가 비어있습니다 (BFF testdata/0-30/ 등 폴더 확인)")
                return@launch
            }
            folders.forEach { folder ->
                val rangeStart = folder.startSec * 1000L
                val rangeEnd = folder.endSec * 1000L
                if (rangeEnd <= rangeStart) return@forEach
                val selections = folder.stems.map { stemName ->
                    StemSelection(
                        stemId = stemName,
                        volume = 1.0f,
                        audioUrl = "$base/api/v2/testdata/separation/${folder.folder}/$stemName",
                    )
                }
                separationDirectiveRepository.add(
                    com.dubcast.shared.domain.model.SeparationDirective(
                        id = Uuid.random().toString(),
                        projectId = projectId,
                        rangeStartMs = rangeStart,
                        rangeEndMs = rangeEnd,
                        numberOfSpeakers = folder.stems.count { it != "background" }.coerceAtLeast(1),
                        muteOriginalSegmentAudio = true,
                        selections = selections,
                        createdAt = currentTimeMillis(),
                    )
                )
            }
            _uiState.value.segments.filter { it.type == com.dubcast.shared.domain.model.SegmentType.VIDEO }
                .forEach { updateSegmentVolume(it.id, 0f) }
            // 성공 시 separation status 도 READY 로 표시 (UI 시그널).
            editProjectRepository.getProject(projectId)?.let { p ->
                editProjectRepository.updateProject(
                    p.copy(
                        separationStatus = AutoJobStatus.READY,
                        separationError = null,
                    )
                )
            }
        }
    }

    /**
     * separation mock / 실제 분리 흐름의 공통 실패 처리. EditProject 의 separationStatus/Error 를
     * FAILED 로 갱신하고 콘솔에 로그.
     */
    private suspend fun setSeparationFailed(msg: String) {
        println("[Separation] $msg")
        editProjectRepository.getProject(projectId)?.let { p ->
            editProjectRepository.updateProject(
                p.copy(
                    separationStatus = AutoJobStatus.FAILED,
                    separationError = msg,
                )
            )
        }
    }

    /**
     * BgmClip 음원분리 — 추가된 음원에 대해 Perso AUDIO 분리 호출. 시트는 동일 [AudioSeparationSheet]
     * 재사용 — onConfirmStemMix 가 [bgmSeparationTargetId] 보고 BGM 교체 path 로 분기.
     *
     * 결과: 사용자가 picked stem 들로 원본 BGM 을 N 개의 stem BGM 클립으로 대체.
     */
    fun onStartBgmSeparation(bgmClipId: String) {
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
        separationJob?.cancel()
        separationJob = viewModelScope.launch {
            val result = startAudioSeparation(
                sourceUri = bgm.sourceUri,
                mediaType = SeparationMediaType.AUDIO,
                numberOfSpeakers = 2,
            )
            val jobId = result.getOrElse { err ->
                updateSeparation { it.copy(step = AudioSeparationStep.FAILED, errorMessage = err.message) }
                return@launch
            }
            updateSeparation { it.copy(jobId = jobId) }
            pollSeparationFlow(jobId)
        }
    }

    fun onShowAudioSeparationSheet(segmentId: String) {
        val state = _uiState.value
        val seg = state.segments.firstOrNull { it.id == segmentId } ?: return
        if (seg.type != SegmentType.VIDEO) return
        // 이미 진행 중인 audioSeparation 이 있으면 그대로 유지 (jobId/progress/stems 등) — 사용자가
        // sheet 만 다시 펼친 케이스. 없으면 새 SETUP 으로 시작.
        // range mode 에서 진입한 케이스 — pendingRangeStartMs/EndMs 를 audioSeparation 에 반영.
        val rangeStart = state.pendingRangeStartMs.takeIf { state.pendingRangeEndMs > it }
        val rangeEnd = state.pendingRangeEndMs.takeIf { it > state.pendingRangeStartMs }
        val current = state.audioSeparation
        val next = current?.copy(rangeStartMs = rangeStart, rangeEndMs = rangeEnd)
            ?: AudioSeparationUiState(segmentId = segmentId, rangeStartMs = rangeStart, rangeEndMs = rangeEnd)
        _uiState.value = state.copy(
            audioSeparation = next,
            showAudioSeparationSheet = true,
            isPlaying = false,
        )
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
                separationJob?.cancel()
                separationJob = null
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
            _uiState.value.segments.filter { it.type == com.dubcast.shared.domain.model.SegmentType.VIDEO }
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
        _uiState.value = _uiState.value.copy(showAudioSeparationSheet = false)
    }

    // ── 자막/더빙 생성 패널 ──────────────────────────────────────────────────
    fun onShowLocalization() {
        _uiState.value = _uiState.value.copy(localizationOpen = true)
    }

    fun onToggleReviewScriptBeforeGenerate() {
        _uiState.value = _uiState.value.copy(
            reviewScriptBeforeGenerate = !_uiState.value.reviewScriptBeforeGenerate
        )
    }

    fun onDismissScriptReviewSheet() {
        // 검토 취소 — pending state 그대로 두고 sheet 만 닫음. 사용자가 다시 열 수 있음.
        _uiState.value = _uiState.value.copy(showScriptReviewSheet = false)
    }

    /**
     * 사용자 검토 완료 — 저장된 source lang clips 을 source 로 RegenerateSubtitlesUseCase 호출하여
     * target 언어 자막 일괄 생성. cue 의 텍스트 수정은 이미 SubtitleEditSheet/inline 편집으로 DB 에 반영됨.
     */
    /**
     * 검토 sheet 가 유지하고 있는 cue id → 수정된 text map 을 일괄 저장 후 regenerate.
     * sheet 의 inline DisposableEffect 저장은 unmount 타이밍 race 가 있어 의존하지 않는다.
     */
    fun onConfirmScriptReview(edits: Map<String, String> = emptyMap()) {
        val state = _uiState.value
        val targets = state.pendingReviewTargetLangs
        reviewSheetGate = TriggerGate.ARMED
        _uiState.value = state.copy(showScriptReviewSheet = false)
        viewModelScope.launch {
            // 사용자가 수정한 cue 들 동기 저장 — regenerate 가 SRT 만들 때 최신 text 보이도록.
            edits.forEach { (clipId, newText) ->
                val clip = subtitleClipRepository.getClip(clipId) ?: return@forEach
                val trimmed = newText.trim()
                if (trimmed.isNotEmpty() && trimmed != clip.text.trim()) {
                    subtitleClipRepository.updateClip(clip.copy(text = trimmed))
                }
            }
            // 검토 대기 마킹 클리어.
            editProjectRepository.getProject(projectId)?.let { p ->
                editProjectRepository.updateProject(
                    p.copy(pendingReviewTargetLangsCsv = null)
                )
            }
            if (targets.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    pendingReviewTargetLangs = emptyList(),
                    sttPreflightStatus = AutoJobStatus.IDLE,
                )
                return@launch
            }
            // edits 저장 완료 후에만 regenerate 호출 — RegenerateSubtitlesUseCase 의 observeClips.first()
            // 가 최신 text 를 보고 SRT 를 만든다.
            onRegenerateOtherLanguages(sourceLanguageCode = "", targetLanguageCodes = targets)
        }
    }
    fun onDismissLocalization() {
        _uiState.value = _uiState.value.copy(localizationOpen = false)
    }
    fun onSetLocalizationMode(mode: String) {
        _uiState.value = _uiState.value.copy(localizationMode = mode)
    }
    fun onToggleLocalizationLang(code: String) {
        val current = _uiState.value.localizationLangs
        val next = if (code in current) current - code else current + code
        _uiState.value = _uiState.value.copy(localizationLangs = next)
    }
    fun onStartLocalization() {
        val s = _uiState.value
        println("[Localization] onStartLocalization mode=${s.localizationMode} langs=${s.localizationLangs} segCount=${s.segments.size}")
        val source = s.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) {
            println("[Localization] aborted: no source segment")
            return
        }
        val mode = s.localizationMode
        val langs = s.localizationLangs.toList()
        if (langs.isEmpty()) {
            println("[Localization] aborted: empty langs")
            return
        }
        viewModelScope.launch {
            println("[Localization] launching mode=$mode langs=$langs source=$source")
            // 사용자가 패널에서 고른 langs 를 project 에 persist — observeProject flow 가 다시 emit 하면서
            // dropdown chip 에 추가됨. (in-memory copy 만 하면 다음 project emit 에 덮어써짐.)
            editProjectRepository.getProject(projectId)?.let { p ->
                val merged = (p.targetLanguageCodes + langs).distinct()
                if (merged != p.targetLanguageCodes) {
                    editProjectRepository.updateProject(p.copy(targetLanguageCodes = merged))
                }
            }
            when (mode) {
                "subtitle" -> if (s.reviewScriptBeforeGenerate) {
                    // 검토 모드: STT only → review sheet → 사용자 수정 → regenerate.
                    println("[Localization] -> review-mode STT only targets=$langs")
                    _uiState.value = _uiState.value.copy(
                        sttPreflightStatus = AutoJobStatus.RUNNING,
                        sttPreflightError = null,
                        pendingReviewTargetLangs = langs,
                    )
                    // 검토 대기 영속화 — timeline 떠났다 와도 sheet 자동 복귀.
                    editProjectRepository.getProject(projectId)?.let { p ->
                        editProjectRepository.updateProject(
                            p.copy(pendingReviewTargetLangsCsv = langs.joinToString(","))
                        )
                    }
                    val r = generateOriginalScript(
                        projectId = projectId,
                        sourceUri = source,
                        mediaType = "VIDEO",
                    )
                    if (r.isSuccess) {
                        reviewSheetGate = TriggerGate.FIRED
                        _uiState.value = _uiState.value.copy(
                            sttPreflightStatus = AutoJobStatus.IDLE,
                            showScriptReviewSheet = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            sttPreflightStatus = AutoJobStatus.FAILED,
                            sttPreflightError = r.exceptionOrNull()?.message,
                            pendingReviewTargetLangs = emptyList(),
                        )
                    }
                } else {
                    // 1회 호출로 N langs 모두 처리 — 영상 업로드 / STT 1번만, Gemini 번역 N번.
                    // sourceLanguageCode="auto" — Perso STT 가 자동 감지.
                    println("[Localization] -> generateAutoSubtitles source=auto targets=$langs")
                    val r = generateAutoSubtitles(
                        projectId = projectId,
                        sourceUri = source,
                        mediaType = "VIDEO",
                        sourceLanguageCode = "auto",
                        targetLanguageCodes = langs,
                        numberOfSpeakers = 1
                    )
                    println("[Localization] subtitle result isSuccess=${r.isSuccess} cues=${r.getOrNull()} err=${r.exceptionOrNull()?.message}")
                    // 미리보기 chip 자동 전환 — source=auto 이므로 originalSrt 는 저장 안 됨.
                    // 현재 미리보기가 null("기본") 이거나 새로 추가된 langs 와 무관할 때만 첫 lang 으로 전환
                    // (사용자가 일부러 다른 lang 을 보고 있으면 유지).
                    if (r.isSuccess && langs.isNotEmpty()) {
                        val current = _uiState.value.previewLangCode
                        if (current == null || current !in _uiState.value.targetLanguageCodes) {
                            _uiState.value = _uiState.value.copy(previewLangCode = langs.first())
                        }
                    }
                }
                "dub" -> langs.forEach { lang ->
                    println("[Localization] -> generateAutoDub source=auto lang=$lang")
                    runCatching {
                        generateAutoDub(
                            projectId = projectId,
                            sourceUri = source,
                            mediaType = "VIDEO",
                            sourceLanguageCode = "auto",
                            targetLanguageCode = lang,
                            numberOfSpeakers = 1
                        )
                    }.onFailure { println("[Localization] dub failed lang=$lang err=${it.message}") }
                }
                else -> println("[Localization] unknown mode=$mode")
            }
        }
        _uiState.value = s.copy(localizationOpen = false)
    }
    fun onSelectPreviewLang(code: String?) {
        _uiState.value = _uiState.value.copy(previewLangCode = code)
    }

    fun onUpdateSeparationSpeakers(count: Int) {
        val sep = _uiState.value.audioSeparation ?: return
        _uiState.value = _uiState.value.copy(
            audioSeparation = sep.copy(numberOfSpeakers = count.coerceIn(1, 10))
        )
    }

    private var separationJob: kotlinx.coroutines.Job? = null

    fun onStartSeparation() {
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
        separationJob?.cancel()
        // 분리 시작 즉시 sheet 닫음 — 진행은 백그라운드, 사용자가 다른 작업 가능.
        // 결과는 directive 막대로 알림.
        _uiState.value = _uiState.value.copy(showAudioSeparationSheet = false)
        separationJob = viewModelScope.launch {
            updateSeparation { it.copy(step = AudioSeparationStep.PROCESSING, errorMessage = null) }
            val startResult = startAudioSeparation(
                sourceUri = segment.sourceUri,
                mediaType = SeparationMediaType.VIDEO,
                numberOfSpeakers = sep.numberOfSpeakers,
                trimStartMs = effStart,
                trimEndMs = effEnd,
            )
            val jobId = startResult.getOrElse { err ->
                updateSeparation {
                    it.copy(step = AudioSeparationStep.FAILED, errorMessage = err.message)
                }
                editProjectRepository.getProject(projectId)?.let {
                    editProjectRepository.updateProject(
                        it.copy(separationStatus = AutoJobStatus.FAILED, separationError = err.message)
                    )
                }
                return@launch
            }
            updateSeparation { it.copy(jobId = jobId) }
            // 잡 ID 받자마자 EditProject 에 영속화 — 화면 떠나거나 앱 재실행 후 재진입해도 폴링 재개.
            editProjectRepository.getProject(projectId)?.let {
                editProjectRepository.updateProject(
                    it.copy(
                        separationJobId = jobId,
                        separationSegmentId = sep.segmentId,
                        separationNumberOfSpeakers = sep.numberOfSpeakers,
                        separationMuteOriginal = sep.muteOriginalSegmentAudio,
                        separationStatus = AutoJobStatus.RUNNING,
                        separationError = null,
                    )
                )
            }
            separationGate = TriggerGate.FIRED
            pollSeparationFlow(jobId)
        }
    }

    private suspend fun pollSeparationFlow(jobId: String) {
        try {
            pollSeparation(jobId).collect { status ->
                when (status) {
                    is SeparationStatus.Processing -> updateSeparation {
                        it.copy(progress = status.progress, progressReason = status.progressReason)
                    }
                    is SeparationStatus.Ready -> {
                        updateSeparation {
                            val defaults = status.stems.associate { stem ->
                                stem.stemId to StemSelectionUi(stem.stemId, selected = false, volume = 1.0f)
                            }
                            it.copy(
                                step = AudioSeparationStep.PICK_STEMS,
                                progress = 100,
                                progressReason = null,
                                stems = status.stems,
                                selections = defaults
                            )
                        }
                        editProjectRepository.getProject(projectId)?.let {
                            editProjectRepository.updateProject(
                                it.copy(separationStatus = AutoJobStatus.READY)
                            )
                        }
                    }
                    is SeparationStatus.Failed -> {
                        val reason = status.progressReason ?: "분리에 실패했습니다"
                        updateSeparation {
                            it.copy(step = AudioSeparationStep.FAILED, errorMessage = reason)
                        }
                        editProjectRepository.getProject(projectId)?.let {
                            editProjectRepository.updateProject(
                                it.copy(separationStatus = AutoJobStatus.FAILED, separationError = reason)
                            )
                        }
                    }
                    is SeparationStatus.Consumed -> {
                        val reason = "이 작업은 이미 합성에 사용되어 더 이상 사용할 수 없습니다"
                        updateSeparation {
                            it.copy(step = AudioSeparationStep.FAILED, errorMessage = reason)
                        }
                        editProjectRepository.getProject(projectId)?.let {
                            editProjectRepository.updateProject(
                                it.copy(separationStatus = AutoJobStatus.FAILED, separationError = reason)
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            updateSeparation { it.copy(step = AudioSeparationStep.FAILED, errorMessage = e.message) }
            editProjectRepository.getProject(projectId)?.let {
                editProjectRepository.updateProject(
                    it.copy(separationStatus = AutoJobStatus.FAILED, separationError = e.message)
                )
            }
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
                // editingDirectiveId 가 있으면 기존 directive 삭제 후 새로 추가 — range 보존하기 위해
                // 기존 directive 의 range 사용. 그렇지 않으면 segment 기반 새 range 계산.
                val existing = editingDirectiveId?.let { id ->
                    _uiState.value.separationDirectives.firstOrNull { it.id == id }
                }
                val effectiveStart = existing?.rangeStartMs ?: directiveStart
                val effectiveEnd = existing?.rangeEndMs ?: directiveEnd
                if (existing != null) {
                    separationDirectiveRepository.delete(existing.id)
                    editingDirectiveId = null
                }
                separationDirectiveRepository.add(
                    SeparationDirective(
                        id = Uuid.random().toString(),
                        projectId = projectId,
                        rangeStartMs = effectiveStart,
                        rangeEndMs = effectiveEnd,
                        numberOfSpeakers = sep.numberOfSpeakers,
                        muteOriginalSegmentAudio = true,  // 항상 음소거 (사용자 정책).
                        selections = selections,
                        createdAt = currentTimeMillis()
                    )
                )
                // 음성분리 구간의 원본 음성은 항상 음소거 — stem 들로 대체되므로 중복 재생 방지.
                if (segment != null) {
                    updateSegmentVolume(segment.id, 0f)
                } else {
                    // 영상 전체 분리 — 모든 video segment 의 원본 audio mute.
                    state.segments.filter { it.type == com.dubcast.shared.domain.model.SegmentType.VIDEO }
                        .forEach { updateSegmentVolume(it.id, 0f) }
                }
                // 적용 즉시 sheet 닫음 — DONE 단계의 "완료" 팝업 노출 안 함.
                _uiState.value = _uiState.value.copy(
                    audioSeparation = null,
                    showAudioSeparationSheet = false,
                )
                // commit 완료 → EditProject 의 separation* 모두 IDLE 로 클리어. 다음 음성분리 새로 가능.
                editProjectRepository.getProject(projectId)?.let { p ->
                    editProjectRepository.updateProject(
                        p.copy(
                            separationJobId = null,
                            separationSegmentId = null,
                            separationStatus = AutoJobStatus.IDLE,
                            separationError = null,
                        )
                    )
                }
                separationGate = TriggerGate.ARMED
                undoRedoManager.clear()
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
                deleteBgmClip(original.id)
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
        _uiState.value = _uiState.value.copy(audioSeparation = transform(current))
    }

    companion object {
        const val MIN_RANGE_MS = SplitSegmentUseCase.MIN_RANGE_MS
        const val MIN_IMAGE_DURATION_MS = 500L
        const val MAX_IMAGE_DURATION_MS = 30_000L
        const val MIN_FRAME_DIMENSION = 16
        const val MAX_FRAME_DIMENSION = 7680
        const val DEFAULT_OVERLAY_DURATION_MS = 3_000L

        private val DEFAULT_VOICES = listOf(
            Voice("EXAVITQu4vr4xnSDxMaL", "Sarah", null, "en"),
            Voice("TX3LPaxmHKxFdv7VOQHJ", "Liam", null, "en"),
            Voice("pFZP5JQG7iQjIQuC4Bku", "Lily", null, "en"),
            Voice("bIHbv24MWmeRgasZH58o", "Will", null, "en"),
            Voice("default-ko-1", "Jimin", null, "ko"),
            Voice("default-ko-2", "Seoyeon", null, "ko"),
        )
    }

    fun onOpenExportOptionsSheet() {
        _uiState.value = _uiState.value.copy(showExportOptionsSheet = true)
    }

    fun onCloseExportOptionsSheet() {
        _uiState.value = _uiState.value.copy(showExportOptionsSheet = false)
    }

    fun onUpdateExportOptions(
        targetLanguageCode: String,
        enableAutoSubtitles: Boolean,
        enableAutoDubbing: Boolean,
        numberOfSpeakers: Int
    ) {
        viewModelScope.launch {
            val current = editProjectRepository.getProject(projectId) ?: return@launch
            val isOriginal = targetLanguageCode == TargetLanguage.CODE_ORIGINAL
            editProjectRepository.updateProject(
                current.copy(
                    targetLanguageCode = targetLanguageCode,
                    // 번역 대상 언어가 원본이면 자막/더빙 파이프라인은 의미가 없으므로 강제 OFF.
                    enableAutoSubtitles = if (isOriginal) false else enableAutoSubtitles,
                    enableAutoDubbing = if (isOriginal) false else enableAutoDubbing,
                    numberOfSpeakers = numberOfSpeakers.coerceIn(1, 10),
                    updatedAt = currentTimeMillis()
                )
            )
            _uiState.value = _uiState.value.copy(showExportOptionsSheet = false)
        }
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
