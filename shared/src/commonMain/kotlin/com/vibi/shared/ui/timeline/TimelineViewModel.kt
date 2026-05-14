@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.vibi.shared.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibi.shared.platform.currentTimeMillis
import kotlin.uuid.Uuid
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.DubClip
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.EditProject
import com.vibi.shared.domain.model.clearAutoSubtitleDub
import com.vibi.shared.domain.model.hasConfirmedOriginalSubtitle
import com.vibi.shared.domain.model.ImageClip
import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.PersistedSeparationJob
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.model.addProcessingSeparation
import com.vibi.shared.domain.model.removeProcessingSeparation
import com.vibi.shared.domain.model.SubtitleClip
import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.domain.model.SubtitlePosition
import com.vibi.shared.domain.model.SubtitleSource
import com.vibi.shared.platform.generateId
import com.vibi.shared.domain.model.TargetLanguage
import com.vibi.shared.domain.model.TextOverlay
import com.vibi.shared.domain.model.clearSeparation
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.BgmClipRepository
import com.vibi.shared.domain.repository.DubClipRepository
import com.vibi.shared.domain.repository.EditProjectRepository
import com.vibi.shared.domain.repository.ImageClipRepository
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository
import com.vibi.shared.domain.repository.SeparationStatus
import com.vibi.shared.domain.repository.StemSelection
import com.vibi.shared.domain.repository.SubtitleClipRepository
import com.vibi.shared.domain.repository.TextOverlayRepository
import com.vibi.shared.domain.model.SeparationMediaType
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.domain.usecase.image.AddImageClipUseCase
import com.vibi.shared.domain.usecase.image.UpdateImageClipUseCase
import com.vibi.shared.domain.usecase.bgm.AddBgmClipUseCase
import com.vibi.shared.domain.usecase.bgm.UpdateBgmClipUseCase
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import com.vibi.shared.domain.usecase.input.ImageMetadataExtractor
import com.vibi.shared.domain.usecase.input.SetProjectFrameUseCase
import com.vibi.shared.domain.usecase.input.VideoMetadataExtractor
import com.vibi.shared.domain.usecase.render.EnsureLatestRenderUseCase
import com.vibi.shared.domain.usecase.save.ExportVariant
import com.vibi.shared.domain.usecase.save.ListExportVariantsUseCase
import com.vibi.shared.domain.usecase.save.SaveAllVariantsUseCase
import com.vibi.shared.domain.usecase.separation.PollSeparationUseCase
import com.vibi.shared.domain.usecase.separation.StartAudioSeparationUseCase
import com.vibi.shared.domain.usecase.share.ShareSheetLauncher
import com.vibi.shared.domain.usecase.subtitle.AddSubtitleClipUseCase
import com.vibi.shared.domain.usecase.subtitle.GenerateAutoDubUseCase
import com.vibi.shared.domain.usecase.subtitle.GenerateAutoSubtitlesUseCase
import com.vibi.shared.domain.usecase.subtitle.GenerateOriginalScriptUseCase
import com.vibi.shared.domain.usecase.subtitle.RegenerateSubtitlesUseCase
import com.vibi.shared.domain.usecase.subtitle.SrtParser
import com.vibi.shared.domain.usecase.subtitle.UndoRedoManager
import com.vibi.shared.domain.usecase.text.AddTextOverlayUseCase
import com.vibi.shared.domain.usecase.text.DuplicateTextOverlayUseCase
import com.vibi.shared.domain.usecase.text.UpdateTextOverlayUseCase
import com.vibi.shared.domain.usecase.timeline.AddImageSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.AddVideoSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.DeleteDubClipUseCase
import com.vibi.shared.domain.usecase.timeline.DuplicateSegmentRangeUseCase
import com.vibi.shared.domain.usecase.timeline.MoveDubClipUseCase
import com.vibi.shared.domain.usecase.timeline.RemoveSegmentRangeUseCase
import com.vibi.shared.domain.usecase.timeline.RemoveSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.SplitSegmentUseCase
import com.vibi.shared.domain.usecase.timeline.UpdateImageSegmentDurationUseCase
import com.vibi.shared.domain.usecase.timeline.UpdateImageSegmentPositionUseCase
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
 * 저장/공유 흐름 진입 시 노출하는 variant picker sheet 상태.
 * variant 가 1개 이하면 picker 안 띄움 (즉시 동작) — 이 state 는 null.
 *
 *  - [Save] : multi-select. default 는 모든 variant 선택. confirm 시 갤러리 저장.
 *  - [Share] : multi-select. default 는 "original" 한 건. confirm 시 share sheet 으로 다중 첨부.
 *      외부 앱이 다중 첨부를 지원 못 하면 chooser 결과는 앱별 동작에 따름.
 */
sealed class ExportVariantPickerState {
    abstract val variants: List<ExportVariant>

    data class Save(
        override val variants: List<ExportVariant>,
        val selected: Set<String>,
    ) : ExportVariantPickerState()

    data class Share(
        override val variants: List<ExportVariant>,
        val selected: Set<String>,
    ) : ExportVariantPickerState()
}

/**
 * 타임라인 작업 단계 — 사용자에게 보이는 2 step stepper 의 모델.
 * EditAudio (편집·음원, 기본 진입 — 영상 segment 편집 + BGM 삽입/조정 + 음원분리) ↔
 * SubtitleDub (자막/더빙).
 * 단계 이동은 산출물·undo 모두 보존하는 양방향 자유 이동. EditAudio 안에서 segment 구간 액션은
 * BGM 에도 ripple/split 로 동시 적용되므로 단계 commit 시 BGM 일괄 wipe 불필요.
 */
enum class TimelineStep { EditAudio, SubtitleDub }

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
    val showSubtitleSheet: Boolean = false,
    /** my_plan: 편집 화면에 자막을 띄울지. */
    val showSubtitlesOnPreview: Boolean = true,
    /** my_plan: 편집 화면에 더빙을 띄울지. */
    val showDubbingOnPreview: Boolean = true,
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
    /**
     * BGM 영역의 lane 개수. 사용자가 하단 drag handle 로 명시적 확장/축소.
     * BGM clip 의 vertical drag 는 0..bgmLaneCount-1 로 clamp — 영역 밖으로 못 옮김.
     * 더 아래 lane 이 필요하면 사용자가 영역을 먼저 늘리고 옮겨야 함. 영속화 X (UI 한정).
     */
    val bgmLaneCount: Int = 3,
    val audioSeparation: AudioSeparationUiState? = null,
    /** AudioSeparationSheet 표시 여부 — audioSeparation (데이터) 과 분리해 자동 팝업 회피. */
    val showAudioSeparationSheet: Boolean = false,
    /**
     * 백그라운드에서 폴링 중인 음원분리 잡들. 동시에 여러 구간을 분리할 수 있도록 리스트.
     * - timeline 의 progress overlay 는 이 리스트를 순회해 각각 그린다.
     * - free-interval / range slider 가 새 분리 시작 시 이 리스트의 range 도 occupied 로 취급.
     * - 잡이 Ready/Failed/Consumed 가 되면 해당 entry 는 즉시 리스트에서 제거.
     */
    val processingSeparations: List<ProcessingSeparation> = emptyList(),
    /** Phase 1 commit 후 timeline 재생 시 stem mixer 가 사용. */
    val separationDirectives: List<SeparationDirective> = emptyList(),
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
    /** 상세 편집 모드 — timeline 위에 자막 cue list + 스타일 panel 노출. */
    val showDetailEdit: Boolean = false,
    val detailEditLang: String? = null,
    // ── 자막/더빙 생성 패널 ─────────────────────────────────────────────────
    val localizationOpen: Boolean = false,
    /** "subtitle" | "dub" */
    val localizationMode: String = "subtitle",
    val localizationLangs: Set<String> = emptySet(),
    /**
     * 자막 모드 한정 — true 면 "원본 언어 (스크립트만)" : BFF 에 targetLanguageCodes=[] 로 호출,
     * STT 결과 originalSrt 만 받고 번역은 skip. false 면 기존 흐름 (사용자가 lang chip 으로 번역 langs 선택).
     * dub 모드에선 사용 안 함 (현재 dropdown 그대로).
     */
    /** 자막 생성 전 STT 스크립트 검토·수정 단계 활성화 여부. dub 모드는 미지원 (BFF 추가 필요). */
    val reviewScriptBeforeGenerate: Boolean = false,
    /** STT only 결과 cue 들. 검토 sheet 의 데이터 source. null = STT 미실행 또는 검토 완료. */
    val pendingReviewCues: List<com.vibi.shared.domain.usecase.subtitle.ParsedSrtCue>? = null,
    /** 검토 후 진행할 target 언어 코드들 — STT 시작 시 사용자 선택값을 보존. */
    val pendingReviewTargetLangs: List<String> = emptyList(),
    val showScriptReviewSheet: Boolean = false,
    /** STT only 진행 상태 — RUNNING 시 chip spinner. */
    val sttPreflightStatus: AutoJobStatus = AutoJobStatus.IDLE,
    val sttPreflightError: String? = null,
    /**
     * STT review-mode 로 시작했고 사용자가 검토 confirm 안 한 상태.
     * `EditProject.pendingReviewTargetLangsCsv != null` 와 동기화 — 빈 string 도 review pending
     * (= original-only review). UI 가 "스크립트 생성 완료" 진입 버튼 표시 여부 결정.
     */
    val subtitleReviewPending: Boolean = false,
    /**
     * 원본 자막 (lang="" SubtitleClip) 이 chip / export variant 에 노출 가능한지.
     * SSOT — [hasConfirmedOriginalSubtitle] 결과를
     * observeProject 에서 set. UI 와 export use case 가 같은 헬퍼를 보므로 갈라질 일 없음.
     */
    val hasOriginalSubtitleVariant: Boolean = false,
    /** null = 원본, 그 외 = 미리보기로 볼 언어 코드. 비디오 소스 swap 은 미구현 (UI 선택만). */
    val previewLangCode: String? = null,
    /** 언어 코드 → 더빙된 audio mp3 local path. (legacy / export 합성용) */
    val dubbedAudioPaths: Map<String, String> = emptyMap(),
    /** 언어 코드 → BFF 가 video+dubAudio mux 한 mp4 local path. 미리보기 swap source. */
    val dubbedVideoPaths: Map<String, String> = emptyMap(),
    /** Timeline 헤더 "저장" 버튼이 트리거하는 multi-variant 갤러리 저장의 진행 상태. */
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    /** 공유 흐름 진행 상태 — 저장과 별도. 공유는 프로젝트 보존, navigate 안 함. */
    val shareStatus: ShareStatus = ShareStatus.IDLE,
    /**
     * 자막/더빙/분리 시작 직전 EnsureLatestRenderUseCase 가 BFF 에 편집 영상 render 잡을 보내고
     * 폴링 중일 때의 진행률(0..100). null = 진행 중 아님 (또는 무편집 → render skip).
     * UI 가 "편집 영상 준비 중… (xx%)" 노출.
     */
    val editedVideoRenderProgress: Int? = null,
    /**
     * 저장/공유 흐름 진입 시 사용자가 어떤 variant 를 출력할지 고르는 picker sheet state.
     * null = picker 미노출 (variant 1개라 즉시 동작 중이거나 흐름 idle).
     */
    val exportVariantPicker: ExportVariantPickerState? = null,
    /**
     * 현재 사용자가 보고 있는 타임라인 단계.
     * 영상 선택 후 진입 시 편집·음원 단계가 기본 — 영상 segment 편집 + BGM 삽입/조정 + 음원분리를
     * 한 화면에서 처리. 자막/더빙은 별도 단계.
     */
    val currentStep: TimelineStep = TimelineStep.EditAudio,
    /**
     * stepper 이동을 사용자에게 한 번 더 확인받기 위한 보류 경고 상태. null = 경고 없음.
     * UserPreferencesStore 의 don't-ask-again 플래그가 set 되어 있으면 onSelectStep 가 처음부터
     * 경고 생성을 건너뛰고 즉시 이동한다.
     */
    val pendingStepWarning: StepTransitionWarning? = null,
) {
    val effectiveTrimEndMs: Long get() = if (trimEndMs <= 0L) videoDurationMs else trimEndMs
    val frameAspectRatio: Float
        get() = if (frameWidth > 0 && frameHeight > 0) {
            frameWidth.toFloat() / frameHeight.toFloat()
        } else 0f
}

/**
 * stepper 전환 시 한 번 더 확인받는 경고 상태.
 *
 * - [LocalizationLock]: 음원 → 자막/더빙 이동 — 자막/더빙 생성 후 음원분리 수정이 잠금됨을 안내.
 * - [EditReset]: 어떤 단계에서든 영상편집 단계로 되돌아갈 때 — 기존 음원/자막/더빙/분리 산출물이
 *   영상편집 commit 시점에 초기화될 수 있음을 안내.
 *
 * 둘 다 don't-ask-again 옵션 (UserPreferencesStore) 가 있고, 한 번 끄면 같은 종류의 경고는 다시
 * 안 뜸. target 은 사용자가 확인 시 그대로 이동할 목표 step.
 */
sealed interface StepTransitionWarning {
    val target: TimelineStep
    data class LocalizationLock(override val target: TimelineStep) : StepTransitionWarning
    data class EditReset(override val target: TimelineStep) : StepTransitionWarning
}

/** 자막/더빙/검토 흐름 중 어떤 잡이라도 진행 중인지 — 백 이동 가드 + UI 버튼 disabled 공용. */
fun TimelineUiState.isLocalizationBusy(): Boolean =
    autoSubtitleStatus == AutoJobStatus.RUNNING ||
        autoDubStatus == AutoJobStatus.RUNNING ||
        regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
        sttPreflightStatus == AutoJobStatus.RUNNING

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
    private val moveDubClip: MoveDubClipUseCase,
    private val deleteDubClip: DeleteDubClipUseCase,
    private val addSubtitleClip: AddSubtitleClipUseCase,
    private val addImageClip: AddImageClipUseCase,
    private val updateImageClip: UpdateImageClipUseCase,
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
    private val duplicateTextOverlay: DuplicateTextOverlayUseCase,
    private val addBgmClip: AddBgmClipUseCase,
    private val updateBgmClip: UpdateBgmClipUseCase,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    private val imageMetadataExtractor: ImageMetadataExtractor,
    private val audioMetadataExtractor: AudioMetadataExtractor,
    private val startAudioSeparation: StartAudioSeparationUseCase,
    private val pollSeparation: PollSeparationUseCase,
    private val audioSeparationRepository: AudioSeparationRepository,
    private val generateAutoSubtitles: GenerateAutoSubtitlesUseCase,
    private val regenerateSubtitles: RegenerateSubtitlesUseCase,
    private val generateOriginalScript: GenerateOriginalScriptUseCase,
    private val generateAutoDub: GenerateAutoDubUseCase,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
    private val bffBaseUrl: String,
    private val bffApi: BffApi,
    private val saveAllVariants: SaveAllVariantsUseCase,
    private val listExportVariants: ListExportVariantsUseCase,
    private val shareSheetLauncher: ShareSheetLauncher,
    private val ensureLatestRender: EnsureLatestRenderUseCase,
    private val userPrefs: com.vibi.shared.data.local.UserPreferencesStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState(projectId = projectId))
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    /**
     * 저장 완료 후 InputScreen 으로 돌아가는 1회성 신호. UI 가 collect 해 nav stack pop.
     * SaveStatus.DONE 만으로 navigation 트리거하면 재진입 시 즉시 또 pop 되는 사고가 나므로 분리.
     */
    private val _navigateBackHome = MutableSharedFlow<Unit>()
    val navigateBackHome: SharedFlow<Unit> = _navigateBackHome.asSharedFlow()

    /**
     * 채팅 패널에 비동기로 어시스턴트 메시지를 push 하기 위한 1회성 이벤트 채널.
     *
     * 동기: 채팅 dispatch 는 fire-and-forget — apply 메서드가 viewModelScope 에서 BFF 호출을
     * 시작하지만 결과(예: STT 스크립트)는 수십 초~수 분 후. 결과를 사용자에게 보여주려면 ChatVM
     * 으로 메시지를 흘려보내야 한다. ChatVM 을 직접 주입하면 KMP DI 사이클 + UI/도메인 결합 →
     * SharedFlow 로 한 단계 풀고 [ChatPanel] 이 collect 후 ChatVM.pushAssistantMessage 호출.
     *
     * 사용처: [applyTranscribeForSubtitlesFromChat] STT 완료 시 SRT 본문 push.
     */
    // extraBufferCapacity + DROP_OLDEST: collector(ChatPanel)가 일시적으로 떨어져 있어도 emit 이
    // suspend 로 멈추지 않게 — 자막 STT 완료 코루틴이 hang 되면 사용자가 panel 다시 열어도 영영
    // 못 받음. 작은 버퍼면 충분.
    private val _chatAssistantEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val chatAssistantEvents: SharedFlow<String> = _chatAssistantEvents.asSharedFlow()

    /**
     * 메인 timeline undo 스택 — TimelineStep 별로 분리.
     *  - 단계 forward 이동 시 출발 단계의 스택 유지 (사용자가 돌아오면 그대로 이어 undo 가능).
     *  - 단계 backward 이동 시 출발 단계의 스택 초기화 (사용자가 다시 앞으로 가도 새 시작).
     * 음원분리 / 음원삽입 / 영상편집(segment edit) 액션은 모두 같은 EditAudio 스택에 push —
     * 사용자가 단일 undo 흐름으로 되돌릴 수 있도록 통합.
     */
    private val mainUndoManagersByStep: Map<TimelineStep, UndoRedoManager<TimelineSnapshot>> =
        TimelineStep.entries.associateWith { UndoRedoManager(maxHistory = 50) }

    private fun mainUndoManagerForCurrent(): UndoRedoManager<TimelineSnapshot> =
        mainUndoManagersByStep.getValue(_uiState.value.currentStep)

    private var hasSeededUndoSnapshot = false

    private fun activeUndoManager(): UndoRedoManager<TimelineSnapshot> =
        mainUndoManagerForCurrent()

    /**
     * `editedVideoRenderProgress` 단일 필드 mutation helper.
     *
     * BFF 편집 영상 render 잡 (자막·자동더빙·분리 시작 직전 EnsureLatestRender) 의 진행률(0..100)을
     * UiState 에 반영하는 패턴이 본 ViewModel 곳곳에 반복되어 helper 로 단일화.
     *
     *  - `percent != null` → "편집 영상 준비 중… (xx%)" 노출.
     *  - `percent == null` → 진행 중 아님 (또는 무편집 → render skip / 다음 단계 진입).
     */
    private fun setRenderProgress(percent: Int?) {
        _uiState.update { it.copy(editedVideoRenderProgress = percent) }
    }

    // Auto-trigger gates: prevent re-firing background pipelines on every
    // project emission. ARMED → eligible to fire; FIRED → already running
    // or finished. Reset to ARMED on explicit retry, or on a failure /
    // cancellation that left the project FAILED so the user can retry.
    private enum class TriggerGate { ARMED, FIRED }
    private var subtitleGate = TriggerGate.ARMED
    private var dubGate = TriggerGate.ARMED
    private var separationGate = TriggerGate.ARMED
    private var separationRefreshGate = TriggerGate.ARMED
    private var reviewSheetGate = TriggerGate.ARMED

    /**
     * Hot-path 가드 — 이미 stale 마킹된 상태면 [markRenderStale] 의 코루틴 launch 도 skip.
     * pushUndoState 가 드래그·슬라이더에서 빈번 호출되므로 idempotent no-op 도 zero round-trip 으로.
     *
     * Lifecycle:
     *  - 신규 프로젝트 / fresh render 직후 → false (mutation 마다 markRenderStale 가 첫 1회 진입 허용).
     *  - markRenderStale 가 invalidateGeneratedResults / RUNNING 가드 stale 마킹 / DB 가 이미 stale
     *    이라 즉시 종료 — 모두 끝에서 true 로 set. 이후 mutation 들은 launch 도 안 함.
     *  - [observeProject] 가 `project.isRenderStale=false` (= EnsureLatestRenderUseCase 가 새로 render
     *    완료 후 set) 를 관찰한 순간 다시 false 로 풀어 다음 mutation cycle 정상.
     *  - [resetTimelineDerivedResults] (영상편집 commit) 직후에도 explicit true — DB 가 stale 이므로
     *    observeProject 의 fresh 검사가 자동 reset 하지 않으니 명시.
     *
     * @Volatile 은 다른 코루틴이 set 한 값을 다른 launch 안에서 즉시 보게 하기 위함 — KMP 공통이라
     *   actual 동작은 플랫폼 메모리모델에 위임 (JVM/Native 모두 volatile 시맨틱 제공).
     */
    @kotlin.concurrent.Volatile
    private var renderStaleMarked = false

    init {
        loadSegments()
        observeClips()
        observeProject()
        observeTextOverlays()
        observeBgmClips()
        observeSeparationDirectives()
    }

    /**
     * Timeline mutation 이 발생하면 즉시 호출 — 다음 자막/더빙/분리 시점에 EnsureLatestRender 가
     * 새로 BFF render 잡을 보내도록 표시 + 이전에 생성된 자막/더빙 결과를 제거 (timeline 과 어긋난
     * stale 결과 노출 방지). 이미 stale 상태면 no-op (idempotent — 첫 mutation 시 1회만 정리).
     * pushUndoState 와 함께 호출되는 곳마다 한 줄 추가하면 됨 — segment add/remove/trim/speed/volume/
     * split/duplicate/range mutate 등.
     *
     * 자막/더빙 generation 이 RUNNING 인 도중에 호출되면 clip 삭제 시 race 가능 — clip 정리는 보류하고
     * `isRenderStale=true` 만 마킹. 사용자가 generation 직후 timeline 편집을 시도하는 드문 케이스에 대비.
     *
     * Hot-path 최적화: in-memory [renderStaleMarked] 플래그로 이미 마킹됐으면 launch 도 skip.
     */
    private fun markRenderStale() {
        if (renderStaleMarked) return
        val current = _uiState.value
        // generation 진행 중 — clip 정리는 race 위험. stale 만 마킹하고 자막/더빙 결과 정리는 보류.
        if (current.autoSubtitleStatus == AutoJobStatus.RUNNING ||
            current.autoDubStatus == AutoJobStatus.RUNNING
        ) {
            viewModelScope.launch {
                val project = editProjectRepository.getProject(projectId) ?: return@launch
                if (!project.isRenderStale) {
                    editProjectRepository.updateProject(project.copy(isRenderStale = true))
                }
                renderStaleMarked = true
            }
            return
        }
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId) ?: return@launch
            if (project.isRenderStale) {
                renderStaleMarked = true
                return@launch
            }
            invalidateGeneratedResults(project)
            renderStaleMarked = true
        }
    }

    /**
     * timeline 편집으로 무효화된 자막/더빙 결과 정리 — clips 삭제 + project 의 dubbed paths /
     * job ids / status 모두 IDLE 로 + render 캐시 두 슬롯 비움 + isRenderStale=true.
     * separation 결과와 segment volume 은 건드리지 않음 (영상편집 commit 시 [resetTimelineDerivedResults]
     * 가 별도 처리).
     *
     * 진행 중 코루틴 (generateAutoSubtitles / generateAutoDub) 이 있으면 race 가능 — [markRenderStale]
     * 의 RUNNING 가드가 본 함수 호출 자체를 skip 하므로 본 함수 진입 시점엔 이미 IDLE/READY/FAILED.
     */
    private suspend fun invalidateGeneratedResults(project: EditProject) {
        subtitleClipRepository.deleteAllClips(projectId)
        dubClipRepository.deleteAllClips(projectId)
        editProjectRepository.updateProject(project.clearAutoSubtitleDub())
        subtitleGate = TriggerGate.ARMED
        dubGate = TriggerGate.ARMED
        reviewSheetGate = TriggerGate.ARMED
        _uiState.value = _uiState.value.clearAutoSubtitleDubUiState()
    }

    /**
     * 자막/더빙 무효화 시 UI state reset — sheet 닫기 + 검토 / 진행 표시 초기화.
     * `invalidateGeneratedResults` (timeline mutation 트리거) 와 `resetTimelineDerivedResults`
     * (영상편집 commit) 양쪽이 같은 UI 표면을 정리하므로 단일 helper.
     */
    private fun TimelineUiState.clearAutoSubtitleDubUiState(): TimelineUiState = copy(
        sttPreflightStatus = AutoJobStatus.IDLE,
        sttPreflightError = null,
        regenerateSubtitleStatus = AutoJobStatus.IDLE,
        regenerateSubtitleError = null,
        pendingReviewCues = null,
        pendingReviewTargetLangs = emptyList(),
        // observeProject 가 한 프레임 뒤 DB(null csv) 보고 동기화하지만 즉시 false 로 — 라벨 깜빡임 방지.
        subtitleReviewPending = false,
        showScriptReviewSheet = false,
        previewLangCode = null,
    )

    /**
     * stepper 노드 탭 — 양방향 자유 이동. 산출물·undo 스택 모두 보존.
     * 잡 가드: 자막/더빙 진행 중 또는 음원 분리 PROCESSING 중에는 무시.
     * 빈 timeline 에서는 forward 이동 차단 — 빈 자막 sheet 가 뜨는 무의미한 흐름 방지.
     *
     * 산출물 wipe 는 오직 영상편집 모드의 ✓ commit 경로 — segment 자체가 바뀌어 downstream
     * 산출물이 stale 이 되는 경우에만 발동.
     */
    fun onSelectStep(target: TimelineStep) {
        val cur = _uiState.value
        if (target == cur.currentStep) return
        if (cur.isLocalizationBusy()) return
        // 음원분리 진행 중에는 단계 이동 차단. 단일 sheet (audioSeparation PROCESSING — 거의 발생하지 않지만
        // FAILED reopen 직전 한 순간) 와 다중 백그라운드 잡 (processingSeparations) 둘 다 가드.
        if (cur.audioSeparation?.step == AudioSeparationStep.PROCESSING) return
        if (cur.processingSeparations.isNotEmpty()) return
        if (target.ordinal > cur.currentStep.ordinal && cur.segments.isEmpty()) return

        // 사용자 경고 단계 — don't-ask-again 안 켜져 있으면 한 번 확인. 본 함수는 경고 state 만 set
        // 하고 실제 이동은 [confirmStepTransition] 가 처리. 종류:
        //   - 편집·음원(EditAudio) → 자막/더빙(SubtitleDub): 자막/더빙 생성 후 음원분리 수정 불가 안내
        //   - 자막/더빙(SubtitleDub) → 편집·음원(EditAudio): 영상편집 commit 시 자막/더빙 산출물 초기화 안내
        val warning = when {
            cur.currentStep == TimelineStep.EditAudio &&
                target == TimelineStep.SubtitleDub &&
                !userPrefs.localizationLockSuppressed ->
                StepTransitionWarning.LocalizationLock(target)

            target == TimelineStep.EditAudio && !userPrefs.editResetSuppressed && (
                cur.subtitleClips.isNotEmpty() ||
                    cur.dubClips.isNotEmpty()
                ) ->
                StepTransitionWarning.EditReset(target)

            else -> null
        }

        if (warning != null) {
            _uiState.update { it.copy(pendingStepWarning = warning) }
            return
        }

        applyStepTransition(target)
    }

    /**
     * 경고 다이얼로그에서 확인 — [dontAskAgain] true 면 같은 종류의 경고는 다시 안 뜸.
     * 보류 중인 target step 으로 즉시 이동.
     */
    fun confirmStepTransition(dontAskAgain: Boolean) {
        val warning = _uiState.value.pendingStepWarning ?: return
        if (dontAskAgain) {
            when (warning) {
                is StepTransitionWarning.LocalizationLock -> userPrefs.localizationLockSuppressed = true
                is StepTransitionWarning.EditReset -> userPrefs.editResetSuppressed = true
            }
        }
        _uiState.update { it.copy(pendingStepWarning = null) }
        applyStepTransition(warning.target)
    }

    /** 경고 다이얼로그 취소 — 이동 없이 경고 state 만 정리. */
    fun dismissStepTransitionWarning() {
        if (_uiState.value.pendingStepWarning == null) return
        _uiState.update { it.copy(pendingStepWarning = null) }
    }

    private fun applyStepTransition(target: TimelineStep) {
        _uiState.update {
            it.copy(
                currentStep = target,
                isSegmentEditMode = false,
                isRangeSelecting = false,
                rangeTargetSegmentId = null,
                showRangeActionSheet = false,
                selectedSegmentId = null,
                localizationOpen = false,
                showAudioSeparationSheet = false,
                showSubtitleSheet = false,
                showScriptReviewSheet = false,
                showAppendSheet = false,
                showDetailEdit = false,
                showFrameSheet = false,
                showTextOverlaySheet = false,
                previewLangCode = null,
            )
        }
        updateUndoRedoState()
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
        val maxLane = (_uiState.value.bgmLaneCount - 1).coerceAtLeast(0)
        val lane = newLane.coerceIn(0, maxLane)
        bgmClipLaneOverrides[clipId] = lane
        val current = _uiState.value
        val updated = current.bgmClips.map { c ->
            if (c.id == clipId && c.lane != lane) c.copy(lane = lane) else c
        }
        _uiState.value = current.copy(bgmClips = updated)
    }

    /** BGM 영역 lane 개수 직접 set. drag handle 이 부드럽게 여러 step 한 번에 적용. */
    fun onSetBgmLaneCount(count: Int) {
        val current = _uiState.value
        val nextCount = count.coerceIn(1, 8)
        // 마지막 lane 들 안에 clip 이 있으면 그 lane 까지는 유지 (축소 보류).
        val maxOccupiedLane = current.bgmClips.maxOfOrNull { it.lane } ?: -1
        val safeCount = nextCount.coerceAtLeast(maxOccupiedLane + 1)
        if (safeCount == current.bgmLaneCount) return
        _uiState.value = current.copy(bgmLaneCount = safeCount)
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
                    // _uiState.update — concurrent observers (observeClips / observeBgmClips) 와의
                    // race 방지를 위해 atomic CAS. helper 입력은 람다 안에서 최신 current 로부터.
                    _uiState.update { current ->
                        val originalVariant = hasConfirmedOriginalSubtitle(
                            subtitleClips = current.subtitleClips,
                            pendingReviewTargetLangsCsv = project.pendingReviewTargetLangsCsv,
                        )
                        current.copy(
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
                            // null = 검토 대기 없음. 빈 string 도 review pending (original-only review)
                            // 이므로 isNullOrBlank 가 아니라 != null 로 판정.
                            subtitleReviewPending = project.pendingReviewTargetLangsCsv != null,
                            hasOriginalSubtitleVariant = originalVariant,
                        )
                    }
                    if (!hasSeededUndoSnapshot) {
                        hasSeededUndoSnapshot = true
                        // 첫 진입 — DB 의 현재 상태 baseline 만 push. mutation 아니라 stale 마킹 안 함.
                        pushUndoState(markStale = false)
                    }
                    // Hot-path 가드 동기화 — DB 가 fresh (=EnsureLatestRender 가 새로 render 후 false 로
                    // set 했거나 신규 프로젝트가 아직 mutation 없는 상태) 면 in-memory 플래그도 false 로
                    // 풀어 다음 mutation 의 markRenderStale 가 정상 진입 가능.
                    if (!project.isRenderStale && renderStaleMarked) {
                        renderStaleMarked = false
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
        if (shouldRefreshSeparation(project)) {
            separationRefreshGate = TriggerGate.FIRED
            refreshSeparationFreshness(project)
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
            // null 검사 — 빈 string 은 original-only review (langs=[]) 의 pending 신호이므로 그대로 통과.
            project.pendingReviewTargetLangsCsv != null &&
            project.autoSubtitleStatus == AutoJobStatus.READY

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
                is SeparationStatus.Failed,
                is SeparationStatus.Consumed -> clearStaleSeparation()
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
                    numberOfSpeakers = project.numberOfSpeakers,
                    onRenderProgress = { p ->
                        setRenderProgress(p)
                    },
                )
                setRenderProgress(null)
                // The use case wrote FAILED on its own; re-arm so a fresh
                // retry path (button or status reset) can trigger again.
                if (result.isFailure) subtitleGate = TriggerGate.ARMED
            } catch (e: kotlinx.coroutines.CancellationException) {
                setRenderProgress(null)
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
                        numberOfSpeakers = project.numberOfSpeakers,
                        onRenderProgress = { p ->
                            setRenderProgress(p)
                        },
                    )
                    setRenderProgress(null)
                    if (result.isFailure) anyFailure = true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    setRenderProgress(null)
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

    private fun observeClips() {
        viewModelScope.launch {
            combine(
                dubClipRepository.observeClips(projectId),
                subtitleClipRepository.observeClips(projectId),
                imageClipRepository.observeClips(projectId)
            ) { dubs, subs, images -> Triple(dubs, subs, images) }
                .collect { (dubs, subs, images) ->
                    // _uiState.update — concurrent observers (observeProject / observeBgmClips) 와의 race
                    // 방지를 위해 atomic CAS. 다른 observer 와 동일 패턴.
                    _uiState.update { current ->
                        // SSOT — UI chip / export variant picker 산출이 같은 헬퍼 결과를 본다.
                        // subtitleReviewPending 은 observeProject 에서 set 되고 pendingReviewTargetLangsCsv != null 과 동기화.
                        // helper 입력으로 sentinel csv ("pending") 를 넘기되 의미는 "review pending = csv != null 이면 false 반환".
                        val originalVariant = hasConfirmedOriginalSubtitle(
                            subtitleClips = subs,
                            pendingReviewTargetLangsCsv = if (current.subtitleReviewPending) "pending" else null,
                        )
                        current.copy(
                            dubClips = dubs,
                            subtitleClips = subs,
                            imageClips = images,
                            hasOriginalSubtitleVariant = originalVariant,
                        )
                    }
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

    /**
     * @param markStale true 면 EditProject.isRenderStale=true 로 마킹 — 다음 자막/더빙/분리
     *   시점에 EnsureLatestRender 가 새로 BFF render 잡을 보냄. 일반 mutation 후 호출 시 true 가 default;
     *   초기 seed 또는 reset 후 baseline 푸시는 false.
     */
    private fun pushUndoState(markStale: Boolean = true) {
        activeUndoManager().pushState(buildSnapshot())
        updateUndoRedoState()
        if (markStale) markRenderStale()
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

    fun onShowSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleSheet = true)
    }

    fun onDismissSubtitleSheet() {
        _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
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
            // 자막 cue 자체 mutation — markStale=false. invalidateGeneratedResults 가 deleteAllClips 로
            // 방금 수정한 자막을 즉시 지우는 self-deletion 회피. 자막 mutation 은 timeline 구조 변경이
            // 아니므로 render 캐시도 보존 (다국어 재생성도 source 자막이 있어야 가능).
            pushUndoState(markStale = false)
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
        fontFamily: String = com.vibi.shared.domain.model.SubtitleClip.DEFAULT_FONT_FAMILY,
        fontSizeSp: Float = com.vibi.shared.domain.model.SubtitleClip.DEFAULT_FONT_SIZE_SP,
        colorHex: String = com.vibi.shared.domain.model.SubtitleClip.DEFAULT_COLOR_HEX,
        backgroundColorHex: String = com.vibi.shared.domain.model.SubtitleClip.DEFAULT_BACKGROUND_COLOR_HEX,
    ) {
        viewModelScope.launch {
            addSubtitleClip(
                projectId, text, startMs, endMs, position,
                fontFamily, fontSizeSp, colorHex, backgroundColorHex,
            )
            _uiState.value = _uiState.value.copy(showSubtitleSheet = false)
            // 자막 추가 — markStale=false. 자동 생성 자막(`dubbedAudioPaths` / autoSubtitleStatus) 의
            // stale 여부와 무관 + 이미 생성된 cue 들을 invalidateGeneratedResults 가 지우지 않도록.
            pushUndoState(markStale = false)
        }
    }

    fun onMoveDubClip(clipId: String, newStartMs: Long) {
        viewModelScope.launch {
            val clip = _uiState.value.dubClips.find { it.id == clipId } ?: return@launch
            moveDubClip(clip, newStartMs, _uiState.value.videoDurationMs)
            // 더빙 clip 위치 변경 — markStale=false. DubClip 행은 사용자 추가 더빙으로 timeline 구조와
            // 별개 (자동 더빙 결과는 project.dubbedAudioPaths 맵에 따로 저장). 이동만으로 render 캐시
            // 무효화 + 자동 더빙 결과 삭제는 과도.
            pushUndoState(markStale = false)
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
            // 더빙 clip 볼륨 변경 — markStale=false (자막/더빙 clip mutation 정책).
            pushUndoState(markStale = false)
        }
    }

    fun onDeleteSelectedClip() {
        viewModelScope.launch {
            val state = _uiState.value
            // 자막/더빙 클립 삭제는 자동 생성 결과 stale 여부에 영향 없음 → markStale=false.
            // image clip (overlay) 은 timeline structure 변경이라 markStale=true 필요 → mixed 시점은
            // 실제 삭제된 카테고리에 따라 분기.
            val deletedImage = state.selectedImageClipId != null
            state.selectedDubClipId?.let { dubClipId ->
                deleteDubClip(dubClipId)
                subtitleClipRepository.deleteClipsBySourceDubClipId(dubClipId)
            }
            state.selectedSubtitleClipId?.let { subtitleClipRepository.deleteClip(it) }
            state.selectedImageClipId?.let { imageClipRepository.deleteClip(it) }
            _uiState.value = _uiState.value.copy(
                selectedDubClipId = null,
                selectedSubtitleClipId = null,
                selectedImageClipId = null
            )
            pushUndoState(markStale = deletedImage)
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
        return com.vibi.shared.domain.util.pickLowestFreeLane(
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
            // 자막 위치 변경 — markStale=false (자막 clip mutation 정책).
            pushUndoState(markStale = false)
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
            // 자막 스타일 일괄 변경 — markStale=false (자막 clip mutation 정책).
            pushUndoState(markStale = false)
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
            // 자막 스타일 변경 — markStale=false (자막 clip mutation 정책).
            pushUndoState(markStale = false)
        }
    }

    fun onUndo() {
        viewModelScope.launch {
            val snapshot = activeUndoManager().undo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
            // undo 도 segments / volumes / speeds 를 변경하므로 render 캐시 무효화.
            markRenderStale()
        }
    }

    fun onRedo() {
        viewModelScope.launch {
            val snapshot = activeUndoManager().redo() ?: return@launch
            restoreSnapshot(snapshot)
            updateUndoRedoState()
            markRenderStale()
        }
    }

    private suspend fun restoreSnapshot(snapshot: TimelineSnapshot) {
        // N+1 insert 회피 — DAO insertAll 한 번 IPC.
        dubClipRepository.deleteAllClips(projectId)
        dubClipRepository.addClips(snapshot.dubClips)
        subtitleClipRepository.deleteAllClips(projectId)
        subtitleClipRepository.addClips(snapshot.subtitleClips)
        imageClipRepository.deleteAllClips(projectId)
        imageClipRepository.addClips(snapshot.imageClips)
        segmentRepository.deleteAllByProjectId(projectId)
        segmentRepository.addSegments(snapshot.segments)
        textOverlayRepository.deleteAllByProjectId(projectId)
        textOverlayRepository.addOverlays(snapshot.textOverlays)
        bgmClipRepository.deleteAllByProjectId(projectId)
        bgmClipRepository.addClips(snapshot.bgmClips)
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
     * Timeline 헤더 "저장" 버튼 — variant 1개면 즉시 모두 렌더 + 갤러리 저장.
     * 2+ 면 picker sheet 노출하고 사용자 선택을 기다림 ([onConfirmSavePicker] 가 실제 호출).
     */
    fun onSaveAllVariants() {
        if (_uiState.value.saveStatus is SaveStatus.RUNNING) return
        if (_uiState.value.exportVariantPicker != null) return
        // 새 시도 진입 — 직전 FAILED 흔적 (snackbar) 제거. RUNNING 가드는 위에서 통과했으므로 충돌 없음.
        run {
            val s = _uiState.value
            _uiState.value = s.copy(
                saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
                shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
            )
        }
        viewModelScope.launch {
            val variants = listExportVariants(projectId).getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    saveStatus = SaveStatus.FAILED(e.message ?: "저장 준비 실패")
                )
                return@launch
            }
            if (variants.size <= 1) {
                runSaveAllVariants(selectedKeys = null)
            } else {
                _uiState.value = _uiState.value.copy(
                    // Picker 와 다른 sheet 의 z-order 충돌 방지 — picker open 직전에 동시 열려있을 수 있는
                    // sheet flag 들 하향.
                    showScriptReviewSheet = false,
                    exportVariantPicker = ExportVariantPickerState.Save(
                        variants = variants,
                        selected = variants.map { it.key }.toSet(),
                    )
                )
            }
        }
    }

    /** Picker sheet 의 "저장" 버튼 — 현재 선택된 variant 들로 본 저장 흐름 시작. */
    fun onConfirmSavePicker() {
        val current = _uiState.value.exportVariantPicker as? ExportVariantPickerState.Save ?: return
        if (current.selected.isEmpty()) return
        _uiState.value = _uiState.value.copy(exportVariantPicker = null)
        viewModelScope.launch { runSaveAllVariants(selectedKeys = current.selected) }
    }

    /** Picker 안에서 항목 체크박스 토글. */
    fun onToggleSavePickerVariant(key: String) {
        val current = _uiState.value.exportVariantPicker as? ExportVariantPickerState.Save ?: return
        val next = if (key in current.selected) current.selected - key else current.selected + key
        _uiState.value = _uiState.value.copy(
            exportVariantPicker = current.copy(selected = next)
        )
    }

    /**
     * Save / Share 양쪽 picker 의 취소 버튼.
     *
     * picker 취소가 새 시도의 entry 라는 의미 — 직전 FAILED 메시지는 클리어해서 snackbar 잔존 방지.
     * RUNNING 중에는 picker 가 떠있을 수 없으니 그 상태는 보존 (RUNNING 가드 onSaveAllVariants/onShareExport 에 있음).
     */
    fun onCancelExportVariantPicker() {
        val s = _uiState.value
        if (s.exportVariantPicker == null) return
        _uiState.value = s.copy(
            exportVariantPicker = null,
            saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
            shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
        )
    }

    private suspend fun runSaveAllVariants(selectedKeys: Set<String>?) {
        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.RUNNING(0))
        val result = saveAllVariants(
            projectId = projectId,
            onProgress = { percent ->
                _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.RUNNING(percent))
            },
            selectedVariantKeys = selectedKeys,
        )
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

    /** Snackbar 닫기 등 — UI 에서 호출 후 idle 로 복귀. */
    fun onClearSaveStatus() {
        _uiState.value = _uiState.value.copy(saveStatus = SaveStatus.IDLE)
    }

    /**
     * Timeline 헤더 "공유" 버튼 — variant 1개면 즉시 그것을 렌더해서 share sheet.
     * 2+ 면 picker sheet 노출 (multi-select, default "original" 한 건).
     */
    fun onShareExport() {
        if (_uiState.value.shareStatus is ShareStatus.RUNNING) return
        if (_uiState.value.segments.isEmpty()) return
        if (_uiState.value.exportVariantPicker != null) return
        // 새 시도 진입 — 직전 FAILED 흔적 제거. RUNNING 가드 위에서 통과했으므로 충돌 없음.
        run {
            val s = _uiState.value
            _uiState.value = s.copy(
                saveStatus = if (s.saveStatus is SaveStatus.FAILED) SaveStatus.IDLE else s.saveStatus,
                shareStatus = if (s.shareStatus is ShareStatus.FAILED) ShareStatus.IDLE else s.shareStatus,
            )
        }
        viewModelScope.launch {
            val variants = listExportVariants(projectId).getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    shareStatus = ShareStatus.FAILED(e.message ?: "공유 준비 실패")
                )
                return@launch
            }
            if (variants.size <= 1) {
                runShareExport(selectedKeys = null)
            } else {
                // computeAllVariantKeys 가 KEY_ORIGINAL 을 항상 첫 항목으로 보장 — fallback (variants.first())
                // 은 invariant 위반 시 안전망. 실제 호출 경로에서 발동되지 않는 dead path.
                val defaultKey = variants.firstOrNull { it.key == ExportVariant.KEY_ORIGINAL }?.key
                    ?: variants.first().key
                _uiState.value = _uiState.value.copy(
                    // Picker 와 다른 sheet 의 z-order 충돌 방지 — picker open 직전에 동시 열려있을 수 있는
                    // sheet flag 들 하향. (RUNNING 가드는 위에서 통과했으므로 진행 중 흐름은 영향 없음.)
                    showScriptReviewSheet = false,
                    exportVariantPicker = ExportVariantPickerState.Share(
                        variants = variants,
                        selected = setOf(defaultKey),
                    )
                )
            }
        }
    }

    /** Share picker 의 체크박스 토글. 빈 selection 도 허용 — confirm 버튼 측에서 가드. */
    fun onToggleSharePickerVariant(key: String) {
        val current = _uiState.value.exportVariantPicker as? ExportVariantPickerState.Share ?: return
        val next = if (key in current.selected) current.selected - key else current.selected + key
        _uiState.value = _uiState.value.copy(
            exportVariantPicker = current.copy(selected = next)
        )
    }

    /** Share picker "공유" 버튼 — 선택된 variant 들을 렌더 후 share sheet 으로 다중 첨부. */
    fun onConfirmSharePicker() {
        val current = _uiState.value.exportVariantPicker as? ExportVariantPickerState.Share ?: return
        if (current.selected.isEmpty()) return
        val keys = current.selected
        _uiState.value = _uiState.value.copy(exportVariantPicker = null)
        viewModelScope.launch { runShareExport(selectedKeys = keys) }
    }

    private suspend fun runShareExport(selectedKeys: Set<String>?) {
        _uiState.value = _uiState.value.copy(shareStatus = ShareStatus.RUNNING(0))
        val result = saveAllVariants(
            projectId = projectId,
            onProgress = { percent ->
                _uiState.value = _uiState.value.copy(shareStatus = ShareStatus.RUNNING(percent))
            },
            saveToGallery = false,
            selectedVariantKeys = selectedKeys,
        )
        result.fold(
            onSuccess = { variants ->
                val paths = variants.map { it.outputPath }
                if (paths.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        shareStatus = ShareStatus.FAILED("공유할 결과가 없음")
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
                        _uiState.value = _uiState.value.copy(
                            shareStatus = ShareStatus.FAILED(e.message ?: "공유 실패")
                        )
                    }
                )
            },
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(
                    shareStatus = ShareStatus.FAILED(e.message ?: "공유 실패")
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
     * 영상편집 액션은 통합 EditAudio undo 스택에 push — 음원분리/음원삽입과 같이 단일 흐름으로 되돌릴 수 있다.
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
            showScriptReviewSheet = false,
            localizationOpen = false,
            showDetailEdit = false,
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
        updateUndoRedoState()
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
     */
    private suspend fun commitSegmentEdit() {
        resetTimelineDerivedResults()
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

    /**
     * 영상편집 commit 후 stale 가 된 downstream 산출물 정리. 자막/더빙 결과는 segment 가 바뀌면
     * 항상 stale 이므로 wipe. 음원분리 directive 도 segment 좌표 기반이라 wipe.
     * BGM 은 구간 액션 시 ripple/split 로 영상과 동기 갱신되므로 보존.
     */
    private suspend fun resetTimelineDerivedResults() {
        _uiState.value.separationDirectives.forEach { separationDirectiveRepository.delete(it.id) }
        _uiState.value.segments.filter { it.type == SegmentType.VIDEO }
            .forEach { updateSegmentVolume(it.id, 1f) }
        // 진행 중 잡 전부 취소 — segment 자체가 바뀌므로 결과가 stale.
        separationJobs.values.forEach { it.cancel() }
        separationJobs.clear()
        bgmSeparationJob?.cancel()
        bgmSeparationJob = null
        separationGate = TriggerGate.ARMED

        subtitleClipRepository.deleteAllClips(projectId)
        dubClipRepository.deleteAllClips(projectId)
        subtitleGate = TriggerGate.ARMED
        dubGate = TriggerGate.ARMED
        reviewSheetGate = TriggerGate.ARMED
        renderStaleMarked = true

        editProjectRepository.getProject(projectId)?.let { p ->
            // 영상편집 commit — 동시 분리 list 전체 비움 + legacy 단일 슬롯 클리어.
            editProjectRepository.updateProject(
                p.clearAutoSubtitleDub().clearSeparation().copy(processingSeparations = emptyList())
            )
        }

        _uiState.update { s ->
            s.copy(
                audioSeparation = null,
                showAudioSeparationSheet = false,
                separationDirectives = emptyList(),
                processingSeparations = emptyList(),
                separationStatus = AutoJobStatus.IDLE,
            ).clearAutoSubtitleDubUiState().copy(
                showDetailEdit = false,
                localizationOpen = false,
            )
        }
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
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        val wasSegmentEdit = state.isSegmentEditMode
        resetRangeMode()
        viewModelScope.launch {
            var lastDuplicated: Segment? = null
            slices.forEach { s ->
                lastDuplicated = duplicateSegmentRange(s.segmentId, s.localStart, s.localEnd)
            }
            applyBgmRangeDuplicate(start, end)
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
            applyBgmRippleDelete(start, end)
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
            applyBgmRangeVolume(start, end, value)
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
                // 사용자 직관: "2x speed → 그 구간 글로벌 길이 절반으로 줄어듦". 선택 구간을
                // 그대로 split 한 뒤 middle 에만 speed 적용 → middle.global = source / newSpeed.
                // (이전: source 를 newSpeed/curSpeed 배 확장해 글로벌 길이 보존했지만 사용자
                // 호소 — 2배 올리면 길이가 늘어나 보임 — 와 반대 방향. 직관 우선.)
                val r = splitSegment(s.segmentId, s.localStart, s.localEnd)
                updateSegmentSpeed(r.middle.id, newSpeed)
                lastMiddleId = r.middle.id
            }
            applyBgmRangeSpeed(start, end, newSpeed)
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
                    val newEffective = if (newSpeed > 0f) (bgm.sourceDurationMs / newSpeed).toLong()
                        else bgm.sourceDurationMs
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
                        lane = bgm.lane,
                    )
                )
            }
        }
    }

    // ── 채팅 dispatcher 전용 code-path ────────────────────────────────────────────
    // UI 슬라이더용 onSetPendingRangeStart/End 는 입력값을 현 pendingRange 와 mode bounds 에 맞춰
    // clamp 해버려서, range 모드가 아닌 상태에서 dispatcher 가 startMs/endMs 를 그대로 넘기면
    // 0 으로 깎이는 버그가 있었다. 본 그룹 메서드들은 UI state 를 거치지 않고 use case 를 직접
    // 호출 — chat tool 이 자연어로 받은 [start, end] 를 그대로 적용. range 모드 진입/종료 부수효과 0.

    /**
     * 입력 [start, end] 가 timeline 안에 들어가고 최소 길이 충족하는지 검증해 slice. 잘못된 입력은
     * IllegalArgumentException — dispatcher 가 [DispatchResult.Failure] 로 노출, 사용자에게 가시화.
     */
    private fun chatRangeSlices(start: Long, end: Long): List<SegmentRangeSlice> {
        val total = _uiState.value.videoDurationMs.coerceAtLeast(0L)
        if (start < 0L || end > total || end - start < MIN_RANGE_MS) {
            throw IllegalArgumentException(
                "range 가 유효하지 않습니다 (start=$start, end=$end, total=$total)"
            )
        }
        val slices = sliceGlobalRange(start, end).sortedByDescending { it.order }
        if (slices.isEmpty()) {
            throw IllegalArgumentException("range 가 video segment 와 겹치지 않습니다")
        }
        return slices
    }

    fun applyDeleteRangeFromChat(start: Long, end: Long) {
        val slices = chatRangeSlices(start, end)
        viewModelScope.launch {
            slices.forEach { sl -> removeSegmentRange(sl.segmentId, sl.localStart, sl.localEnd) }
            refreshSegmentsStateFromDb()
            pushUndoState()
        }
    }

    fun applyDuplicateRangeFromChat(start: Long, end: Long) {
        val slices = chatRangeSlices(start, end)
        viewModelScope.launch {
            slices.forEach { sl -> duplicateSegmentRange(sl.segmentId, sl.localStart, sl.localEnd) }
            refreshSegmentsStateFromDb()
            pushUndoState()
        }
    }

    fun applyVolumeRangeFromChat(start: Long, end: Long, value: Float) {
        val slices = chatRangeSlices(start, end)
        val v = value.coerceIn(0f, 2f)
        viewModelScope.launch {
            slices.forEach { sl ->
                val r = splitSegment(sl.segmentId, sl.localStart, sl.localEnd)
                updateSegmentVolume(r.middle.id, v)
            }
            refreshSegmentsStateFromDb()
            pushUndoState()
        }
    }

    fun applySpeedRangeFromChat(start: Long, end: Long, value: Float) {
        val slices = chatRangeSlices(start, end)
        val v = value.coerceIn(0.25f, 4f)
        viewModelScope.launch {
            slices.forEach { sl ->
                val r = splitSegment(sl.segmentId, sl.localStart, sl.localEnd)
                updateSegmentSpeed(r.middle.id, v)
            }
            refreshSegmentsStateFromDb()
            pushUndoState()
        }
    }

    /**
     * 채팅 dispatcher 전용 음원분리. UI sheet 진입 없이 audioSeparation state 만 직접 시드해
     * [onStartSeparation] 백그라운드 잡 흐름에 합류 — 진행은 timeline directive 막대로 노출.
     *
     * BGM 대상이면 [onStartBgmSeparation] 으로 위임 (전체 클립 단위라 range 무관).
     * Video 대상이면 trim 범위가 있을 때만 [AudioSeparationUiState.rangeStartMs/EndMs] 에 반영.
     */
    suspend fun applySeparateRangeFromChat(
        segmentId: String?,
        bgmClipId: String?,
        trimStartMs: Long?,
        trimEndMs: Long?,
    ) {
        if (bgmClipId != null) {
            val bgm = _uiState.value.bgmClips.firstOrNull { it.id == bgmClipId }
                ?: throw IllegalArgumentException("bgmClipId 가 projectContext.bgmClips 에 없습니다")
            onStartBgmSeparation(bgm.id)
            watchBgmSeparationForChat()
            awaitBgmSeparationCompleteForChat()
            return
        }
        val segId = segmentId
            ?: throw IllegalArgumentException("segmentId 또는 bgmClipId 중 하나는 필수입니다")
        val seg = _uiState.value.segments.firstOrNull { it.id == segId }
            ?: throw IllegalArgumentException("segmentId 가 projectContext.segments 에 없습니다")
        if (seg.type != SegmentType.VIDEO) {
            throw IllegalArgumentException("video segment 만 음원분리 가능합니다")
        }
        val rs = trimStartMs
        val re = trimEndMs
        val (rangeStart, rangeEnd) = if (rs != null && re != null && re > rs) rs to re else null to null
        // 동시 분리 모델에서 video 분리 결과는 directive 로 자동 commit + processingSeparations 에서 사라짐.
        // 시작 직전 directive id snapshot 으로 새로 추가된 directive 를 식별한다.
        val priorDirectiveIds = _uiState.value.separationDirectives.map { it.id }.toSet()
        val priorProcessingTokens = _uiState.value.processingSeparations.map { it.clientToken }.toSet()
        // numberOfSpeakers 는 Perso audio-separation 전용 endpoint 가 받지 않는 dead 인자
        // (BFF SeparationSpec 에 남아있긴 하나 PersoClient.submitAudioSeparation 에 미전달).
        // chat 경로는 default 1 로 채움 — UI sheet 경로의 호환성 위해 필드 자체는 유지.
        _uiState.value = _uiState.value.copy(
            audioSeparation = AudioSeparationUiState(
                segmentId = segId,
                step = AudioSeparationStep.SETUP,
                numberOfSpeakers = 1,
                rangeStartMs = rangeStart,
                rangeEndMs = rangeEnd,
            ),
            showAudioSeparationSheet = false,
            isPlaying = false,
        )
        onStartSeparation()
        // onStartSeparation 이 audioSeparation 을 null 로 비우고 processingSeparations 에 entry 를 추가했다.
        // priorProcessingTokens 와 비교해 새로 추가된 token 을 찾는다 — 그 잡의 완료/실패를 추적.
        val newToken = _uiState.value.processingSeparations
            .firstOrNull { it.clientToken !in priorProcessingTokens }
            ?.clientToken
            ?: return  // 시작이 즉시 실패해 entry 가 안 만들어진 케이스 — handleSeparationFailure 가 audioSeparation FAILED 로 마킹.
        watchVideoSeparationForChat(newToken, priorDirectiveIds)
        awaitVideoSeparationCompleteForChat(newToken, priorDirectiveIds)
    }

    /**
     * Video 분리 (chat 경로) 의 terminal 상태 대기 — token 이 processingSeparations 에서 사라질 때까지.
     * 종료 시점의 audioSeparation 을 반환 (FAILED 면 non-null, 성공이면 null 또는 직전 상태). 호출자가 분기.
     */
    private suspend fun awaitVideoSeparationTerminal(
        clientToken: String,
        priorDirectiveIds: Set<String>,
    ): AudioSeparationUiState? {
        return _uiState
            .map { Triple(it.processingSeparations, it.separationDirectives, it.audioSeparation) }
            .distinctUntilChanged()
            .first { (processing, dirs, sep) ->
                val stillRunning = processing.any { it.clientToken == clientToken }
                if (stillRunning) return@first false
                val hasNewDirective = dirs.any { it.id !in priorDirectiveIds }
                val failedSheet = sep?.step == AudioSeparationStep.FAILED
                hasNewDirective || failedSheet
            }
            .third
    }

    private suspend fun awaitVideoSeparationCompleteForChat(
        clientToken: String,
        priorDirectiveIds: Set<String>,
    ) {
        val sep = awaitVideoSeparationTerminal(clientToken, priorDirectiveIds)
        if (sep?.step == AudioSeparationStep.FAILED) {
            throw IllegalStateException(sep.errorMessage ?: "음원 분리 실패")
        }
    }

    private fun watchVideoSeparationForChat(clientToken: String, priorDirectiveIds: Set<String>) {
        viewModelScope.launch {
            val sep = awaitVideoSeparationTerminal(clientToken, priorDirectiveIds)
            if (sep?.step == AudioSeparationStep.FAILED) {
                val err = sep.errorMessage ?: "알 수 없는 오류"
                _chatAssistantEvents.emit("⚠ 음원 분리에 실패했습니다: $err")
            } else {
                _chatAssistantEvents.emit("음원 분리가 완료됐습니다 — timeline 의 stem 막대를 확인하세요.")
            }
        }
    }

    /**
     * BGM 분리 (chat 경로) 의 완료/실패 대기. audioSeparation 싱글 state 기반 — 기존 video 흐름이
     * 사용하던 로직 그대로 (video 흐름은 processingSeparations 로 분리됐다).
     */
    private suspend fun awaitBgmSeparationCompleteForChat() {
        val terminal = _uiState
            .map { it.audioSeparation }
            .distinctUntilChanged()
            .first { sep ->
                // BGM 흐름: PICK_STEMS 에서 onConfirmBgmStemMix 가 audioSeparation=null 로 만들고 BGM 클립을 교체.
                // 그래서 "audioSeparation 가 null 로 변함 + 직전엔 PICK_STEMS/PROCESSING 였음" = 성공.
                // FAILED 단계 도달 = 실패.
                sep?.step == AudioSeparationStep.FAILED || sep == null
            }
        if (terminal?.step == AudioSeparationStep.FAILED) {
            throw IllegalStateException(terminal.errorMessage ?: "음원 분리 실패")
        }
    }

    /**
     * BGM 분리 (chat 경로) 결과를 채팅 thread 로 push.
     */
    private fun watchBgmSeparationForChat() {
        viewModelScope.launch {
            val terminal = _uiState
                .mapNotNull { it.audioSeparation?.step }
                .filter { it == AudioSeparationStep.PICK_STEMS || it == AudioSeparationStep.FAILED }
                .first()
            when (terminal) {
                AudioSeparationStep.PICK_STEMS ->
                    _chatAssistantEvents.emit("음원 분리가 완료됐습니다 — timeline 의 stem 막대를 확인하세요.")
                AudioSeparationStep.FAILED -> {
                    val err = _uiState.value.audioSeparation?.errorMessage ?: "알 수 없는 오류"
                    _chatAssistantEvents.emit("⚠ 음원 분리에 실패했습니다: $err")
                }
                else -> {}
            }
        }
    }

    /**
     * 채팅 dispatcher 전용 stem 볼륨 변경. UI 의 [onUpdateStemVolume] 은 sheet 가 열려있고
     * (`audioSeparation != null`) 편집 모드에 진입한 상태(`editingDirectiveId != null`)에만
     * 동작하는 silent return 가 3중으로 걸려 있어서, 채팅으로 호출하면 아무 일도 안 일어남.
     *
     * 본 메서드는 UI state 와 무관하게 [SeparationDirective.selections] 를 직접 갱신해
     * persist — export render 가 새 볼륨으로 amix 한다. sheet 가 열려있다면 in-memory
     * 미러도 함께 갱신해 즉시 preview 반영.
     *
     * stemId 가 어느 directive 에도 없으면 throw — dispatcher 가 사용자에게 가시화한다.
     */
    suspend fun applyUpdateStemVolumeFromChat(stemId: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 2f)
        val directive = _uiState.value.separationDirectives.firstOrNull { d ->
            d.selections.any { it.stemId == stemId }
        } ?: throw IllegalArgumentException(
            "stemId '$stemId' 에 해당하는 분리 결과가 없습니다 — 음원분리를 먼저 진행하세요"
        )
        val updatedSelections = directive.selections.map { sel ->
            if (sel.stemId == stemId) sel.copy(volume = clamped) else sel
        }
        separationDirectiveRepository.add(directive.copy(selections = updatedSelections))
        // sheet 가 열려있으면 in-memory state 도 동기화 — 사용자가 sheet 안에서 chat 호출한 경우
        // slider 값이 즉시 반영되도록.
        val sep = _uiState.value.audioSeparation
        if (sep != null) {
            val current = sep.selections[stemId]
            if (current != null) {
                val nextSelections = sep.selections + (stemId to current.copy(volume = clamped))
                updateSeparation { it.copy(selections = nextSelections) }
            }
        }
    }

    /**
     * 채팅 dispatcher 전용 — 자막 흐름의 1단계 (transcribe_for_subtitles). STT 만 실행해서
     * 원본 스크립트를 lang="" SubtitleClip 으로 저장한 뒤, SRT 본문을 [chatAssistantEvents]
     * 로 push 한다. 사용자는 채팅에서 그 스크립트를 보고 confirm 또는 수정 요청 → Gemini 가
     * `apply_subtitles_with_script` 로 [applyApplySubtitlesWithScriptFromChat] 호출.
     *
     * 기존 [GenerateOriginalScriptUseCase] 를 그대로 재사용 — UI 의 "스크립트 생성" 버튼과
     * 동일한 영속화. 사용자가 채팅 외에 timeline UI 로 스크립트를 추가 편집해도 정합.
     */
    suspend fun applyTranscribeForSubtitlesFromChat(targetLanguageCodes: List<String>) {
        val source = _uiState.value.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) {
            throw IllegalStateException("source video 없음 — timeline 에 영상을 먼저 올리세요")
        }
        val targets = targetLanguageCodes.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) {
            throw IllegalArgumentException("targetLanguageCodes 가 비었습니다")
        }
        println("[Chat] applyTranscribeForSubtitlesFromChat targets=$targets source=$source")
        val r = generateOriginalScript(
            projectId = projectId,
            sourceUri = source,
            mediaType = "VIDEO",
            onRenderProgress = { p -> setRenderProgress(p) },
        )
        setRenderProgress(null)
        if (r.isFailure) {
            val err = r.exceptionOrNull()?.message ?: "스크립트 생성 실패"
            _chatAssistantEvents.emit("⚠ 스크립트 생성에 실패했습니다: $err")
            throw IllegalStateException(err)
        }
        // 저장된 lang="" 클립을 SRT 로 직렬화 → 채팅에 사람-친화 포맷으로 push.
        val clips = subtitleClipRepository.observeClips(projectId).first()
            .filter { it.languageCode.isBlank() }
            .sortedBy { it.startMs }
        if (clips.isEmpty()) {
            _chatAssistantEvents.emit("⚠ STT 결과가 비어있습니다 — 영상에 음성이 없을 수 있습니다.")
            throw IllegalStateException("STT 결과 비어있음")
        }
        val script = formatScriptForChat(clips)
        val targetLabel = targets.joinToString(", ")
        _chatAssistantEvents.emit(
            "스크립트가 준비됐습니다 (${clips.size}줄):\n\n$script\n\n" +
                "이대로 [$targetLabel] 자막을 만들까요? 수정 사항이 있으면 알려주세요 " +
                "(예: \"3번째 줄을 X로 바꿔\")."
        )
    }

    /**
     * 채팅 dispatcher 전용 — 자막 흐름의 2단계 (apply_subtitles_with_script). 1단계에서 저장된
     * lang="" 클립 (사용자가 수정한 SRT 가 있으면 그것으로 교체) 을 source 로 다국어 자막 생성.
     *
     * @param srt 사용자가 수정 요청을 했고 Gemini 가 수정된 SRT 를 보낸 경우 — lang="" 클립을
     *            이 SRT 의 cue 들로 교체. null 이면 1단계에서 저장된 클립 그대로 사용.
     * @param targetLanguageCodes 번역 대상. 1단계의 targets 와 같아야 정상이지만 검증 안 함 (Gemini 책임).
     */
    suspend fun applyApplySubtitlesWithScriptFromChat(srt: String?, targetLanguageCodes: List<String>) {
        val targets = targetLanguageCodes.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) {
            throw IllegalArgumentException("targetLanguageCodes 가 비었습니다")
        }
        println("[Chat] applyApplySubtitlesWithScriptFromChat srtProvided=${srt != null} targets=$targets")

        // 1) srt 인자가 있으면 lang="" 클립 교체. 없으면 1단계 결과 그대로 사용.
        if (!srt.isNullOrBlank()) {
            val cues = runCatching { SrtParser.parse(srt) }.getOrElse {
                _chatAssistantEvents.emit("⚠ 수정된 SRT 파싱 실패: ${it.message}")
                throw IllegalStateException("SRT 파싱 실패: ${it.message}")
            }
            if (cues.isEmpty()) {
                _chatAssistantEvents.emit("⚠ 수정된 SRT 에 cue 가 없습니다.")
                throw IllegalStateException("SRT 에 cue 가 없음")
            }
            val existing = subtitleClipRepository.observeClips(projectId).first()
                .filter { it.languageCode.isBlank() }
            existing.forEach { subtitleClipRepository.deleteClip(it.id) }
            val rows = cues.map { cue ->
                SubtitleClip(
                    id = generateId(),
                    projectId = projectId,
                    text = cue.text,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    position = SubtitlePosition(),
                    source = SubtitleSource.AUTO,
                    languageCode = "",
                )
            }
            subtitleClipRepository.addClips(rows)
        }

        // 프로젝트 targetLanguageCodes 에 신규 lang merge — UI preview 토글 등에서 사용.
        editProjectRepository.getProject(projectId)?.let { p ->
            val merged = (p.targetLanguageCodes + targets).distinct()
            if (merged != p.targetLanguageCodes) {
                editProjectRepository.updateProject(p.copy(targetLanguageCodes = merged))
            }
        }

        // 2) lang="" 를 source 로 다국어 자막 생성.
        val r = regenerateSubtitles(
            projectId = projectId,
            sourceLanguageCode = "",
            targetLanguageCodes = targets,
        )
        if (r.isFailure) {
            val err = r.exceptionOrNull()?.message ?: "자막 생성 실패"
            _chatAssistantEvents.emit("⚠ 자막 생성에 실패했습니다: $err")
            throw IllegalStateException(err)
        }
        // chat 흐름은 원본 자막을 export variant 로 노출하려는 의도가 아님 — source 역할만 한
        // lang="" 클립이 살아있으면 hasConfirmedOriginalSubtitle 이 true 가 되어 SAVE picker 에
        // KEY_ORIGINAL_SUBTITLE 변종이 자동 포함되고 default 체크되어 "원본 자막 영상" 까지 생성됨.
        // target 생성이 끝났으므로 source 는 정리.
        val sourceClips = subtitleClipRepository.observeClips(projectId).first()
            .filter { it.languageCode.isBlank() }
        sourceClips.forEach { subtitleClipRepository.deleteClip(it.id) }

        val current = _uiState.value.previewLangCode
        if (current == null || current !in _uiState.value.targetLanguageCodes) {
            _uiState.value = _uiState.value.copy(previewLangCode = targets.first())
        }
        _chatAssistantEvents.emit("자막이 준비됐습니다 — [${targets.joinToString(", ")}]")
    }

    /**
     * SubtitleClip 들을 채팅 표시용으로 포맷. 정식 SRT (timestamp + index + text) 가 아니라
     * 1-based 번호 + 텍스트만 — 사용자가 "3번째 줄 바꿔줘" 같이 가리키기 쉬운 형태.
     * Gemini 는 직전 turn 에 이 포맷을 보고 수정 요청 응답 시 SRT 를 새로 생성한다.
     */
    private fun formatScriptForChat(clips: List<SubtitleClip>): String {
        val sb = StringBuilder()
        clips.forEachIndexed { idx, c ->
            sb.append(idx + 1).append(". ").append(c.text.trim().replace('\n', ' '))
            if (idx < clips.size - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * 채팅 dispatcher 전용 자막 생성. UI flow(onSetLocalizationMode/onToggleLocalizationLang/
     * onStartLocalization) 우회 — review-mode 우회, silent return 분기 throw 로 가시화.
     * 동기 검증(throw)은 dispatcher 가 즉시 받고, BFF 호출은 기존 apply…FromChat 들과
     * 동일하게 viewModelScope.launch 로 fire-and-forget.
     */
    suspend fun applyGenerateSubtitlesFromChat(targetLanguageCodes: List<String>) {
        val source = _uiState.value.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) {
            throw IllegalStateException("source video 없음 — timeline 에 영상을 먼저 올리세요")
        }
        val langs = targetLanguageCodes.filter { it.isNotBlank() }.distinct()
        if (langs.isEmpty()) {
            throw IllegalArgumentException("targetLanguageCodes 가 비었습니다")
        }
        println("[Chat] applyGenerateSubtitlesFromChat langs=$langs source=$source")
        editProjectRepository.getProject(projectId)?.let { p ->
            val merged = (p.targetLanguageCodes + langs).distinct()
            if (merged != p.targetLanguageCodes) {
                editProjectRepository.updateProject(p.copy(targetLanguageCodes = merged))
            }
        }
        val r = generateAutoSubtitles(
            projectId = projectId,
            sourceUri = source,
            mediaType = "VIDEO",
            sourceLanguageCode = "auto",
            targetLanguageCodes = langs,
            numberOfSpeakers = 1,
            onRenderProgress = { p -> setRenderProgress(p) },
            includeOriginalLanguage = false,
        )
        setRenderProgress(null)
        println("[Chat] subtitle result isSuccess=${r.isSuccess} err=${r.exceptionOrNull()?.message}")
        if (r.isSuccess) {
            val current = _uiState.value.previewLangCode
            if (current == null || current !in _uiState.value.targetLanguageCodes) {
                _uiState.value = _uiState.value.copy(previewLangCode = langs.first())
            }
            _chatAssistantEvents.emit("자막이 준비됐습니다 — [${langs.joinToString(", ")}]")
        } else {
            val err = r.exceptionOrNull()?.message ?: "자막 생성 실패"
            _chatAssistantEvents.emit("⚠ 자막 생성에 실패했습니다: $err")
            throw IllegalStateException(err)
        }
    }

    /**
     * 채팅 dispatcher 전용 더빙 생성. UI flow 우회 — silent return 분기 throw 로 가시화.
     * tool def 가 single lang 이라 1회만 호출.
     */
    suspend fun applyGenerateDubFromChat(targetLanguageCode: String) {
        val source = _uiState.value.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) {
            throw IllegalStateException("source video 없음 — timeline 에 영상을 먼저 올리세요")
        }
        val lang = targetLanguageCode.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("targetLanguageCode 가 비었습니다")
        println("[Chat] applyGenerateDubFromChat lang=$lang source=$source")
        editProjectRepository.getProject(projectId)?.let { p ->
            val merged = (p.targetLanguageCodes + lang).distinct()
            if (merged != p.targetLanguageCodes) {
                editProjectRepository.updateProject(p.copy(targetLanguageCodes = merged))
            }
        }
        val result = runCatching {
            generateAutoDub(
                projectId = projectId,
                sourceUri = source,
                mediaType = "VIDEO",
                sourceLanguageCode = "auto",
                targetLanguageCode = lang,
                numberOfSpeakers = 1,
                onRenderProgress = { p -> setRenderProgress(p) },
            )
        }
        setRenderProgress(null)
        result.onSuccess {
            _chatAssistantEvents.emit("더빙이 준비됐습니다 — [$lang]")
        }.onFailure {
            println("[Chat] dub failed lang=$lang err=${it.message}")
            val err = it.message ?: "알 수 없는 오류"
            _chatAssistantEvents.emit("⚠ 더빙 생성에 실패했습니다: $err")
            throw IllegalStateException(err)
        }
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

    /**
     * 채팅 전용 — BGM 클립을 timeline range 에 정렬. BgmClip 에 trim 필드가 없어 `speedScale` 로
     * stretch. UseCase 가 [BgmClip.MIN_SPEED, BgmClip.MAX_SPEED] 로 silent clamp 하므로
     * out-of-range 일 때 dispatcher 가 Success 반환 후 BGM 이 어긋나는 사고 방지 위해 사전 throw.
     */
    suspend fun applyUpdateBgmRangeFromChat(clipId: String, newStartMs: Long, newEndMs: Long) {
        require(newEndMs > newStartMs) {
            "끝 지점(${newEndMs}ms)은 시작 지점(${newStartMs}ms)보다 커야 해요."
        }
        val clip = _uiState.value.bgmClips.firstOrNull { it.id == clipId }
            ?: throw IllegalArgumentException("BGM 클립을 찾을 수 없어요 (id=$clipId).")
        val desiredDuration = newEndMs - newStartMs
        val newSpeed = clip.sourceDurationMs.toFloat() / desiredDuration.toFloat()
        require(newSpeed in BgmClip.MIN_SPEED..BgmClip.MAX_SPEED) {
            "요청한 길이(${desiredDuration}ms)는 BGM 속도 한계(${BgmClip.MIN_SPEED}~${BgmClip.MAX_SPEED}x)를 " +
                "벗어나요. 원본 길이 ${clip.sourceDurationMs}ms 기준으로 가능한 범위로 다시 알려주세요."
        }
        updateBgmClip(
            clipId = clipId,
            startMs = newStartMs.coerceAtLeast(0L),
            speedScale = newSpeed,
        )
        pushUndoState()
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
                mediaType = SeparationMediaType.AUDIO,
                numberOfSpeakers = 2,
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
                            // BGM 분리: 배경음 stem 만 제외하고 나머지(보컬/speaker) default 선택.
                            val defaults = absStems.associate { stem ->
                                stem.stemId to StemSelectionUi(
                                    stem.stemId,
                                    selected = stem.stemId != Stem.STEM_ID_BACKGROUND,
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
                    is SeparationStatus.Consumed -> updateSeparation {
                        it.copy(step = AudioSeparationStep.FAILED, errorMessage = ERROR_SEPARATION_CONSUMED)
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
     * "스크립트 생성 완료" 버튼(자막 패널) 클릭 시 review sheet 다시 열기.
     * subtitleReviewPending 이 true 일 때만 동작 — 그렇지 않으면 사용자가 이미 confirm 한 상태라 no-op.
     *
     * timeline 재진입 시 자동 sheet 복귀 (`maybeTriggerAutoPipelines` → `shouldShowPendingReview`)
     * 가 작동하지 않거나 사용자가 sheet 를 dismiss 한 뒤 다시 열어야 할 때의 explicit 진입점.
     *
     * pendingReviewTargetLangs 는 observeProject 가 csv 에서 hydrate 한 값을 그대로 둔다.
     */
    fun onReopenScriptReviewSheet() {
        val s = _uiState.value
        if (!s.subtitleReviewPending) return
        // pendingReviewTargetLangs 가 비어 있을 수 있음 (timeline 재진입 직후 in-memory copy 만 비어 있고
        // csv 는 영속화돼 있는 경우). 영속화된 csv 에서 다시 hydrate.
        viewModelScope.launch {
            val project = editProjectRepository.getProject(projectId)
            val targets = project?.pendingReviewTargetLangsCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            _uiState.value = _uiState.value.copy(
                showScriptReviewSheet = true,
                pendingReviewTargetLangs = targets,
            )
        }
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
            // observeProject 가 다시 emit 하면서 subtitleReviewPending = false 로 동기화되지만,
            // 즉시 UI 라벨이 "스크립트 생성 완료" 에서 "생성 시작" 으로 돌아가도록 explicit set.
            _uiState.value = _uiState.value.copy(subtitleReviewPending = false)
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
        // 더빙 모드 전환 시 자막 모드의 "원본" sentinel ("") 을 정리. 잔존 시 startEnabled 는 true 인데
        // 더빙 모드에선 "원본" chip 미노출 + onStartLocalization 가드가 silent return → 사용자 막힘.
        val current = _uiState.value.localizationLangs
        val cleaned = if (mode == "dub") current - "" else current
        _uiState.value = _uiState.value.copy(localizationMode = mode, localizationLangs = cleaned)
    }
    fun onToggleLocalizationLang(code: String) {
        val current = _uiState.value.localizationLangs
        val next = if (code in current) current - code else current + code
        _uiState.value = _uiState.value.copy(localizationLangs = next)
    }
    fun onStartLocalization() {
        val s = _uiState.value
        val source = s.segments.firstOrNull()?.sourceUri
        if (source.isNullOrBlank()) return
        val mode = s.localizationMode
        // 자막 모드의 "원본" chip = "" sentinel — 다른 lang 과 함께 다중 선택 가능.
        // 더빙 모드는 UI 에서 원본 chip 안 띄우므로 sentinel 도 안 들어옴.
        // Defense-in-depth — 이미 결과 있는 lang 은 UI 에서 chip disabled 지만 stale state 로 들어와도
        // 호출 단계에서 한번 더 거름 (중복 BFF 호출 방지).
        val rawSelected = s.localizationLangs.toList()
        val selected = rawSelected.filter { code ->
            when (mode) {
                "subtitle" -> {
                    if (code.isEmpty()) {
                        // 원본 chip — 이미 원본 자막이 확정돼 있으면 skip.
                        !s.hasOriginalSubtitleVariant
                    } else {
                        s.subtitleClips.none { it.languageCode == code }
                    }
                }
                "dub" -> {
                    val done = s.dubbedAudioPaths.containsKey(code) ||
                        s.dubbedVideoPaths.containsKey(code)
                    val running = s.autoDubStatusByLang[code] == AutoJobStatus.RUNNING
                    !done && !running
                }
                else -> true
            }
        }
        val translationLangs = selected.filter { it.isNotBlank() }
        val includesOriginalSubtitle = mode == "subtitle" && "" in selected
        // 자막 모드: 원본만 OR 번역 1개+ → 진행. 둘 다 없으면 abort.
        // 더빙 모드: 번역 1개+ → 진행.
        val isSubtitleOriginalOnly = mode == "subtitle" && includesOriginalSubtitle && translationLangs.isEmpty()
        if (translationLangs.isEmpty() && !isSubtitleOriginalOnly) return
        val langs = translationLangs
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
                        onRenderProgress = { p ->
                            setRenderProgress(p)
                        },
                    )
                    setRenderProgress(null)
                    if (r.isSuccess) {
                        reviewSheetGate = TriggerGate.FIRED
                        _uiState.value = _uiState.value.copy(
                            sttPreflightStatus = AutoJobStatus.IDLE,
                            showScriptReviewSheet = true,
                            subtitleReviewPending = true,
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
                        numberOfSpeakers = 1,
                        onRenderProgress = { p ->
                            setRenderProgress(p)
                        },
                        includeOriginalLanguage = includesOriginalSubtitle,
                    )
                    // render 진행 표시 reset — STT/번역 단계로 넘어갔거나 실패.
                    setRenderProgress(null)
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
                            numberOfSpeakers = 1,
                            onRenderProgress = { p ->
                                setRenderProgress(p)
                            },
                        )
                    }.onFailure { println("[Localization] dub failed lang=$lang err=${it.message}") }
                    // 첫 dub 성공 후 cache hit 으로 render skip 되는 langs 도 있음 — 매 lang 끝에 reset.
                    setRenderProgress(null)
                }
                else -> println("[Localization] unknown mode=$mode")
            }
        }
        // 호출 직후 panel 닫음 + 선택 chip 비움 — 사용자가 다시 열었을 때 stale selection 으로 이미
        // 시작된 lang 이 다시 선택돼 보이는 사고 방지. 결과 / 진행 상태는 이미 생성된 클립 (subtitleClips /
        // dubbedAudioPaths) 또는 autoDubStatusByLang 등으로 chip 표시.
        _uiState.value = s.copy(localizationOpen = false, localizationLangs = emptySet())
    }
    fun onSelectPreviewLang(code: String?) {
        _uiState.value = _uiState.value.copy(previewLangCode = code)
    }

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
            // 편집 영상이 필요한 경우 BFF 에 audio-only render 잡 1개 보내고 jobId 회수 — multipart
            // `file` 업로드 절약. 분리는 audio 만 필요하므로 AUDIO kind (5–10x 빠름).
            val editedRenderJobId = ensureLatestRender(
                projectId = projectId,
                kind = com.vibi.shared.domain.usecase.render.RenderKind.AUDIO,
                onProgress = { p ->
                    setRenderProgress(p)
                },
            ).getOrElse { err ->
                setRenderProgress(null)
                handleSeparationFailure(clientToken, err.message ?: ERROR_SEPARATION_GENERIC)
                return@launch
            }
            setRenderProgress(null)
            val startResult = startAudioSeparation(
                sourceUri = segment.sourceUri,
                mediaType = SeparationMediaType.VIDEO,
                numberOfSpeakers = sep.numberOfSpeakers,
                trimStartMs = effStart,
                trimEndMs = effEnd,
                editedRenderJobId = editedRenderJobId,
            )
            val jobId = startResult.getOrElse { err ->
                handleSeparationFailure(clientToken, err.message ?: ERROR_SEPARATION_GENERIC)
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
                    )
                )
            }
            separationGate = TriggerGate.FIRED
            pollSeparationFlow(clientToken, jobId)
        }
        separationJobs[clientToken] = job
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
                cleaned.copy(separationStatus = AutoJobStatus.FAILED, separationError = reason)
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
                        // 모든 stem default 선택 — 사용자가 directive 막대 탭으로 사후 편집 가능.
                        // EditProject 의 separationStatus=READY 중간 write 는 곧바로 clearSeparation 으로
                        // 덮이므로 생략 — commit 이 단일 write 로 처리.
                        val defaults = absStems.associate { stem ->
                            stem.stemId to StemSelectionUi(stem.stemId, selected = true, volume = 1.0f)
                        }
                        commitProcessingSeparationToDirective(clientToken, absStems, defaults)
                    }
                    is SeparationStatus.Failed ->
                        handleSeparationFailure(clientToken, status.progressReason ?: ERROR_SEPARATION_GENERIC)
                    is SeparationStatus.Consumed ->
                        handleSeparationFailure(clientToken, ERROR_SEPARATION_CONSUMED)
                }
            }
        } catch (e: Exception) {
            handleSeparationFailure(clientToken, e.message ?: ERROR_SEPARATION_GENERIC)
        }
    }

    /**
     * Processing entry → SeparationDirective 영속화. polling Ready 시 자동 호출.
     * entry 의 range / numberOfSpeakers / editingDirectiveId 그대로 사용. 기존 directive 의 좌표나
     * editingDirectiveId 가 일치하면 upsert (id 보존) — TimelineScreen 의 stemMixer group 끊김 방지.
     */
    private suspend fun commitProcessingSeparationToDirective(
        clientToken: String,
        stems: List<Stem>,
        selections: Map<String, StemSelectionUi>,
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
            val directiveEnd = when {
                entry.rangeEndMs != null -> entry.rangeEndMs
                isWholeVideo -> state.videoDurationMs.coerceAtLeast(1L)
                else -> directiveStart + (
                    if (segment.trimEndMs > 0L) segment.trimEndMs - segment.trimStartMs else segment.durationMs
                )
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
            mainUndoManagerForCurrent().clear()
            pushUndoState()
        } catch (e: Exception) {
            handleSeparationFailure(clientToken, e.message ?: ERROR_SEPARATION_GENERIC)
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
     * 모든 video segment 의 원본 audio mute toggle. directive range 진입/이탈 시점에 TimelineScreen 이
     * 호출 — range 안에서만 video mute, 밖에서는 원본 audio 들리도록.
     */
    fun muteVideoSegmentsForDirective(mute: Boolean) {
        val target = if (mute) 0f else 1f
        viewModelScope.launch {
            _uiState.value.segments
                .filter { it.type == SegmentType.VIDEO && it.volumeScale != target }
                .forEach { updateSegmentVolume(it.id, target) }
        }
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
                mainUndoManagerForCurrent().clear()
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
        const val MIN_IMAGE_DURATION_MS = 500L
        const val MAX_IMAGE_DURATION_MS = 30_000L
        const val MIN_FRAME_DIMENSION = 16
        const val MAX_FRAME_DIMENSION = 7680
        const val DEFAULT_OVERLAY_DURATION_MS = 3_000L
        private const val HTTP_NOT_FOUND = 404
        private const val ERROR_SEPARATION_GENERIC = "분리에 실패했습니다"
        private const val ERROR_SEPARATION_CONSUMED =
            "이 작업은 이미 합성에 사용되어 더 이상 사용할 수 없습니다"
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
