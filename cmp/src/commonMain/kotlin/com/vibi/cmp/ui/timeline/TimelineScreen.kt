package com.vibi.cmp.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.SpeakerPalette
import com.vibi.cmp.theme.VibiRadius
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.cmp.platform.RuntimeFlags
import com.vibi.cmp.platform.StemMixerSource
import com.vibi.cmp.platform.rememberMediaPickerLauncher
import com.vibi.cmp.ui.account.ConfettiOverlay
import com.vibi.cmp.ui.account.PAID_CREDITS_CTA_LABEL
import com.vibi.cmp.ui.account.PaidCreditsComingSoonNote
import com.vibi.cmp.platform.rememberStemMixer
import com.vibi.shared.domain.model.hasNonTrivialEdits
import com.vibi.shared.platform.fileExists
import com.vibi.shared.ui.timeline.AudioSeparationStep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.vibi.cmp.platform.VideoPlayer
import com.vibi.shared.ui.timeline.EditTarget
import com.vibi.shared.ui.timeline.SaveStatus
import com.vibi.shared.ui.timeline.ShareStatus
import com.vibi.shared.ui.timeline.hasBgm
import com.vibi.shared.ui.timeline.PreviewMode
import com.vibi.shared.ui.timeline.TimelineViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * stem mixer 동기화 key — playback position 매 tick 마다 LaunchedEffect 재발화 회피용.
 * Triple 을 쓰던 자리에서 [previewMode] 가 추가되며 명명형 데이터 클래스로 승격.
 */
private data class StemSyncKey(
    val directiveId: String?,
    val inRange: Boolean,
    val isPlaying: Boolean,
    val previewMode: PreviewMode,
)

/**
 * Timeline 화면 — 영상 업로드 · 세그먼트 편집 · BGM 삽입 · 음원분리 통합.
 *
 * shared `TimelineViewModel` 을 Koin parametersOf(projectId) 로 inject. 각 기능 sheet 는
 * 별도 Composable. 비디오 프리뷰는 `cmp.platform.VideoPlayer` expect/actual.
 */
@Composable
fun TimelineScreen(
    projectId: String,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val viewModel: TimelineViewModel = koinInject { parametersOf(projectId) }
    val state by viewModel.uiState.collectAsState()

    // 이름 변경 다이얼로그 상태 (순수 표시용 — 저장/렌더 동작 무관).
    //  - 프로젝트 제목: 헤더 제목 탭으로 open.
    //  - 음원·녹음: SoundDeck 카드 연필 탭으로 대상 clipId set.
    var renameProjectOpen by remember { mutableStateOf(false) }
    var renameBgmTargetId by remember { mutableStateOf<String?>(null) }

    // 음원분리 취소 흐름.
    //  - selectedProcessingToken: 진행 중 바를 탭해 취소 UI 를 띄운 대상.
    //  - cancelWarnToken: 경고 다이얼로그를 띄운 대상 (null = 닫힘).
    //  - "다시 보지 않기" 는 VM 의 skipSeparationCancelWarning (Settings 영속) 으로 관리.
    var selectedProcessingToken by remember { mutableStateOf<String?>(null) }
    var cancelWarnToken by remember { mutableStateOf<String?>(null) }

    // 영상이 모두 삭제된 빈 상태의 "+" → 갤러리에서 새 영상 선택 → 세그먼트로 추가(append 흐름 재사용).
    val addVideoLauncher = rememberMediaPickerLauncher { uri -> viewModel.onAppendVideoSegment(uri) }

    // 음원 삽입 / 즉시 녹음 통합 peek sheet — null 이면 닫힘. Entry sheet 의 두 카드가 진입 mode 결정.
    var audioInsertMode by remember { mutableStateOf<AudioInsertMode?>(null) }
    // 진입 액션 시트 (Record / Upload 두 카드) — IconLabelCard 트리거가 토글, AudioInsertSheet 와 같은 BottomCenter 위치에 렌더.
    var audioEntryOpen by remember { mutableStateOf(false) }

    // SoundDeck 의 분리 구간 펼침 상태 — UnifiedTimelineBar 파형의 화자별 색 표시와 같은 진실 공유.
    // 펼치면 화자 색, 닫히면 단일 highlight.
    var expandedSeparationIds by remember { mutableStateOf(emptySet<String>()) }

    // navigateBackHome 신호 시 InputScreen 복귀. 저장 완료로는 더 이상 emit 하지 않는다(저장 후
    // 에디터에 머물며 Export 버튼 체크로 알림) — 명시적 나가기 등 향후 신호용으로 collector 유지.
    LaunchedEffect(viewModel) {
        viewModel.navigateBackHome.collect { onSaved() }
    }

    // 분리 시작 시 잔액 부족이면 ViewModel 이 navigateToBuyCredits emit — UserMenu sheet 띄워
    // CreditPurchaseSheet 흐름으로 자연스럽게 분기. 충전 후 사용자가 sheet 닫으면 다시
    // AudioSeparationSheet 의 FAILED step 가 노출돼 재시도 가능 (사용자가 Start 다시 누름).
    var showUserMenuForCredits by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.navigateToBuyCredits.collect { showUserMenuForCredits = true }
    }

    // IAP 미오픈 기간 "I want this" 탭 → 수요 적재(VM) + 컨페티 보상. nonce 를 올려 재탭마다
    // ConfettiOverlay 를 새로 생성(애니메이션 재시작), 종료 시 visible=false 로 제거.
    var confettiVisible by remember { mutableStateOf(false) }
    var confettiNonce by remember { mutableStateOf(0) }
    val onWantPaidCredits = {
        viewModel.onWantPaidCredits()
        confettiNonce++
        confettiVisible = true
    }

    // ── 분리된 stem 동시 재생 mixer (Phase 2) ──
    // 첫 directive 의 selections 기준으로 stem 들을 ExoPlayer (Android) 다중 인스턴스에 로드하고,
    // 영상 재생/일시정지/seek 에 동기화. directive range 밖 위치에서는 mute 효과 (volume 0).
    // iOS 는 cinterop 한계로 no-op fallback (Swift bridge 도입 시 활성화).
    val stemMixer = rememberStemMixer()
    // 모든 directive 의 stems 를 한 번에 load — directive 별 group 으로 prepare. transition 시
    // setActiveGroup 만 변경, 다운로드 끊김 없음.
    val allDirectives = state.separationDirectives
    // localPath 가 key 에 포함돼야 캐시 완료 후 (URL→로컬 경로) load 가 재실행돼 로컬 파일로 전환됨.
    val directivesKey = remember(allDirectives) {
        allDirectives.joinToString("|") { d ->
            "${d.id}:${d.selections.joinToString(",") { "${it.stemId}=${it.localPath ?: it.audioUrl}" }}"
        }
    }
    LaunchedEffect(directivesKey) {
        val sources = allDirectives.flatMap { dir ->
            dir.selections.mapNotNull { sel ->
                // 영구 캐시된 로컬 파일이 있으면 우선 — 서버 연결이 끊겨도 재생. 없으면 원격 URL 스트리밍.
                val src = sel.localPath?.takeIf { it.isNotBlank() && fileExists(it) }
                    ?: sel.audioUrl?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                StemMixerSource(stemId = sel.stemId, audioUrl = src, groupId = dir.id)
            }
        }
        stemMixer.load(sources)
    }
    // 어느 directive 의 stem 도 audioUrl 있으면 정렬 우선. playbackPosition 이 들어간 것 우선.
    val activeDirective = allDirectives.firstOrNull { d ->
        state.playbackPositionMs in d.rangeStartMs..d.rangeEndMs &&
            d.selections.any { !it.audioUrl.isNullOrBlank() }
    }
    // selection / volume 변경 시 setVolume 만. load 와 분리해 끊김 방지.
    // previewMode == ORIGINAL 이면 모든 stem 강제 mute — 영상 원본 audio 만 들리도록.
    // VOICE_ALL ("모든 화자") 은 SoundDeck UI 에서 숨겼으므로 mixer 에서도 강제 mute — 기존 프로젝트가
    // selected=true 로 저장된 경우 화자별 stem 과 중복 재생되는 것 방지 (안전망).
    LaunchedEffect(activeDirective?.id, activeDirective?.selections, state.previewMode) {
        val dir = activeDirective ?: return@LaunchedEffect
        val forceMute = state.previewMode == com.vibi.shared.ui.timeline.PreviewMode.ORIGINAL
        dir.selections.forEach { sel ->
            val isVoiceAll = sel.stemId == com.vibi.shared.domain.model.Stem.STEM_ID_VOICE_ALL
            val v = when {
                forceMute -> 0f
                isVoiceAll -> 0f
                sel.selected -> sel.volume
                else -> 0f
            }
            stemMixer.setVolume(sel.stemId, v)
        }
    }
    // playback position 진입/이탈 시 active group + video mute 동시 toggle.
    // playbackPositionMs (200ms tick) 직접 의존하면 매 tick 마다 LaunchedEffect 가 cancel/restart 돼
    // coroutine churn. derivedStateOf 로 (directive id, inRange, isPlaying) Triple 만 추출해 상태
    // 변화 순간만 trigger.
    val stemSyncKey by remember(activeDirective, state.previewMode) {
        derivedStateOf {
            val dir = activeDirective
            // previewMode 도 키에 포함 — ORIGINAL ↔ MIX 토글이 즉시 반영되도록 4-tuple 로 확장.
            val mode = state.previewMode
            if (dir == null) StemSyncKey(null, false, state.isPlaying, mode)
            else StemSyncKey(
                directiveId = dir.id,
                inRange = state.playbackPositionMs in dir.rangeStartMs..dir.rangeEndMs,
                isPlaying = state.isPlaying,
                previewMode = mode,
            )
        }
    }
    LaunchedEffect(stemSyncKey) {
        val key = stemSyncKey
        // ORIGINAL 모드 — directive 무관하게 mixer pause + video 항상 unmute.
        if (key.previewMode == com.vibi.shared.ui.timeline.PreviewMode.ORIGINAL) {
            stemMixer.pause()
            stemMixer.setActiveGroup(null)
            viewModel.muteVideoSegmentsForDirective(false)
            return@LaunchedEffect
        }
        if (key.directiveId == null) {
            stemMixer.pause()
            stemMixer.setActiveGroup(null)
            // directive range 밖 (또는 directive 없음) → video unmute.
            viewModel.muteVideoSegmentsForDirective(false)
            return@LaunchedEffect
        }
        val dir = activeDirective ?: return@LaunchedEffect
        when {
            !key.isPlaying -> stemMixer.pause()
            key.inRange -> {
                // 진입 — group 전환 + offset seek + play + video mute.
                // sourceOffsetMs = directive 가 split 된 경우 stem audio 의 중간부터 재생하기 위한 누적 offset.
                // 신규 directive 는 0 이라 기존 동작 동일. split piece 는 0 보다 큰 값.
                stemMixer.setActiveGroup(dir.id)
                val offset = ((state.playbackPositionMs - dir.rangeStartMs) + dir.sourceOffsetMs)
                    .coerceAtLeast(0L)
                stemMixer.seekTo(offset)
                stemMixer.play()
                viewModel.muteVideoSegmentsForDirective(true)
            }
            else -> {
                stemMixer.pause()
                viewModel.muteVideoSegmentsForDirective(false)
            }
        }
    }

    // directive 안에서 scrub/playback 진행 시 stem mixer 도 따라가야 함. stemSyncKey 는
    // coroutine churn 회피로 playbackPositionMs 를 의도적으로 제외해 inRange 변화 시점만
    // reaction — 그 결과 directive 안에서 scrub 해도 mixer 가 옛 위치 그대로 재생되는 사고.
    // 본 effect 는 playbackPositionMs 를 키에 포함해 매 변화마다 mixer.seekTo 호출.
    // Mixer 내부 drift 가드(50ms) 가 polling 자체 emit 으로 인한 무의미 set 을 차단해
    // audio glitch 없이 사용자 scrub 만 정확히 반영.
    LaunchedEffect(activeDirective?.id, state.playbackPositionMs, state.previewMode) {
        val dir = activeDirective ?: return@LaunchedEffect
        if (state.previewMode == com.vibi.shared.ui.timeline.PreviewMode.ORIGINAL) return@LaunchedEffect
        val inRange = state.playbackPositionMs in dir.rangeStartMs..dir.rangeEndMs
        if (!inRange) return@LaunchedEffect
        val offset = ((state.playbackPositionMs - dir.rangeStartMs) + dir.sourceOffsetMs)
            .coerceAtLeast(0L)
        stemMixer.seekTo(offset)
    }

    // video segment 의 speedScale 을 stem mixer 에 그대로 sync — 영상이 1.5x 면 stem 도 1.5x.
    // 미수정 시 영상만 빨라지고 stem 은 1.0x 로 흘러 즉시 desync. derivedStateOf 로 playbackPositionMs
    // tick 마다의 재계산은 허용하되 결과(rate) 변화 시점에만 effect 발화 — 33ms polling churn 회피.
    val activeStemRate by remember(state.segments) {
        derivedStateOf {
            val pos = state.playbackPositionMs
            var accum = 0L
            var rate = 1f
            for (seg in state.segments) {
                if (seg.type != com.vibi.shared.domain.model.SegmentType.VIDEO) continue
                val end = accum + seg.effectiveDurationMs
                if (pos < end) {
                    rate = if (seg.speedScale > 0f) seg.speedScale else 1f
                    break
                }
                accum = end
            }
            rate
        }
    }
    LaunchedEffect(activeStemRate) {
        stemMixer.setRate(activeStemRate)
    }

    // EditAudio (편집·음원) 단계는 자동 segment edit 모드 진입을 하지 않는다 —
    // 사용자가 BGM 작업 + 영상 segment 작업을 자유롭게 섞을 수 있도록. segment 편집은
    // 영상 위 우상단 연필 버튼(onEnterSegmentEditMode) 명시 진입.

    // 구간 모드 진입 + 선택 있는 경우만 재생 위치 정렬 (zero-width 선택 = 자유 재생 허용).
    LaunchedEffect(state.isRangeSelecting) {
        if (state.isRangeSelecting && state.pendingRangeEndMs > state.pendingRangeStartMs) {
            val pos = state.playbackPositionMs
            if (pos < state.pendingRangeStartMs || pos > state.pendingRangeEndMs) {
                viewModel.onUpdatePlaybackPosition(state.pendingRangeStartMs)
            }
        }
    }
    // 재생 중 구간 끝 도달 시 시작점으로 자동 seek (loop). 단 선택이 있을 때만 — 빈 선택 상태에선
    // loop 없이 영상 끝까지 자유 재생.
    //
    // LaunchedEffect 의 block 안 `state` 는 launch 시점 캡처라 stale — playbackPositionMs / pendingRangeEnd
    // 가 갱신되어도 while 조건이 옛 값을 보고 동작 안 함 (음원만 계속 진행 / 재생바만 멈추는 결함).
    // viewModel.uiState.value 로 매 tick fresh fetch.
    LaunchedEffect(state.isRangeSelecting, state.isPlaying) {
        while (true) {
            val cur = viewModel.uiState.value
            if (!cur.isRangeSelecting || !cur.isPlaying) break
            if (cur.pendingRangeEndMs > cur.pendingRangeStartMs &&
                cur.playbackPositionMs >= cur.pendingRangeEndMs
            ) {
                viewModel.onUpdatePlaybackPosition(cur.pendingRangeStartMs)
            }
            delay(100)
        }
    }

    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current

    // 영상 audio peaks — UnifiedTimelineBar 의 재생바 배경에 표시. 모듈 캐시(videoPeaksCache) 가
    // sourceUri 단위로 1회 추출 결과 보관 → 화면 재진입 시 즉시. Android 는 extractAudioPeaks
    // stub 이라 빈 list → UnifiedTimelineBar 가 기존 회색 strip 으로 fallback.
    var videoPeaks by remember(state.videoUri) {
        mutableStateOf(videoPeaksCache[state.videoUri] ?: emptyList())
    }
    LaunchedEffect(state.videoUri) {
        if (state.videoUri.isNotEmpty() && videoPeaks.isEmpty()) {
            val extracted = com.vibi.cmp.platform.extractAudioPeaks(state.videoUri, samples = 480)
            if (extracted.isNotEmpty()) {
                videoPeaksCache[state.videoUri] = extracted
                videoPeaks = extracted
            }
        }
    }

    // 각 directive 의 stem audio peaks — 분리된 화자/배경음 stem 별로 자체 파형을 추출해 timeline
    // directive 영역에 그대로 반영. 원본 video peaks 만 쓰면 모든 directive 가 동일한 shape 으로
    // 보임 (volume scalar 차이만). stem peaks 를 사용하면 voice/background 구간이 시각적으로 달라짐.
    val stemPeaks = remember {
        androidx.compose.runtime.mutableStateMapOf<String, List<Float>>().apply {
            putAll(stemPeaksCacheTimeline)
        }
    }
    val activeStemUrls = remember(state.separationDirectives) {
        state.separationDirectives
            .flatMap { d -> d.selections }
            .mapNotNull { it.audioUrl?.takeIf { url -> url.isNotBlank() } }
            .distinct()
    }
    LaunchedEffect(activeStemUrls) {
        // 각 stem 추출은 독립적이라 병렬 실행 — sequential 이면 N stems × per-URL latency 누적.
        coroutineScope {
            for (url in activeStemUrls) {
                if (!stemPeaks[url].isNullOrEmpty()) continue
                launch {
                    val extracted = com.vibi.cmp.platform.extractAudioPeaks(url, samples = 480)
                    if (extracted.isNotEmpty()) {
                        stemPeaksCacheTimeline[url] = extracted
                        stemPeaks[url] = extracted
                    }
                }
            }
        }
    }

    // 삽입된 BGM 클립의 파형 — 타임라인 BGM 레인 블록 내부에 mini 파형으로 렌더. BgmActionSheet 의
    // bgmPeaksCache 와 같은 모듈 캐시 공유라 sheet 에서 이미 본 적 있는 클립은 즉시 표시. 신규 클립은
    // 백그라운드 추출. mutableStateMapOf 로 emit 해 recomposition 트리거.
    val bgmPeaks = remember {
        androidx.compose.runtime.mutableStateMapOf<String, List<Float>>().apply {
            putAll(bgmPeaksCache)
        }
    }
    val bgmSourceUris = remember(state.bgmClips) {
        state.bgmClips.map { it.sourceUri }.distinct()
    }
    LaunchedEffect(bgmSourceUris) {
        coroutineScope {
            for (uri in bgmSourceUris) {
                if (uri.isBlank()) continue
                if (!bgmPeaks[uri].isNullOrEmpty()) continue
                launch {
                    // samples=160 — 블록 폭이 화면 폭의 일부라 240 까지 안 가도 충분. 추출 시간/메모리 절약.
                    val extracted = com.vibi.cmp.platform.extractAudioPeaks(uri, samples = 160)
                    if (extracted.isNotEmpty()) {
                        bgmPeaksCache[uri] = extracted
                        bgmPeaks[uri] = extracted
                    }
                }
            }
        }
    }

    // remember — 매 recomposition(재생 중 33ms tick 포함)마다 filter/map 으로 새 리스트를 할당하지
    // 않도록. 의존 state 가 바뀔 때만 재계산.
    val videoSegs = remember(state.segments) {
        state.segments.filter {
            it.type == com.vibi.shared.domain.model.SegmentType.VIDEO
        }
    }
    val playerItems: List<com.vibi.cmp.platform.VideoPlayerItem> = remember(
        videoSegs, state.videoUri, state.runtimeVideoMutedForDirective,
    ) {
        if (state.videoUri.isEmpty()) {
            emptyList()
        } else {
            videoSegs.map { seg ->
                com.vibi.cmp.platform.VideoPlayerItem(
                    sourceUri = seg.sourceUri,
                    trimStartMs = seg.trimStartMs,
                    trimEndMs = seg.effectiveTrimEndMs,
                    speedScale = seg.speedScale,
                    volumeScale = if (state.runtimeVideoMutedForDirective) 0f else seg.volumeScale,
                )
            }
        }
    }
    // BGM 재생 sync — 인라인/풀스크린 무관하게 1회만. 두 군데서 호출하면 audio engine 충돌.
    if (state.videoUri.isNotEmpty()) {
        com.vibi.cmp.platform.BgmPlaybackSync(
            clips = if (state.isSegmentEditMode) emptyList() else state.bgmClips,
            isPlaying = state.isPlaying,
            currentMs = state.playbackPositionMs,
        )
    }
    // 전체화면 토글 — 인라인 프리뷰는 fullscreen 시 검은 박스로 비워두고 Dialog 가 단일 player 소유.
    var fullscreenOpen by remember { mutableStateOf(false) }
    // 가로 회전 허용 — Android 는 force-rotate, iOS 는 plist + AppDelegate flag flip (수동 회전).
    com.vibi.cmp.platform.LockLandscape(enabled = fullscreenOpen)

    Box(modifier = Modifier.fillMaxSize().background(tokens.backgroundPrimary)) {
    val bottomTarget = state.bottomActionTarget()
    // 상단(헤더~타임라인)은 고정, 사운드덱만 스크롤 — 메인 Column 자체는 스크롤 안 함. 편집 액션바는
    // 컬럼 in-flow 최하단(아래)에 둬 나타날 때 덱을 밀어올린다(overlay 로 가리지 않음) — reserve 불필요.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(VibiSpacing.base),
        verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
    ) {
        // 헤더: 뒤로 + 단계 타이틀 + 공유/저장. 백그라운드 잡 진행 중이면 저장 disabled.
        // 저장 버튼이 자체적으로 모든 variant 렌더 → 갤러리 저장 → EditProject 삭제 → InputScreen 복귀를
        // 호출하므로 별도 ExportScreen 으로 이동하는 흐름은 폐기됐다.
        val saveAnyJobRunning = state.audioSeparation?.step == AudioSeparationStep.PROCESSING ||
            state.processingSeparations.isNotEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            // 뒤로가기 버튼·제목·Export 버튼 높이가 달라(특히 Export 축소 후) Top 정렬이면 어긋나 보임
            // → 세로 중앙 정렬로 통일. 뒤로가기 버튼도 Export 와 같은 xl 높이로 맞춤.
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(VibiSpacing.xl)
                    .clip(CircleShape)
                    .background(tokens.chipBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = tokens.onBackgroundPrimary, style = typo.titleLg)
            }
            // 헤더 제목 = 프로젝트 이름. 탭하면 이름 변경 다이얼로그. 미지정 시 "Untitled".
            // 표시용일 뿐 — 저장/렌더/내보내기 파일명과 무관.
            Text(
                text = state.projectTitle?.takeIf { it.isNotBlank() } ?: "Untitled",
                // displaySm 은 EB Garamond (serif display) — 화면 다른 텍스트가 모두 Inter (body)
                // 라 혼자 튀어보임. body family 의 titleLg 기반에 displaySm 크기(20sp) + Bold 유지.
                style = typo.titleLg.copy(fontSize = 20.sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = tokens.onBackgroundPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { renameProjectOpen = true },
            )
            val saving = state.saveStatus is SaveStatus.RUNNING
            val sharing = state.shareStatus is ShareStatus.RUNNING
            val isSaved = state.saveStatus is SaveStatus.DONE
            val isShared = state.shareStatus is ShareStatus.DONE
            var exportSheetOpen by remember { mutableStateOf(false) }
            // 내보내기 진입점 — accent 배경 CTA. 분리 진행 중이거나 저장·공유 중엔 버튼 비활성.
            // 진행 표시는 전체화면 원형 링 오버레이(사진 앱 스타일)가 담당. 현재 편집 상태가 이미
            // 저장/공유됐으면 라벨 옆 체크 — 편집으로 출력이 바뀌면 VM 이 상태를 IDLE 로 돌려 체크가 사라짐.
            Button(
                enabled = !sharing && !saving && !saveAnyJobRunning && state.segments.isNotEmpty(),
                onClick = { exportSheetOpen = true },
                shape = VibiShape.lg,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.accent,
                    contentColor = tokens.backgroundPrimary,
                    disabledContainerColor = tokens.chipBg,
                    disabledContentColor = tokens.chipContentDisabled,
                ),
                elevation = null,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VibiSpacing.xs, vertical = 0.dp),
                modifier = Modifier.height(VibiSpacing.xl),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Export", style = typo.bodySm)
                    if (isSaved || isShared) {
                        Spacer(Modifier.width(VibiSpacing.xxs))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "내보내기 완료됨",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (exportSheetOpen) {
                ExportOptionsSheet(
                    saved = isSaved,
                    shared = isShared,
                    onSave = {
                        exportSheetOpen = false
                        viewModel.onSaveAllVariants()
                    },
                    onShare = {
                        exportSheetOpen = false
                        viewModel.onShareExport()
                    },
                    onDismiss = { exportSheetOpen = false },
                )
            }
        }


        // 비디오 프리뷰 (inline)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(VibiShape.lg)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (state.videoUri.isNotEmpty() && !fullscreenOpen) {
                VideoPlayer(
                    items = playerItems,
                    isPlaying = state.isPlaying,
                    seekToMs = state.playbackPositionMs.takeIf { state.videoDurationMs > 0 },
                    onPositionChanged = { ms -> viewModel.onUpdatePlaybackPosition(ms) },
                    onEnded = {
                        if (state.isPlaying) viewModel.onTogglePlayback()
                        viewModel.onUpdatePlaybackPosition(0L)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (state.videoUri.isEmpty()) {
                // 영상이 삭제된 빈 상태 — "No video" 텍스트 대신 "+" 버튼. 탭하면 갤러리에서 새 영상 선택.
                // (영상 Box bg 가 항상 검정이므로 라이트 모드에서도 흰색 유지.)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                    modifier = Modifier.clickable { addVideoLauncher() },
                ) {
                    Box(
                        modifier = Modifier
                            .size(VibiSpacing.xxl)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add video",
                            tint = Color.White,
                        )
                    }
                    Text("Add video", color = Color.White, style = typo.bodySm)
                }
            }

        }

        // Transport row — 좌: 전체화면 / 중앙: 재생 정지 / 우: undo·redo. 세 영역을 Box 의 align 으로
        // 절대 배치해 중앙 버튼이 화면 너비와 무관하게 정확히 가운데 위치. 버튼 크기는 touchMin(44dp)
        // 균일 — iOS HIG 44pt / Material 3 48dp 기준 충족.
        // undo/redo 는 영상이 없을 때(영상 전체 삭제 후)에도 노출 — 삭제를 되돌릴 수 있어야 하므로
        // 행 자체는 항상 렌더하고, 전체화면·재생만 영상이 있을 때로 가둔다.
        run {
            val btnSize = VibiSpacing.touchMin
            val iconSize = 20.dp
            Box(modifier = Modifier.fillMaxWidth().height(btnSize)) {
                if (state.videoUri.isNotEmpty()) {
                    // Left — 전체화면 + 그 오른쪽에 '][' 구간 분리(재생헤드 컷).
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(btnSize)
                                .clip(CircleShape)
                                .background(tokens.chipBg)
                                .clickable { fullscreenOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = tokens.onBackgroundPrimary,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                        // '][' 구간 분리 — 재생헤드가 놓인 영상 세그먼트를 그 지점에서 둘로 나눈다.
                        Box(
                            modifier = Modifier
                                .size(btnSize)
                                .clip(CircleShape)
                                .background(tokens.chipBg)
                                .clickable { viewModel.onSplitAtPlayhead() },
                            contentAlignment = Alignment.Center
                        ) {
                            // SplitIcon 은 Canvas 가 박스를 꽉 채워(가로 76%·세로 64%) 내부 패딩이 있는
                            // Material Fullscreen 글리프보다 커 보임 → 0.85배로 줄여 시각 크기를 맞춤.
                            // 박스(btnSize 터치영역)는 그대로라 정렬·간격은 불변.
                            SplitIcon(
                                tint = tokens.onBackgroundPrimary,
                                modifier = Modifier.size(iconSize * 0.85f),
                            )
                        }
                    }
                    // Center — 재생/정지 + 현재s/전체s 라벨. Row 로 묶어 align(Center) 하면 (버튼+텍스트)
                    // 두 자식의 합산 폭이 화면 중앙에 위치 — 버튼만 정중앙에 두고 텍스트를 오른쪽에 띄우는
                    // 절대 offset 보다 자연스럽고 너비 변동(예: 999s vs 9s) 에 안전.
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(btnSize)
                                .clip(CircleShape)
                                .background(tokens.onBackgroundPrimary)
                                .clickable { viewModel.onTogglePlayback() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = tokens.backgroundPrimary,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                        Text(
                            // ms→s 는 올림 — 4.87s 영상이 라벨에서 "4s" 로 잘려 보이지 않도록.
                            // (ms + 999) / 1000 = ceil(ms/1000). ms ≥ 0 보장 컨텍스트.
                            text = "${(state.playbackPositionMs + 999) / 1000}s/${(state.videoDurationMs + 999) / 1000}s",
                            style = typo.bodySm,
                            color = tokens.mutedText,
                        )
                    }
                }
                // Right — undo / redo. 영상 유무와 무관하게 항상 노출.
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(if (state.canUndo) tokens.chipBg else tokens.chipBgDisabled)
                            .clickable(enabled = state.canUndo) { viewModel.onUndo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo) tokens.onBackgroundPrimary else tokens.chipContentDisabled,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(btnSize)
                            .clip(CircleShape)
                            .background(if (state.canRedo) tokens.chipBg else tokens.chipBgDisabled)
                            .clickable(enabled = state.canRedo) { viewModel.onRedo() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo) tokens.onBackgroundPrimary else tokens.chipContentDisabled,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }

        // BGM 트랙 display layout — 선택 없으면 한 줄 collapsed (모두 lane 0), 선택 시 자동 pack.
        // bgmClips 또는 selectedBgmClipId 가 바뀔 때만 재계산.
        val bgmDisplayLayout = remember(state.bgmClips, state.selectedBgmClipId) {
            computeBgmDisplayLayout(state.bgmClips, state.selectedBgmClipId)
        }

        // 통합 재생 바.
        //  - 메인 (range 모드 비활성): 재생 Slider + 그 아래 directive 회색 막대 (탭 → 편집 sheet).
        //  - range 모드: **단일 라인** = Box 안에 RangeSlider + directive 회색 overlay 를 layered.
        //    overlay 는 시각만 (pointerInput 없음) — 슬라이더 핸들이 directive 경계 근처에서도 자유로이
        //    드래그 가능. 탭 처리는 Box parent 의 detectTapGestures 가 위치 기반으로 분기:
        //    directive 위 탭 → 편집 sheet, free interval 위 탭 → 그 구간으로 pendingRange 점프.
        if (state.videoDurationMs > 0) {
            val sortedDirectives = state.separationDirectives.sortedBy { it.rangeStartMs }
            // range 정보 없는 entry (BGM/whole-video) 는 영상 전체로 fallback.
            val processingOverlays = remember(state.processingSeparations, state.segments, state.videoDurationMs) {
                state.processingSeparations
                    .filter { it.segmentId.isNotBlank() }
                    .map { p ->
                        // 분리는 격리된 세그먼트(segmentId)에 앵커 — overlay 를 그 세그먼트의 *현재* timeline
                        // 위치로 그려 재정렬로 세그먼트가 이동해도 반투명 fill 이 함께 움직인다. 세그먼트가
                        // 없으면(이례적) 캐시 range, 그것도 없으면 영상 전체로 폴백.
                        val seg = state.segments.firstOrNull { s -> s.id == p.segmentId }
                        val (start, end) = if (seg != null) {
                            val s = state.segments.filter { it.order < seg.order }
                                .sumOf { it.effectiveDurationMs }
                            s to (s + seg.effectiveDurationMs)
                        } else {
                            val s = p.rangeStartMs
                            val e = p.rangeEndMs
                            if (s != null && e != null && e > s) s to e else 0L to state.videoDurationMs
                        }
                        ProcessingSeparationOverlay(start, end, p.progress, p.clientToken)
                    }
            }
            // 배경음 제거(음원분리) 진행 중인 BGM 클립 id — 해당 클립은 timeline 에서 선택/드래그/트림 잠금.
            val separatingBgmClipIds = remember(state.bgmBackgroundRemovalProgress) {
                state.bgmBackgroundRemovalProgress
                    .filterValues { it is com.vibi.shared.ui.timeline.BgmRemovalProgress.Processing }
                    .keys
            }
            // 단일 통합 타임라인 바 — 재생/구간선택/segment·directive + BGM lane 까지 한 컴포넌트.
            UnifiedTimelineBar(
                segments = state.segments,
                directives = sortedDirectives,
                showSegments = state.isSegmentEditMode,
                showDirectives = !state.isSegmentEditMode,
                showRange = state.isRangeSelecting,
                totalMs = state.videoDurationMs,
                playbackPositionMs = state.playbackPositionMs,
                rangeStartMs = state.pendingRangeStartMs,
                rangeEndMs = state.pendingRangeEndMs,
                selectedSegmentId = state.selectedSegmentId,
                accent = tokens.accent,
                markerColor = tokens.onBackgroundPrimary,
                trackColor = tokens.timelineBarTrack,
                segmentColor = tokens.timelineBarSegment,
                segmentEditedColor = tokens.timelineBarSegmentEdited,
                directiveColor = tokens.timelineBarDirective,
                videoPeaks = videoPeaks,
                stemPeaksByUrl = stemPeaks,
                primarySourceUri = state.videoUri,
                primarySourceDurationMs = state.segments.firstOrNull { it.sourceUri == state.videoUri }
                    ?.durationMs ?: state.videoDurationMs,
                processingSeparations = processingOverlays,
                // 탭 지점 ms 를 함께 넘겨, 영상 다듬기 모드에서도 그 점이 속한 free 구간(음원분리 진행 중
                // 제외)으로 선택을 좁힌다 — 분리중 구간 위 탭은 무동작. 완료된 directive 는 free 에 포함돼
                // 인접 영상과 함께 선택. 범위 선택 흐름과 동일 규칙(freeIntervalsInSegment) 공유.
                onSegmentTap = { segId, tapMs -> viewModel.onSelectSegmentInEdit(segId, tapMs) },
                onMoveSegment = viewModel::onMoveSegment,
                onWaveformTapInNeutral = { tapMs ->
                    val segId = state.segments.firstOrNull {
                        it.type == com.vibi.shared.domain.model.SegmentType.VIDEO
                    }?.id
                    // 진입(첫 클릭)도 탭 지점 free 구간으로 스냅 — 분리중 구간 제외가 첫 클릭부터 적용되게.
                    if (segId != null) viewModel.onEnterSegmentEditMode(segId, tapMs = tapMs)
                },
                // directive 탭 시 AudioSeparationSheet 띄우지 않음 — 편집은 SoundDeck 의 stem 카드에서 처리.
                onDirectiveTap = {},
                // 진행 중 음원분리 바 탭 → 취소 UI 노출 대상 지정.
                // 같은 진행 구간 재탭 → 토글로 닫힘(기존 "Keep processing" 효과). 다른 구간 탭 → 그쪽으로 전환.
                onProcessingTap = { token ->
                    selectedProcessingToken = if (selectedProcessingToken == token) null else token
                },
                onScrub = { viewModel.onUpdatePlaybackPosition(it) },
                onRangeStartChange = { viewModel.onSetPendingRangeStart(it) },
                onRangeEndChange = { viewModel.onSetPendingRangeEnd(it) },
                onTranslateRange = { viewModel.onTranslateRange(it) },
                onFreeIntervalTap = { s, e -> viewModel.onSelectVideoRange(s, e) },
                onRangeTapToggle = { viewModel.onClearRangeSelection() },
                bgmClips = state.bgmClips,
                bgmLaneByClipId = bgmDisplayLayout.laneByClipId,
                bgmDisplayLaneCount = bgmDisplayLayout.laneCount,
                selectedBgmClipId = state.selectedBgmClipId,
                separatingBgmClipIds = separatingBgmClipIds,
                // BGM 블록 탭은 항상 허용 — 모드별로 콜백이 분기됨 (segment edit 면 range 스냅, 그 외엔
                // 단순 selection → trim 핸들 노출). 단, 분리 진행 중인 클립은 위 set 으로 per-clip 잠금.
                bgmTapEnabled = true,
                // segment edit (영상 다듬기) 모드에선 BGM 위치/lane drag + lane 수 조절 pill 모두 잠금.
                // 영상 편집 중 BGM 이 같이 따라 움직이면 사용자 의도와 어긋나는 사고가 잦아, 다듬기
                // 활성 동안엔 BGM 트랙은 read-only (탭으로 BGM range 편집 진입은 그대로 허용).
                bgmDragEnabled = !state.isSegmentEditMode,
                onBgmSelectClip = { clipId ->
                    // BGM/녹음 클립 탭 → 영상과 동일한 구간편집 모드 진입(트림 대체). editTargets=Bgm 이 되어
                    // bgmRangeMode=true → 블록 트림 핸들 대신 range 핸들 노출. 영상 다듬기 중 탭이면 영상 모드를
                    // 빠져나와 BGM 으로 작업 전환. 같은 클립 재탭은 토글 종료(onEnterBgmRangeEditMode 내부).
                    viewModel.onEnterBgmRangeEditMode(clipId)
                },
                onBgmUpdateStart = viewModel::onUpdateBgmStartMs,
                onBgmUpdateTrim = viewModel::onUpdateBgmTrim,
                bgmPeaksByUri = bgmPeaks,
                // segment edit 모드에서도 BGM 표시 — range-edit (volume/speed/duplicate/delete) 가
                // applyBgmRange* 헬퍼로 BGM 까지 적용하므로 사용자가 lane 을 보면서 편집 가능.
                // 음원분리 흐름 전체에서는 BGM 레인 숨김 — 영상 트랙만 노출해 화자/배경음 stem 선택에 집중.
                //   - 구간 선택 단계: isRangeSelecting && !isSegmentEditMode (음원분리 IconLabelCard
                //     → onEnterRangeMode 진입). 단, **BGM 구간편집**(editTargets=Bgm) 은 같은 플래그
                //     조합이지만 편집 대상 클립을 봐야 하므로 레인 유지 — hasBgm() 으로 구분.
                //   - sheet/processing 단계: showAudioSeparationSheet (BGM 분리 path 는 range mode
                //     없이 곧장 sheet 만 열리므로 별도 가드 필요).
                showBgm = !state.showAudioSeparationSheet &&
                    !(state.isRangeSelecting && !state.isSegmentEditMode && !state.editTargets.hasBgm()),
                // BGM 타깃 모드 — editTargets 에 Bgm 포함 시 영상 strip 의 range overlay 숨기고 BGM lane
                // 에 동일 디자인의 range fill 노출. 영상/BGM 동시 선택 막아 사용자 의도가 분명한 트랙에만 apply.
                bgmRangeMode = state.editTargets.hasBgm(),
            )
            if (state.isRangeSelecting) {
                Text(
                    "Range ${state.pendingRangeStartMs / 1000}s – ${state.pendingRangeEndMs / 1000}s · Time ${state.playbackPositionMs / 1000}s",
                    style = typo.bodySm,
                    color = tokens.accent
                )
                // 음원분리 진입(구간 선택 → "Separate this range")은 제거됨 — 음원분리는 영상 import
                // 시점에 자동으로 전체 분리가 돌고, 영상 편집 화면에서는 '][' 구간 분리(세그먼트 컷)만 제공.
                // SegmentEditActionPanel 은 음원분리/음원삽입 행 아래로 이동 — 사용자 요청.
            }

            // 진행 중 음원분리 취소 UI — 진행 바 탭으로 selectedProcessingToken 지정 시 노출.
            // 로딩바(LinearProgress) 바로 옆에 취소 버튼. 잡이 끝나면 LaunchedEffect 가 선택 해제.
            val selectedProcessing = selectedProcessingToken?.let { tok ->
                state.processingSeparations.firstOrNull { it.clientToken == tok }
            }
            LaunchedEffect(state.processingSeparations, selectedProcessingToken, cancelWarnToken) {
                val active = state.processingSeparations.mapTo(HashSet()) { it.clientToken }
                selectedProcessingToken?.let { if (it !in active) selectedProcessingToken = null }
                cancelWarnToken?.let { if (it !in active) cancelWarnToken = null }
            }
            if (selectedProcessing != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                    ) {
                        Text("Separating audio…", style = typo.bodySm, color = tokens.onBackgroundPrimary)
                        LinearProgressIndicator(
                            progress = { (selectedProcessing.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f),
                            color = tokens.accent,
                            trackColor = tokens.chipBg,
                        )
                        Text("${selectedProcessing.progress}%", style = typo.bodySm, color = tokens.mutedText)
                    }
                    // 닫기(keep processing)는 진행 바를 한 번 더 탭 → onProcessingTap 토글. 버튼은 취소만, 우측 정렬.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = {
                                val token = selectedProcessing.clientToken
                                if (viewModel.skipSeparationCancelWarning) {
                                    viewModel.onCancelProcessingSeparation(token)
                                    selectedProcessingToken = null
                                } else {
                                    cancelWarnToken = token
                                }
                            },
                        ) { Text("Cancel separation") }
                    }
                }
            }
        }

        // 사운드덱 영역 — 여기부터 스크롤. weight(1f) 로 상단 고정 영역(헤더~타임라인) 아래 남은
        // 세로를 차지하고, 내부 verticalScroll 로 덱이 길어지면 스크롤한다.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
        ) {
        // 진입점 버튼 (음원 삽입). 음원 분리 진입은 제거 — import 시 전체 자동 분리로 대체.
        if (!state.isRangeSelecting || state.isSegmentEditMode) {
            run {
                // Audio 섹션 헤더 + 음원 추가 버튼(우측 상단) — 타임라인 바로 아래, 사운드덱 공간 상단.
                // 버튼은 soundDeckEnabled 와 무관하게 항상 노출. 탭 시 AudioInsertEntrySheet(Record/Upload).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Audio", style = typo.titleSm, color = tokens.onBackgroundPrimary)
                    if (state.separationDirectives.isNotEmpty()) {
                        Spacer(Modifier.width(VibiSpacing.xs))
                        Text(
                            "Tap a range to adjust the sounds",
                            style = typo.bodySm,
                            color = tokens.mutedText,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // 음원추가 버튼 임시 숨김 — 나중에 복구 예정.
                    // AddAudioButton(
                    //     enabled = !state.isAddingBgm,
                    //     onClick = { audioEntryOpen = true },
                    // )
                }
                // SoundDeck — 분리된 stem + BGM 을 세로 카드 스택으로. 기존 AudioSeparationSheet
                // 와 같은 state 를 공유하므로 한쪽 토글이 다른 쪽에도 즉시 반영. 헤더는 위 Row 가 담당.
                if (com.vibi.cmp.platform.RuntimeFlags.soundDeckEnabled) {
                    val deckGroups = remember(state.separationDirectives, state.bgmClips) {
                        com.vibi.cmp.ui.timeline.sounddeck.buildSoundDeckGroups(
                            separations = state.separationDirectives,
                            bgmClips = state.bgmClips,
                        )
                    }
                    val deckDisabled = state.audioSeparation?.step ==
                        com.vibi.shared.ui.timeline.AudioSeparationStep.PROCESSING
                    // 헤더(Audio/hint)와 첫 카드 사이 간격은 스크롤 Column 의 spacedBy(xs) 만으로 충분 —
                    // 별도 Spacer 제거해 간격 축소.
                    com.vibi.cmp.ui.timeline.sounddeck.SoundDeck(
                        groups = deckGroups,
                        disabled = deckDisabled,
                        expandedSeparationIds = expandedSeparationIds,
                        onToggleSeparationExpanded = { id ->
                            expandedSeparationIds = if (id in expandedSeparationIds)
                                expandedSeparationIds - id
                            else expandedSeparationIds + id
                        },
                        onToggleStem = { directiveId, stemId, selected ->
                            viewModel.onSetStemSelectionForDirective(directiveId, stemId, selected)
                        },
                        onUpdateStemVolume = { directiveId, stemId, volume ->
                            viewModel.onSetStemVolumeForDirective(directiveId, stemId, volume)
                        },
                        onUpdateBgmVolume = { clipId, v -> viewModel.onUpdateBgmVolume(clipId, v) },
                        onCommitBgmEditUndo = { viewModel.commitBgmEditUndo() },
                        onApplyBgmSpeed = { clipId, v -> viewModel.onApplyBgmClipSpeed(clipId, v) },
                        // BGM 분리 trigger — Android stub 환경에서는 ViewModel 진입부의 isSupported
                        // 가드가 silent return 처리 (3단 방어 중 2단).
                        onRemoveBgmBackground = { clipId -> viewModel.onStartBgmSeparation(clipId) },
                        onDeleteBgm = { clipId -> viewModel.onDeleteBgmClip(clipId) },
                        onRenameBgm = { clipId -> renameBgmTargetId = clipId },
                        // 분리/BGM 진입은 위 Audio 헤더의 추가 버튼이 담당 — deck add 슬롯·내부 헤더 미사용.
                        onAddSeparation = null,
                        onAddBgm = null,
                        showHeader = false,
                    )
                    // 영상 다듬기 진입은 timeline 의 영상 파형 탭으로 일원화 (UnifiedTimelineBar
                    // 의 onWaveformTapInNeutral) — 별도 진입 카드 폐기.
                }
            }
        }

        // BGM panel 은 inline list 가 아닌, lane 의 막대 탭 → ModalBottomSheet 로 전환.
        // 아래 `state.selectedBgmClipId` 분기가 sheet 를 띄움.

        Spacer(Modifier.height(VibiSpacing.xs))

        // 저장 상태 메시지 (실패 시) — 헤더 저장 버튼이 진행률을 자체 표시하므로
        // running/done 은 별도 표시 안 함. 실패 메시지만 사용자에게 알림.
        when (val s = state.saveStatus) {
            is SaveStatus.FAILED -> Text(
                s.message,
                color = Color(0xFFFF6B6B),
                style = typo.bodySm,
            )
            else -> Unit
        }
        } // 사운드덱 스크롤 Column 끝

        // 영상/BGM 편집 액션바 — overlay 가 아니라 컬럼 in-flow 최하단. 나타날 때 덱(weight)을 밀어올려
        // 가리지 않는다. None 이면 AnimatedVisibility 0 높이.
        TimelineActionBottomBar(
            target = bottomTarget,
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // A/B (원본/내믹스) 미리듣기 바는 UI 에서 일단 제거 — 추후 재추가 예정. VM 의 [state.previewMode] +
    // [TimelineViewModel.onTogglePreviewMode] + [sounddeck/ABPreviewBar.kt] composable 은 그대로 두어
    // 다시 띄울 때 한 줄 Box 호출만 복구하면 됨.

    // 전체화면 overlay — Dialog 대신 일반 Box overlay 로 (iOS Dialog 는 sheet 스타일 modal 이라
    // 배경이 비치고 시스템 bar 영역이 채워지지 않음). 본 Box 는 outer Box(=fillMaxSize) 의 마지막
    // child 라 자연스럽게 최상단 z-order. BgmPlaybackSync 는 상단에서 1회만 호출하므로 중복 없음.
    if (fullscreenOpen && playerItems.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.backgroundPrimary),
        ) {
            // 영상 본체 — 전체 화면 letterbox. 영상이 채우지 못한 영역은 Box 의 캔버스 색이 보임.
            VideoPlayer(
                items = playerItems,
                isPlaying = state.isPlaying,
                seekToMs = state.playbackPositionMs.takeIf { state.videoDurationMs > 0 },
                onPositionChanged = { ms -> viewModel.onUpdatePlaybackPosition(ms) },
                onEnded = {
                    if (state.isPlaying) viewModel.onTogglePlayback()
                    viewModel.onUpdatePlaybackPosition(0L)
                },
                modifier = Modifier.fillMaxSize().align(Alignment.Center),
            )
            // 상단 행 — 좌: back / 중앙: 원본·내믹스 토글 / 우: spacer. chrome 색은 모두 tokens 페어링.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = VibiSpacing.sm, vertical = VibiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(VibiSpacing.touchMin)
                        .clip(CircleShape)
                        .clickable { fullscreenOpen = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit fullscreen",
                        tint = tokens.onBackgroundPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 원본·내믹스 segmented control — VM 의 [state.previewMode] 가 source of truth.
                // 초기값은 [TimelineUiState.previewMode] 기본인 PreviewMode.MIX (= 내믹스). 2-모드라
                // binary flip 으로 충분. 색은 tokens 페어링 — 양 테마 모두 contrast 보장.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tokens.onBackgroundPrimary.copy(alpha = 0.10f))
                        .padding(2.dp),
                ) {
                    listOf(
                        com.vibi.shared.ui.timeline.PreviewMode.ORIGINAL to "Original",
                        com.vibi.shared.ui.timeline.PreviewMode.MIX to "My mix",
                    ).forEach { (mode, label) ->
                        val selected = state.previewMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) tokens.onBackgroundPrimary else Color.Transparent)
                                .clickable(enabled = !selected) {
                                    if (!selected) viewModel.onTogglePreviewMode()
                                }
                                .padding(horizontal = VibiSpacing.sm, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) tokens.backgroundPrimary else tokens.onBackgroundPrimary,
                                style = typo.bodySm,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // 우측 spacer — back 버튼과 같은 너비로 토글이 정확히 중앙 위치.
                Box(modifier = Modifier.size(40.dp))
            }
            // 하단 transport — 재생/정지 + 현재시각 + 시킹 슬라이더 + 총길이.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = VibiSpacing.base, vertical = VibiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.onTogglePlayback() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = tokens.onBackgroundPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = formatMmSs(state.playbackPositionMs),
                    color = tokens.onBackgroundPrimary,
                    style = typo.bodySm,
                )
                Slider(
                    value = state.playbackPositionMs.toFloat().coerceIn(
                        0f,
                        state.videoDurationMs.toFloat().coerceAtLeast(1f),
                    ),
                    valueRange = 0f..state.videoDurationMs.toFloat().coerceAtLeast(1f),
                    onValueChange = { v -> viewModel.onUpdatePlaybackPosition(v.toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = tokens.onBackgroundPrimary,
                        activeTrackColor = tokens.onBackgroundPrimary,
                        inactiveTrackColor = tokens.onBackgroundPrimary.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatMmSs(state.videoDurationMs),
                    color = tokens.onBackgroundPrimary,
                    style = typo.bodySm,
                )
            }
        }
    }

    // 편집 액션바는 메인 Column in-flow 최하단으로 이동(overlay 제거) — 덱을 밀어올려 가리지 않음.
    // AudioInsertSheet/EntrySheet 는 여전히 BottomCenter overlay 라 필요 시 그 위를 덮는다.

    // 음원 삽입 진입 시트 — Add audio 카드 탭 시 슬라이드 업, 두 큰 카드 (Record / Upload) 노출.
    // 카드 탭 → entry close + audioInsertMode set → 같은 BottomCenter 위치에서 AudioInsertSheet 가 이어 슬라이드 업.
    AudioInsertEntrySheet(
        expanded = audioEntryOpen,
        modifier = Modifier.align(Alignment.BottomCenter),
        onSelect = { mode ->
            audioEntryOpen = false
            audioInsertMode = mode
        },
        onDismiss = { audioEntryOpen = false },
    )

    // 음원 삽입 / 즉시 녹음 통합 peek sheet — 하단 ~48% peek. 위쪽 타임라인은 그대로 노출.
    // 삽입 확정 시 onPickBgmAudio 호출 — 영상보다 길면 BgmTrimSheet 가 자동 chain.
    AudioInsertSheet(
        mode = audioInsertMode,
        modifier = Modifier.align(Alignment.BottomCenter),
        onInsert = { uri ->
            audioInsertMode = null
            viewModel.onPickBgmAudio(uri)
        },
        onDismiss = { audioInsertMode = null },
    )

    // 저장/공유 진행 오버레이 — iOS 사진 앱 영상편집 "완료" 대기와 동일한 결정형 원형 링.
    // 실제 진행률(업로드→서버 렌더→다운로드 합산 percent)만큼 회색 트랙 위 호가 시계방향으로 채워진다.
    // % 텍스트 없이 미니멀. 진행 중 화면을 살짝 dim 처리하고 터치를 차단(하단 UI 오조작 방지).
    // outer Box 의 마지막 child → 최상단 z-order. 100% 도달 후 status 가 DONE/IDLE 로 바뀌면 자연 사라짐.
    val exportPercent = (state.saveStatus as? SaveStatus.RUNNING)?.progress
        ?: (state.shareStatus as? ShareStatus.RUNNING)?.progress
    if (exportPercent != null) {
        // 진행률이 단계적으로(0→30 업로드, 40→89 렌더, 90→100 다운로드) 튀므로 보간해 연속적으로 채워지게.
        val animatedProgress by animateFloatAsState(
            targetValue = (exportPercent / 100f).coerceIn(0f, 1f),
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
            label = "exportProgress",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.backgroundPrimary.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(56.dp),
                color = tokens.onBackgroundPrimary,
                trackColor = tokens.onBackgroundPrimary.copy(alpha = 0.22f),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
            )
        }
    }
    } // close Box wrapper

    // BGM trim 시트 — 영상보다 긴 음원 선택 시 onPickBgmAudio 가 bgmTrimRequest 를 set.
    state.bgmTrimRequest?.let { req ->
        BgmTrimSheet(
            request = req,
            videoDurationMs = state.videoDurationMs,
            onUpdateRange = { s, e -> viewModel.onUpdateBgmTrimRange(s, e) },
            onConfirm = { viewModel.onConfirmBgmTrim() },
            onCancel = { viewModel.onCancelBgmTrim() },
        )
    }

    // BGM 클립 액션 sheet 는 폐기됨 — 선택된 BGM 의 볼륨/속도/배경분리/삭제 액션은 SoundDeck 카드 가
    // 담당하고, 트림은 타임라인 위 좌·우 핸들이 담당한다. 화면 하단에서 sheet 가 올라오는 동선은
    // 새 인터랙션과 중복돼 사용자가 같은 작업을 두 군데서 보게 됨.

    // 음성분리 sheet — 영상편집 모드 아닐 때만.
    state.audioSeparation
        ?.takeIf {
            state.showAudioSeparationSheet && !state.isSegmentEditMode
        }
        ?.let { sepState ->
        AudioSeparationSheet(
            state = sepState,
            onStart = { viewModel.onStartSeparation() },
            onToggleStem = { viewModel.onToggleStemSelection(it) },
            onUpdateStemVolume = { id, vol -> viewModel.onUpdateStemVolume(id, vol) },
            onToggleMuteOriginal = { viewModel.onToggleMuteOriginalSegmentAudio() },
            onConfirmMix = { viewModel.onConfirmStemMix() },
            onDismiss = { viewModel.onDismissAudioSeparationSheet() },
            onDelete = { viewModel.onDeleteCurrentSeparation() },
            onBuyCredits = { viewModel.onRequestBuyCredits() },
            onWantPaidCredits = onWantPaidCredits,
        )
    }

    // BGM "배경음 제거" 첫 분리 비용 confirmation — 영상 구간 "Separate this range" 와 동일 UX.
    // 캐시된 voice-only 토글 (restore↔isolate) 은 비용 없음 → prompt 안 띄움 (VM 에서 분기 처리).
    state.bgmRemovalCostPrompt?.let { prompt ->
        val preview = prompt.costPreview
        val insufficient = preview?.sufficient == false
        AlertDialog(
            onDismissRequest = { viewModel.onDismissBgmRemovalCost() },
            title = { Text("Isolate vocals") },
            text = {
                // IAP 오픈 전 + 잔액 부족이면 "충전" 대신 "곧 열린다" 고지로 전환.
                if (insufficient && !RuntimeFlags.iapEnabled) {
                    PaidCreditsComingSoonNote()
                } else {
                    Text(
                        text = when {
                            preview == null -> "Checking credit balance…"
                            insufficient ->
                                "This separation needs ${preview.credits} credits, " +
                                    "but you only have ${preview.balance}."
                            else -> "This separation will use ${preview.credits} credits " +
                                "(balance: ${preview.balance})."
                        },
                        color = if (insufficient) MaterialTheme.colorScheme.error
                        else LocalContentColor.current,
                    )
                }
            },
            confirmButton = {
                when {
                    // IAP 오픈 전: 부족 → "I want this" 수요 표현. 다이얼로그 닫고 컨페티.
                    insufficient && !RuntimeFlags.iapEnabled -> TextButton(onClick = {
                        viewModel.onDismissBgmRemovalCost()
                        onWantPaidCredits()
                    }) { Text(PAID_CREDITS_CTA_LABEL) }
                    insufficient -> TextButton(onClick = {
                        viewModel.onDismissBgmRemovalCost()
                        viewModel.onRequestBuyCredits()
                    }) { Text("Buy credits") }
                    else -> TextButton(
                        enabled = preview != null,  // fetch 미완료 동안 confirm 잠시 disable
                        onClick = { viewModel.onConfirmBgmRemovalCost() },
                    ) { Text("Confirm") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissBgmRemovalCost() }) { Text("Cancel") }
            },
        )
    }

    // "I want this" 탭 보상 — Popup 으로 그려 위 sheet/dialog 를 모두 덮는다. 트리 내 위치는
    // 무관(Popup 은 최상단 렌더). nonce 로 keyed → 재탭마다 새 인스턴스로 애니메이션 재시작.
    if (confettiVisible) {
        key(confettiNonce) {
            ConfettiOverlay(onFinished = { confettiVisible = false })
        }
    }

    // 프로젝트 이름 변경 — 헤더 제목 탭. 표시용일 뿐 저장/렌더 동작과 무관.
    if (renameProjectOpen) {
        RenameDialog(
            title = "Rename project",
            currentName = state.projectTitle.orEmpty(),
            placeholder = "Untitled",
            onConfirm = { newName ->
                viewModel.onRenameProject(newName)
                renameProjectOpen = false
            },
            onDismiss = { renameProjectOpen = false },
        )
    }

    // 음원·녹음 이름 변경 — 카드 연필 탭. 대상 clip 이 (삭제 등으로) 사라졌으면 다이얼로그 미표시.
    val renameBgmClip = renameBgmTargetId?.let { id -> state.bgmClips.firstOrNull { it.id == id } }
    if (renameBgmClip != null) {
        RenameDialog(
            title = "Rename audio",
            // 진입 시 현재 카드에 표시되는 이름을 기본값으로 — 커스텀명 없으면 자동 라벨(파일명/"Recording N").
            // 카드 라벨 계산(SoundCardModel)과 동일 규칙: recording ordinal = createdAt 정렬 내 순번.
            currentName = renameBgmClip.customName?.takeIf { it.isNotBlank() }
                ?: com.vibi.cmp.ui.timeline.sounddeck.bgmDisplayLabel(
                    renameBgmClip.sourceUri,
                    state.bgmClips
                        .sortedWith(compareBy({ it.createdAt }, { it.id }))
                        .filter { com.vibi.cmp.ui.timeline.sounddeck.isRecordingSourceUri(it.sourceUri) }
                        .withIndex().firstOrNull { it.value.id == renameBgmClip.id }
                        ?.let { it.index + 1 },
                ),
            placeholder = "Audio name",
            onConfirm = { newName ->
                viewModel.onRenameBgmClip(renameBgmClip.id, newName)
                renameBgmTargetId = null
            },
            onDismiss = { renameBgmTargetId = null },
        )
    }

    // 음원분리 취소 경고 — 크레딧 환불 불가 안내 + "다시 보지 않기". 체크 시 Settings 에 영속해
    // 다음부터는 이 다이얼로그 없이 바로 취소된다.
    cancelWarnToken?.let { token ->
        var dontShowAgain by remember(token) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cancelWarnToken = null },
            containerColor = tokens.panelBg,
            titleContentColor = tokens.onBackgroundPrimary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Stop audio separation?",
                        style = typo.titleSm,
                        modifier = Modifier.weight(1f),
                    )
                    // 우측 상단 X = 기존 "Keep processing" 과 동일하게 그냥 닫기(폴링 유지).
                    IconButton(
                        onClick = { cancelWarnToken = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Keep processing",
                            tint = tokens.mutedText,
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
                    Text(
                        "Separation will stop and this part keeps its original audio. " +
                            "Credits already used won't be refunded.",
                        style = typo.bodySm,
                        color = tokens.mutedText,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = tokens.accent,
                                uncheckedColor = tokens.mutedText,
                            ),
                        )
                        Text(
                            "Don't show this again",
                            style = typo.bodySm,
                            color = tokens.onBackgroundPrimary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontShowAgain) viewModel.setSkipSeparationCancelWarning(true)
                    viewModel.onCancelProcessingSeparation(token)
                    cancelWarnToken = null
                    selectedProcessingToken = null
                }) { Text("Stop separation", color = tokens.accent) }
            },
        )
    }

    // 잔액 부족 분기로 띄워지는 UserMenu — 안에 CreditPurchaseSheet 가 내장돼 있어 사용자가
    // BuyCreditsRow 를 탭하면 IAP 흐름 진입. sign out / delete account 도 노출되지만 사용자가
    // timeline 안에서 의도적으로 누르는 케이스라 그대로 진행 — 로그아웃 시 onBack 으로 빠짐.
    if (showUserMenuForCredits) {
        com.vibi.cmp.ui.account.UserMenuSheet(
            onDismiss = { showUserMenuForCredits = false },
            onSignedOut = {
                showUserMenuForCredits = false
                onBack()
            },
        )
    }

    // 구간 선택 — popup sheet 대신 인라인 RangeSlider 로 처리하므로 sheet 제거

}

/**
 * 통합 타임라인 바 — segment/directive content strip + BGM lane 까지 한 컴포넌트로 묶음.
 *
 * 두 단으로 구성:
 *  - 상단 playback region (56dp 고정) : segment/directive 띠, range overlay+핸들, 재생 헤드 drag hit zone.
 *  - 하단 BGM region (가변) : `showBgm && bgmClips 비지 않음` 일 때만 렌더. lane 행마다 막대, 그 아래 lane
 *    수 조절 drag pill. BGM 막대 drag/tap 은 막대 자체에서 처리.
 *
 * 두 region 은 같은 x 좌표계(totalMs ↔ 컨테이너 width)를 공유. 재생 헤드 시각 line 은 상·하단을 관통하고,
 * range 핸들/재생 헤드 drag hit zone 은 충돌 회피를 위해 상단 playback region 안쪽에서만 잡힌다.
 *
 * 색 분리: 외곽 bg = trackColor.alpha(0.45) 단일 면, segment bg/edited = 중성 회색 톤,
 * range fill = accent, BGM 막대 = accent (선택 시 진하게).
 *
 * 제스처:
 *  - segment 탭 (영상편집) → onSegmentTap (그 segment 전체로 range 스냅)
 *  - directive 탭 (음원분리) → onDirectiveTap (편집 sheet)
 *  - 빈 영역 탭 → onScrub
 *  - range fill 드래그 → onTranslateRange (양쪽 끝 동시 이동)
 *  - 좌/우 핸들 드래그 → onRangeStartChange/EndChange
 *  - 재생 마커 = playbackPositionMs 위치 흰선 (BGM region 까지 관통, drag 은 상단 hit zone 에서)
 *  - BGM 막대 drag → onBgmUpdateStart / onBgmUpdateLane
 *  - BGM 막대 탭 (bgmTapEnabled) → onBgmSelectClip
 *  - 하단 pill drag → onBgmSetLaneCount
 */
/**
 * 통합 타임라인 바의 시각/제스처 spec — 사이즈/간격/clamp 등 매직 넘버 한 곳에 모음.
 * 색은 [VibiColors] 의 `timelineBar*` 토큰 사용 — light/dark 자동 분기.
 */
private object TimelineBarSpec {
    val BarHeight = 56.dp
    val ContentHeight = VibiSpacing.sm
    /**
     * 영상 audio 파형 strip 높이 — ContentHeight(12dp) 회색 strip 보다 키워 파형 막대가 시각적으로
     * 드러나게. 56dp playback region 안에 centered 라 위/아래로 ~8dp 여유가 남아 range 핸들 grip
     * (gripHeight = ContentHeight) 이 파형 위에 겹쳐도 답답해 보이지 않는다.
     */
    val WaveformHeight = 45.dp
    val HandleHitWidth = VibiSpacing.xl
    val HandleVisualWidth = VibiSpacing.xs
    val GripWidth = 3.dp
    val GripVerticalInset = VibiSpacing.sm
    val ContentCornerRadius = VibiSpacing.xxs
    val HandleCornerRadius = VibiSpacing.xxs
    val SegmentSpacing = 1.dp
    /** 재생 마커 hit area — drag 으로 scrub. 마커 visual 자체는 GripWidth, hit zone 은 더 넓게. */
    val PlaybackHitWidth = VibiSpacing.xl
    /** 재생 마커 visual line 높이 — 바 높이보다 짧게 (bar 위/아래로 marker 가 튀어나오지 않도록). */
    val PlaybackMarkerVerticalInset = VibiSpacing.base
    /** 구간 선택 영역 상/하단 accent border 두께 — Android 초기 트림 핸들 스타일. */
    val RangeBorderThickness = 2.dp
    /** range 핸들 사이 최소 간격 — VM 의 MIN_RANGE_MS 와 동일 의미. */
    const val MinRangeGapMs = 100L
    /** BGM lane 수 상한 — drag pill 로 늘릴 수 있는 lane 개수 max. */
    const val MaxBgmLaneCount = 8
    val ChevronThumbWidth = 10.dp
    val ChevronIconSize = 14.dp
    const val MinZoom = 1f
    const val MaxZoom = 10f
    /** 상단 시간 눈금자 (CapCut 스타일) 높이 — major tick + 라벨 1줄. */
    val RulerHeight = 20.dp
    /** 라벨 사이 최소 시각 간격 — nice-interval snap 의 px 기준 (dp). */
    val RulerLabelTargetSpacing = 80.dp
    val RulerMajorTickHeight = 6.dp
    val RulerMinorTickHeight = 3.dp
    /** 진행 중(음원분리/BGM 제거) 구간 위 중앙 로딩 스피너 지름. */
    val ProcessingSpinnerSize = 16.dp
    /** 스피너 링 두께. */
    val ProcessingSpinnerStroke = 2.dp
    /** 구간 폭이 이보다 좁으면 스피너 생략 (겹침/넘침 방지) — 색만 유지. */
    val ProcessingSpinnerMinWidth = 22.dp
    /** BGM 컬러 블록용 backing 원 패딩 — 지름 = Size + Pad. */
    val ProcessingSpinnerBackingPad = 8.dp
}

/**
 * 진행 중(음원분리/BGM 배경음 제거) 구간 위 중앙 무한 회전 스피너. 기존 fill/scrim 색 위에 얹어
 * "처리 중" 을 모션으로 표시. [backing] 은 BGM 처럼 채도 있는 블록 위에 올릴 때만 지정 —
 * 스피너 뒤에 대비용 원을 깐다 (중립 파형 위인 음원분리는 불필요).
 */
@Composable
private fun ProcessingSpinner(color: Color, backing: Color? = null) {
    Box(contentAlignment = Alignment.Center) {
        if (backing != null) {
            Box(
                modifier = Modifier
                    .size(TimelineBarSpec.ProcessingSpinnerSize + TimelineBarSpec.ProcessingSpinnerBackingPad)
                    .clip(CircleShape)
                    .background(backing)
            )
        }
        // Material3 기본 indeterminate 스피너는 호(arc) 길이가 늘었다 줄었다(sweep 애니) 해서 "선이 길어졌다
        // 짧아졌다" 보임. 여기선 길이 고정 호를 등속 회전만 시켜 일관된 모양 유지.
        val transition = rememberInfiniteTransition(label = "processingSpinner")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "processingSpinnerRotation",
        )
        val strokePx = with(LocalDensity.current) { TimelineBarSpec.ProcessingSpinnerStroke.toPx() }
        Canvas(modifier = Modifier.size(TimelineBarSpec.ProcessingSpinnerSize)) {
            val inset = strokePx / 2f
            drawArc(
                color = color,
                startAngle = rotation,
                sweepAngle = 270f, // 고정 호 길이 (3/4 링) — 회전만, 길이 변화 없음
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * 좌/우 트림 (음원분리 range + BGM 클립) 공용 chevron thumb.
 *
 * [outerCornerSharp] true 면 boundary 에 flush 된 쪽 (Start=left, End=right) 의 위·아래 모서리만
 * 직각으로 처리 — chevron 의 둥근 corner 가 만들어내던 fill/bar 와의 1-2dp 단차 ("따로 노는" 시각)
 * 차단. inner 쪽 (range 안쪽으로 향하는 면) 모서리는 그대로 둥글게 유지해 CapCut 식 부드러운 느낌.
 */
@Composable
private fun ChevronThumb(
    side: BgmTrimSide,
    height: androidx.compose.ui.unit.Dp,
    handleColor: Color,
    gripColor: Color,
    outerCornerSharp: Boolean = false,
) {
    val r = TimelineBarSpec.HandleCornerRadius
    val shape = if (!outerCornerSharp) RoundedCornerShape(r)
        else when (side) {
            BgmTrimSide.Start -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = r, bottomEnd = r)
            BgmTrimSide.End -> RoundedCornerShape(topStart = r, bottomStart = r, topEnd = 0.dp, bottomEnd = 0.dp)
        }
    Box(
        modifier = Modifier
            .width(TimelineBarSpec.ChevronThumbWidth)
            .height(height)
            .clip(shape)
            .background(handleColor),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (side == BgmTrimSide.Start)
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (side == BgmTrimSide.Start) "Trim start" else "Trim end",
            tint = gripColor,
            modifier = Modifier.size(TimelineBarSpec.ChevronIconSize),
        )
    }
}

/**
 * UnifiedTimelineBar 에 전달되는 in-flight 음원분리 overlay 1건. ViewModel 의
 * [com.vibi.shared.ui.timeline.ProcessingSeparation] 가 UI 좌표로 펴진 형태.
 */
data class ProcessingSeparationOverlay(
    val startMs: Long,
    val endMs: Long,
    val progress: Int,
    /** 취소 대상 식별자 — 진행 바 탭 시 이 토큰으로 해당 폴링 잡을 취소. */
    val clientToken: String,
)

/**
 * '][' 구간 분리 아이콘 — 머티리얼에 해당 글리프가 없어 Canvas 로 직접 그린다. 가운데 두 spine(] 와 [)
 * 이 마주보고, 위/아래 tick 이 바깥으로 향해 "이 지점에서 자른다"를 표현. (텍스트 글리프 대신 vector.)
 */
@Composable
private fun SplitIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.1f).coerceAtLeast(1.5f)
        val gap = w * 0.16f     // 가운데에서 각 spine 까지 거리
        val tick = w * 0.22f    // 위/아래 가로 tick 길이
        val cx = w / 2f
        val top = h * 0.18f
        val bot = h * 0.82f
        val leftSpine = cx - gap
        val rightSpine = cx + gap
        val cap = StrokeCap.Round
        // ] — 왼쪽 spine, tick 은 왼쪽으로.
        drawLine(tint, Offset(leftSpine, top), Offset(leftSpine, bot), stroke, cap)
        drawLine(tint, Offset(leftSpine, top), Offset(leftSpine - tick, top), stroke, cap)
        drawLine(tint, Offset(leftSpine, bot), Offset(leftSpine - tick, bot), stroke, cap)
        // [ — 오른쪽 spine, tick 은 오른쪽으로.
        drawLine(tint, Offset(rightSpine, top), Offset(rightSpine, bot), stroke, cap)
        drawLine(tint, Offset(rightSpine, top), Offset(rightSpine + tick, top), stroke, cap)
        drawLine(tint, Offset(rightSpine, bot), Offset(rightSpine + tick, bot), stroke, cap)
    }
}

@Composable
private fun UnifiedTimelineBar(
    segments: List<com.vibi.shared.domain.model.Segment>,
    directives: List<com.vibi.shared.domain.model.SeparationDirective>,
    showSegments: Boolean,
    showDirectives: Boolean,
    showRange: Boolean,
    totalMs: Long,
    playbackPositionMs: Long,
    rangeStartMs: Long,
    rangeEndMs: Long,
    selectedSegmentId: String?,
    accent: Color,
    markerColor: Color,
    trackColor: Color,
    segmentColor: Color,
    segmentEditedColor: Color,
    directiveColor: Color,
    /**
     * 영상 audio peaks (0..1 normalized). 비지 않으면 재생바 strip 배경에 파형으로 렌더 — directive
     * 구간 내 막대는 accent 컬러로, 그 외는 회색으로 칠해 음원분리 영역을 시각 표시.
     * 비어 있으면 (Android stub / 추출 실패) 기존 회색 strip + directive 막대 fallback.
     */
    videoPeaks: List<Float> = emptyList(),
    /** stem audioUrl → 추출된 peaks. directive 영역에서 source peaks 대신 사용해 각 stem 의 실제 파형 노출. */
    stemPeaksByUrl: Map<String, List<Float>> = emptyMap(),
    /** Peak 가 추출된 source URI — segment.sourceUri 가 일치하는 segment 만 peak lookup. 다른 source 영역은 0. */
    primarySourceUri: String = "",
    /** Peak source 의 raw duration (ms). segment trim/speed 역매핑에 사용. */
    primarySourceDurationMs: Long = 0L,
    /** 진행 중인 음원분리 range 들 — 동시에 여러 구간이 분리 진행될 수 있어 리스트로 받음. */
    processingSeparations: List<ProcessingSeparationOverlay> = emptyList(),
    onSegmentTap: (segmentId: String, tapMs: Long) -> Unit = { _, _ -> },
    /** Neutral 모드에서 CapCut 블록을 길게 눌러 드래그 재정렬 — (segmentId, 목표 index). */
    onMoveSegment: (segmentId: String, targetIndex: Int) -> Unit = { _, _ -> },
    /** Neutral (range/segment edit 모드 아님) 상태에서 영상 파형 탭 — BGM 클립 탭과 같은 의미로 영상 다듬기 진입. */
    onWaveformTapInNeutral: (tapMs: Long) -> Unit = {},
    /** 진행 중인 음원분리 바를 탭 — 해당 clientToken 으로 취소 UI 노출. 다른 탭(파형/구간)보다 우선. */
    onProcessingTap: (clientToken: String) -> Unit = {},
    onDirectiveTap: (String) -> Unit = {},
    onScrub: (Long) -> Unit,
    onRangeStartChange: (Long) -> Unit = {},
    onRangeEndChange: (Long) -> Unit = {},
    onTranslateRange: (Long) -> Unit = {},
    /** 음원분리 range 모드에서 free interval 탭 시 그 구간 [start, end] 로 range 스냅. */
    onFreeIntervalTap: (startMs: Long, endMs: Long) -> Unit = { _, _ -> },
    /** 음원분리 range 모드에서 현재 선택된 구간 내부를 재탭 시 호출 — 선택 해제. */
    onRangeTapToggle: () -> Unit = {},
    /** BGM lane 통합 슬롯 — clips 가 있고 [showBgm]=true 일 때만 하단에 lane region 렌더. */
    bgmClips: List<com.vibi.shared.domain.model.BgmClip> = emptyList(),
    /**
     * 클립 id → 표시 lane. 선택 없을 땐 전부 0 (한 줄), 선택 있을 땐 자동 pack 된 lane.
     * [computeBgmDisplayLayout] 가 호출부에서 생성.
     */
    bgmLaneByClipId: Map<String, Int> = emptyMap(),
    /** 현재 사용 중인 lane 수 (= max(lane) + 1). region 높이 계산 입력. */
    bgmDisplayLaneCount: Int = 1,
    selectedBgmClipId: String? = null,
    /** 배경음 제거(음원분리) 진행 중인 BGM 클립 id — 해당 클립은 선택/드래그/트림 잠금 + scrim 표시. */
    separatingBgmClipIds: Set<String> = emptySet(),
    bgmTapEnabled: Boolean = true,
    /** false 면 BGM clip 의 위치 drag + 트림 핸들 disable. 탭(선택)은 분리 — `bgmTapEnabled` 가 담당.
     *  segment edit (영상 다듬기) 모드에서 BGM 트랙을 read-only 로 잠그기 위함. */
    bgmDragEnabled: Boolean = true,
    onBgmSelectClip: (String) -> Unit = {},
    onBgmUpdateStart: (String, Long) -> Unit = { _, _ -> },
    /**
     * 선택된 BGM 의 좌·우 트림 핸들 드래그 시 호출. start 핸들이면 [newStartMs] 가 동반 (CapCut 의미상
     * 좌측 잘릴 때 timeline 좌측 엣지가 손가락 위치에 머묾), end 핸들이면 null.
     */
    onBgmUpdateTrim: (clipId: String, sourceTrimStartMs: Long, sourceTrimEndMs: Long, newStartMs: Long?) -> Unit = { _, _, _, _ -> },
    /** BGM sourceUri → 0..1 normalized peaks. 블록 내부 mini 파형 렌더용. 비어 있으면 단색 fallback. */
    bgmPeaksByUri: Map<String, List<Float>> = emptyMap(),
    showBgm: Boolean = false,
    /** true 면 영상 strip 의 range overlay 숨기고 BGM lane 에 range fill 노출. mutual exclusion. */
    bgmRangeMode: Boolean = false,
) {
    val density = LocalDensity.current
    val tokens = LocalVibiColors.current
    val currentRangeStart by rememberUpdatedState(rangeStartMs)
    val currentRangeEnd by rememberUpdatedState(rangeEndMs)

    val rulerHeight = TimelineBarSpec.RulerHeight
    val playbackRegionHeight = TimelineBarSpec.BarHeight
    val contentHeight = TimelineBarSpec.ContentHeight
    val handleHitWidth = TimelineBarSpec.HandleHitWidth
    val handleVisualWidth = TimelineBarSpec.HandleVisualWidth

    // BGM region metric — clips 가 있을 때만 렌더. 없으면 전체 높이 = playbackRegionHeight (기존과 동일).
    val showBgmRegion = showBgm && bgmClips.isNotEmpty() && totalMs > 0L
    // BGM 클립 블록 높이 — 영상 파형 strip (45dp) 보다 살짝 얇게 (38dp) 두 트랙 위계 표현.
    val bgmRowHeight = 38.dp
    val bgmRowGap = 4.dp
    val bgmRowCount = bgmDisplayLaneCount.coerceAtLeast(1)
    val bgmRegionHeight = if (showBgmRegion) {
        bgmRowHeight * bgmRowCount + bgmRowGap * (bgmRowCount - 1).coerceAtLeast(0)
    } else 0.dp
    val bgmRowStrideDp = bgmRowHeight + bgmRowGap
    val playheadVisualBottom = rulerHeight + playbackRegionHeight + bgmRegionHeight
    val totalHeight = playheadVisualBottom

    // range 모드 (영상편집 + 음원분리) 양쪽 다 parent tap detector 단일화. segment 자체에 clickable
    // 두지 않고 ms 좌표로 segment id 를 역검색 → onSegmentTap 호출. 같은 segment 재탭 시 VM 의
    // onSelectSegmentInEdit 토글 로직이 처리. modifier 는 top playback region 안쪽에 부착해 BGM
    // lane drag 와 분리 — 빈 영역 탭은 영상 timeline 위에서만 잡힌다.
    val currentStartForTap by rememberUpdatedState(rangeStartMs)
    val currentEndForTap by rememberUpdatedState(rangeEndMs)
    // pointerInput key — progress 변경 (1~2초 폴링) 마다 processingSeparations ref 가 새로 생성되지만
    // tap 검출은 start/end 만 보므로 그 둘만 추출. equals 가 stable 이라 polling tick 동안 gesture
    // detector 가 재등록되지 않음.
    val processingRangesKey = remember(processingSeparations) {
        processingSeparations.map { Triple(it.startMs, it.endMs, it.clientToken) }
    }
    val rangeTapModifier = if (showRange && totalMs > 0L) {
        Modifier.pointerInput(showSegments, segments, directives, processingRangesKey, totalMs) {
            detectTapGestures(onTap = { offset ->
                val w = size.width.toFloat()
                if (w <= 0f) return@detectTapGestures
                val frac = (offset.x / w).coerceIn(0f, 1f)
                val ms = (frac * totalMs).toLong()

                if (showSegments) {
                    // 영상편집: ms 가 속한 video segment 검색 → onSegmentTap (재탭은 VM 에서 토글).
                    var acc = 0L
                    for (seg in segments) {
                        val nextAcc = acc + seg.effectiveDurationMs
                        if (ms in acc until nextAcc) {
                            if (seg.type == com.vibi.shared.domain.model.SegmentType.VIDEO) {
                                onSegmentTap(seg.id, ms)
                            }
                            return@detectTapGestures
                        }
                        acc = nextAcc
                    }
                    return@detectTapGestures
                }

                // BGM 구간편집 중(bgmRangeMode) 영상 strip 탭 → 영상 편집(segment edit)으로 전환 — neutral
                // 영상 탭과 동일 진입(onEnterSegmentEditMode: isSegmentEditMode=true + editTargets=Video +
                // selectedBgmClipId 해제). 이게 없으면 아래 free-interval → onSelectVideoRange 가 editTargets=Bgm
                // 인 채 range 만 잡아, 하단 버튼은 BGM·range 핸들은 BGM 레인에 뜨는 꼬임(트랙 전환 실패)이 났음.
                if (bgmRangeMode) {
                    onWaveformTapInNeutral(ms)
                    return@detectTapGestures
                }

                // 진행 중 음원분리 바 위 탭 → 취소 UI 노출 (점유 no-op 보다 우선).
                processingSeparations.firstOrNull { ms in it.startMs..it.endMs }?.let {
                    onProcessingTap(it.clientToken)
                    return@detectTapGestures
                }

                // 음원분리 *추가* 흐름: 진행 중(processing) + 완료된 directive 모두 점유로 취급해 그 위
                // 탭/range 를 막는다 (이미 분리됐거나 분리 중인 구간 재분리 방지). 그 외 free interval → snap,
                // 현재 선택 영역 재탭 → toggle. (영상 다듬기 모드는 if(showSegments) 분기에서 별도 처리 —
                // 완료 구간은 선택 허용.)
                data class OccupiedRange(val start: Long, val end: Long)
                val occupied = (
                    directives.map { OccupiedRange(it.rangeStartMs, it.rangeEndMs) } +
                        processingSeparations.map { OccupiedRange(it.startMs, it.endMs) }
                    ).sortedBy { it.start }
                val onOccupied = occupied.any { ms in it.start..it.end }
                if (onOccupied) return@detectTapGestures
                val hasSelection = currentEndForTap > currentStartForTap
                if (hasSelection && ms in currentStartForTap..currentEndForTap) {
                    onRangeTapToggle()
                    return@detectTapGestures
                }
                var freeStart = 0L
                var freeEnd = totalMs
                for (d in occupied) {
                    if (ms < d.start) {
                        freeEnd = d.start
                        break
                    }
                    freeStart = maxOf(freeStart, d.end)
                }
                // 세그먼트 단위 선택 — 탭한 세그먼트(블록) 범위를 free 구간과 교집합해 그 세그먼트만 선택.
                // 이후 좌/우 핸들로 free 구간 안에서 자유 조절 (rangeBoundsForCurrentMode 분리 분기).
                var segStart = freeStart
                var segEnd = freeEnd
                run {
                    var acc = 0L
                    for (seg in segments) {
                        val next = acc + seg.effectiveDurationMs
                        if (ms in acc until next) { segStart = acc; segEnd = next; return@run }
                        acc = next
                    }
                }
                onFreeIntervalTap(maxOf(freeStart, segStart), minOf(freeEnd, segEnd))
            })
        }
    } else if (totalMs > 0L) {
        // Neutral 상태 — 영상 파형 탭 시 영상 다듬기 진입 (BGM 클립 탭과 같은 진입 의미).
        // 단, 진행 중 음원분리 바 위 탭은 취소 UI 로 분기 (다듬기 진입보다 우선).
        Modifier.pointerInput(totalMs, processingRangesKey) {
            detectTapGestures(onTap = { offset ->
                val w = size.width.toFloat()
                val ms = if (w > 0f) ((offset.x / w).coerceIn(0f, 1f) * totalMs).toLong() else 0L
                processingSeparations.firstOrNull { ms in it.startMs..it.endMs }?.let {
                    onProcessingTap(it.clientToken)
                    return@detectTapGestures
                }
                onWaveformTapInNeutral(ms)
            })
        }
    } else Modifier

    // content 폭을 viewport*zoom 으로 늘리고 우리가 직접 든 scrollPx(offset)로 pan — 내부 ms↔px 수식은 그대로 동작.
    // zoom·scrollPx 둘 다 직접 보유한 float state 라 같은 프레임에 함께 적용됨 → 확대 중 레이아웃 지연으로 인한
    // 스크롤 클램프 불일치(떨림) 가 없음. (horizontalScroll/ScrollState 는 dispatchRawDelta 가 직전 레이아웃
    //  기준 maxValue 로 잘려 확대 시 한 프레임 늦게 따라잡아 진동을 일으켰음.)
    var zoom by remember(totalMs) { mutableFloatStateOf(1f) }
    var scrollPx by remember(totalMs) { mutableFloatStateOf(0f) }

    // 세그먼트 드래그 재정렬 상태 — 함수 레벨로 hoist (블록 레이어 + 파형 마스킹 양쪽에서 참조).
    // 드래그 중엔 연속 파형/directive fill 을 숨기고 reflow 되는 카드만 노출 → "파형이 안 따라오는" 혼란 제거.
    var dragSegId by remember(totalMs) { mutableStateOf<String?>(null) }
    var dragTranslatePx by remember(totalMs) { mutableFloatStateOf(0f) }
    var dragDropIndex by remember(totalMs) { mutableIntStateOf(-1) }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clip(RoundedCornerShape(6.dp))
    ) {
        val viewportWidthDp = maxWidth
        val viewportWidthPx = with(density) { viewportWidthDp.toPx() }
        val contentWidthDp = viewportWidthDp * zoom
        val contentWidthPx = viewportWidthPx * zoom
        // zoom 을 바꾸는 건 아래 pinch 제스처뿐이고 거기서 scrollPx 를 새 max 로 coerce 하므로
        // scrollPx 는 항상 [0, maxScroll] 안. offset 적용 시에도 한 번 더 coerce 해 안전망.
        val maxScroll = (contentWidthPx - viewportWidthPx).coerceAtLeast(0f)

        // 1-finger pan/fling — 직전엔 horizontalScroll 이 담당. scrollable 도 자식이 소비한 제스처엔 양보하므로
        // range/playhead/BGM drag 핸들러는 그대로 우선. maxScroll 은 매 프레임 갱신되니 rememberUpdatedState 로 최신값 참조.
        val maxScrollState = rememberUpdatedState(maxScroll)
        val scrollableState = rememberScrollableState { delta ->
            val old = scrollPx
            val target = (old - delta).coerceIn(0f, maxScrollState.value)
            scrollPx = target
            old - target
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .clipToBounds()
                .scrollable(state = scrollableState, orientation = Orientation.Horizontal)
                .pointerInput(totalMs) {
                    // 2-finger 만 처리 — 1-finger 는 consume 하지 않아 inner range/playhead/BGM drag
                    // 핸들러가 그대로 받음. detectTransformGestures 는 1-finger pan 도 잡아 충돌.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var prevCentroid: Offset? = null
                        var prevSpan = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val activeCount = event.changes.count { it.pressed }
                            if (activeCount < 2) {
                                prevCentroid = null
                                prevSpan = 0f
                                if (activeCount == 0) break
                                continue
                            }
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val span = event.calculateCentroidSize(useCurrent = true)
                            val pc = prevCentroid
                            val ps = prevSpan
                            if (pc != null && ps > 0f && span > 0f) {
                                val newZoom = (zoom * span / ps)
                                    .coerceIn(TimelineBarSpec.MinZoom, TimelineBarSpec.MaxZoom)
                                val actualFactor = newZoom / zoom
                                val newMaxScroll = (viewportWidthPx * newZoom - viewportWidthPx)
                                    .coerceAtLeast(0f)
                                // 직전 centroid(pc.x) 아래 있던 콘텐츠 픽셀을 현재 centroid.x 아래로 고정 →
                                // 확대 중심이 손가락을 정확히 따라가고(두 손가락 이동분 pan 도 포함), 떨림 없음.
                                scrollPx = (actualFactor * (scrollPx + pc.x) - centroid.x)
                                    .coerceIn(0f, newMaxScroll)
                                zoom = newZoom
                                event.changes.forEach { it.consume() }
                            }
                            prevCentroid = centroid
                            prevSpan = span
                        }
                    }
                }
        ) {
        Box(
            modifier = Modifier
                // 오버사이즈(확대) 콘텐츠를 부모 안에서 가운데가 아닌 **start(좌측)** 로 고정 — 안 하면 Box 가
                // viewport 보다 넓은 자식을 center 배치해 scrollPx=0 에서 앞부분이 화면 왼쪽 밖으로 밀리고
                // (앞 빈칸), 끝 너머로도 빈 화면이 스크롤되는 문제가 났음. unbounded=true 로 viewport 초과 허용.
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .requiredWidth(contentWidthDp)
                .height(totalHeight)
                .offset { IntOffset(-scrollPx.coerceIn(0f, maxScroll).roundToInt(), 0) }
                .background(trackColor.copy(alpha = 0.45f))
        ) {
        val totalWidthDp = contentWidthDp
        val totalWidthPx = contentWidthPx

        // === 상단 시간 눈금자 (CapCut 스타일) — 줌 레벨에 따라 nice-interval 라벨 + minor tick ===
        TimelineRuler(
            totalMs = totalMs,
            contentWidthDp = contentWidthDp,
            contentWidthPx = contentWidthPx,
            tickColor = markerColor.copy(alpha = 0.45f),
            labelColor = tokens.mutedText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(rulerHeight),
        )

        // === 상단 playback region — 기존 단일 바 56dp 의 모든 시각/제스처가 여기 안에서 동작 ===
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = rulerHeight)
                .fillMaxWidth()
                .height(playbackRegionHeight)
                .then(rangeTapModifier),
        ) {
            // Layer 1 — 영상 audio 파형. 영상편집 모드에서도 동일하게 — 사용자가 audio 위치를 보면서
            // 구간을 잡도록. segment 의 volume/speed 변경은 파형 bar 높이/밀도에 이미 반영됨
            // (TimelineWaveformBackground 가 seg.volumeScale, speedScale 을 곱해 매핑).
            // peaks 비면 (Android stub / 추출 실패) 회색 strip + directive 막대 fallback.
            // 드래그 재정렬 중엔 연속 파형/fallback 을 숨긴다 — 파형은 타임라인 전체 한 덩어리라 세그먼트별로
            // 따라 움직이지 못해 "카드만 움직이고 파형은 가만히 있다 점프" 하는 혼란을 줬음. 드래그 중엔
            // 콘텐츠 bg 위에서 reflow 되는 카드만 노출.
            val hasWaveform = videoPeaks.isNotEmpty() && totalMs > 0L
            if (hasWaveform && dragSegId == null) {
                    TimelineWaveformBackground(
                        sourcePeaks = videoPeaks,
                        segments = segments,
                        primarySourceUri = primarySourceUri,
                        sourceDurationMs = primarySourceDurationMs,
                        totalMs = totalMs,
                        directives = directives,
                        stemPeaksByUrl = stemPeaksByUrl,
                        // 불투명 흰 패널 위에서 파형이 또렷하게 — 기존 0.45 는 흰 배경에서 밋밋한 중간 회색.
                        defaultBarColor = markerColor.copy(alpha = 0.65f),
                        highlightBarColor = accent,
                        // 라이트 #F5F5F5 (흰 패널 위 밝은 회색), 다크 #1C1917 (elevated plate). 테두리 없음.
                        trackBg = tokens.timelineWaveformStripBg,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(TimelineBarSpec.WaveformHeight),
                    )
                    if (showDirectives && directives.isNotEmpty()) {
                        // range 모드에선 parent rangeTapModifier 가 directive tap 도 처리 — 본 overlay clickable 비활성.
                        DirectiveOverlayRow(
                            directives = directives,
                            totalMs = totalMs,
                            height = TimelineBarSpec.WaveformHeight,
                            barColor = null,
                            // directive 탭은 no-op — clickable 자체를 안 붙여 ripple 시각 효과 제거.
                            tapEnabled = false,
                            onDirectiveTap = onDirectiveTap,
                        )
                    }
                } else if (dragSegId == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(contentHeight)
                            // 파형 strip 과 동일 — 로드 전/후 레인 일관. 테두리 없음.
                            .clip(VibiShape.lg)
                            .background(tokens.timelineWaveformStripBg)
                    )
                    if (showDirectives && directives.isNotEmpty() && totalMs > 0L) {
                        DirectiveOverlayRow(
                            directives = directives,
                            totalMs = totalMs,
                            height = contentHeight,
                            barColor = directiveColor,
                            // directive 탭은 no-op — clickable 자체를 안 붙여 ripple 시각 효과 제거.
                            tapEnabled = false,
                            onDirectiveTap = onDirectiveTap,
                        )
                    }
            }

            // 이미 분리 완료된 directive 구간 — 옅은 fill 로 점유 표시. 파형 highlight 색만으로는
            // 음원분리 range 선택 중 어디가 occupied 인지 한눈에 안 들어와 사용자 보고.
            // 드래그 중엔 숨김 — 파형과 마찬가지로 한 덩어리라 따라 움직이지 못해 혼란.
            if (totalMs > 0L && dragSegId == null) {
                val dirFillHeight = TimelineBarSpec.WaveformHeight
                directives.forEach { d ->
                    if (d.rangeEndMs <= d.rangeStartMs) return@forEach
                    val dStart = (d.rangeStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    val dEnd = (d.rangeEndMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    val dStartDp = totalWidthDp * dStart
                    val dWidthDp = totalWidthDp * (dEnd - dStart).coerceAtLeast(0f)
                    Box(
                        modifier = Modifier
                            .offset(x = dStartDp)
                            .width(dWidthDp)
                            .height(dirFillHeight)
                            .align(Alignment.CenterStart)
                            .background(accent.copy(alpha = 0.12f))
                    )
                }
            }

            // === CapCut 세그먼트 블록 + 드래그 재정렬 — 각 영상 segment 를 둥근 사각형으로 묶어 경계 시각화
            // (분할/복제로 생긴 세그먼트가 한눈에). 파형/directive 위에 outline 으로 올려 파형 가독성 유지,
            // 블록 사이 작은 gap 으로 분할 표현. neutral 모드에선 블록을 길게 눌러 드래그로 순서 변경 가능
            // (directive 는 세그먼트 앵커링으로 자동 동반 — onMoveSegment → reanchorDirectiveCache).
            if (totalMs > 0L && segments.isNotEmpty()) {
                val blockHeight = TimelineBarSpec.WaveformHeight
                val blockGap = 2.dp
                val blockGapPx = with(density) { blockGap.toPx() }
                // 재정렬 = 일반(neutral) + 영상편집 모드. 음원분리 범위 선택 중(showRange && !showSegments)만 제외.
                val reorderEnabled = showSegments || !showRange
                val edgePx = with(density) { 44.dp.toPx() }
                val autoPanStepPx = with(density) { 6.dp.toPx() }

                // finger content-x → 드롭 index (드래그 대상 제외, 나머지 세그먼트 중심이 finger 왼쪽인 개수).
                fun dropIndexFor(fingerContentX: Float, draggedId: String): Int {
                    var acc = 0f; var count = 0; var others = 0
                    segments.forEach { s ->
                        if (s.type != com.vibi.shared.domain.model.SegmentType.VIDEO) return@forEach
                        val wPx = totalWidthPx * (s.effectiveDurationMs.toFloat() / totalMs)
                        if (s.id != draggedId) {
                            others++
                            if (acc + wPx / 2f < fingerContentX) count++
                        }
                        acc += wPx
                    }
                    return count.coerceIn(0, others)
                }

                // CapCut식 reflow 기준 — 들린 세그먼트의 index/width, 드롭 슬롯(k=dragDropIndex).
                val draggedSeg = dragSegId?.let { id -> segments.firstOrNull { it.id == id } }
                val draggedIdx = if (dragSegId != null) segments.indexOfFirst { it.id == dragSegId } else -1
                val draggedWpx = draggedSeg?.let { totalWidthPx * (it.effectiveDurationMs.toFloat() / totalMs) } ?: 0f

                var accMs = 0L
                segments.forEachIndexed { i, seg ->
                    val segStart = accMs
                    val segEnd = accMs + seg.effectiveDurationMs
                    accMs = segEnd
                    if (seg.type != com.vibi.shared.domain.model.SegmentType.VIDEO) return@forEachIndexed
                    val sFrac = (segStart.toFloat() / totalMs).coerceIn(0f, 1f)
                    val eFrac = (segEnd.toFloat() / totalMs).coerceIn(0f, 1f)
                    val rawWidthDp = totalWidthDp * (eFrac - sFrac).coerceAtLeast(0f)
                    if (rawWidthDp <= blockGap) return@forEachIndexed
                    val blockStartContentPx = totalWidthPx * sFrac + blockGapPx / 2f
                    val isDragging = dragSegId == seg.id
                    // 다른 블록은 들린 세그먼트가 빠진 자리(remove: 뒤쪽 -W) + 드롭 슬롯 앞에 열리는 빈자리
                    // (insert: 슬롯 이후 +W)를 반영해 밀려난다 → 들린 블록이 놓일 gap 이 실시간으로 열림.
                    val reflowTargetPx = if (draggedSeg != null && !isDragging) {
                        val j = if (i < draggedIdx) i else i - 1
                        (if (i > draggedIdx) -draggedWpx else 0f) + (if (j >= dragDropIndex) draggedWpx else 0f)
                    } else 0f
                    val animatedReflowPx by animateFloatAsState(reflowTargetPx, label = "segReflow")

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)   // 들린 블록은 다른 블록 위로
                            .offset(x = totalWidthDp * sFrac + blockGap / 2)
                            .width(rawWidthDp - blockGap)
                            .height(blockHeight)
                            .align(Alignment.CenterStart)
                            .then(
                                if (!reorderEnabled) Modifier
                                else Modifier.pointerInput(seg.id, segments, totalMs, totalWidthPx, viewportWidthPx, maxScroll) {
                                    // 롱프레스→드래그 표준 제스처 — scrollable 부모/탭 검출과 깔끔히 공존(재정렬 리스트 패턴).
                                    var grabOffsetX = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            grabOffsetX = offset.x
                                            dragSegId = seg.id
                                            dragTranslatePx = 0f
                                            dragDropIndex = dropIndexFor(blockStartContentPx + grabOffsetX, seg.id)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragTranslatePx += dragAmount.x
                                            val fingerContentX = blockStartContentPx + grabOffsetX + dragTranslatePx
                                            dragDropIndex = dropIndexFor(fingerContentX, seg.id)
                                            // 가장자리 auto-pan (확대/스크롤 가능 시). scrollPx 는 live state.
                                            if (maxScroll > 0f) {
                                                val fingerViewportX = fingerContentX - scrollPx
                                                if (fingerViewportX < edgePx)
                                                    scrollPx = (scrollPx - autoPanStepPx).coerceAtLeast(0f)
                                                else if (fingerViewportX > viewportWidthPx - edgePx)
                                                    scrollPx = (scrollPx + autoPanStepPx).coerceAtMost(maxScroll)
                                            }
                                        },
                                        onDragEnd = {
                                            val drop = dragDropIndex
                                            val wasThis = dragSegId == seg.id
                                            dragSegId = null; dragTranslatePx = 0f; dragDropIndex = -1
                                            if (wasThis && drop >= 0) onMoveSegment(seg.id, drop)
                                        },
                                        onDragCancel = {
                                            dragSegId = null; dragTranslatePx = 0f; dragDropIndex = -1
                                        },
                                    )
                                }
                            )
                            .graphicsLayer {
                                // pointerInput 이 graphicsLayer 보다 위(앞)라 translationX 가 pointer 좌표에
                                // 피드백되지 않음 — 시각만 담당. 들린 블록은 손가락 따라 뜨고, 나머지는 reflow.
                                if (isDragging) {
                                    translationX = dragTranslatePx
                                    scaleX = 1.03f
                                    scaleY = 1.12f
                                    alpha = 0.95f
                                    shadowElevation = 12f
                                } else {
                                    translationX = animatedReflowPx
                                }
                            }
                            .then(
                                // 드래그 중엔 블록을 카드로 채워 reflow(밀려남)가 또렷이 보이게 — 들린 블록은
                                // accent, 나머지는 track 색. 그 위에 자기 세그먼트 파형(아래 content)을 얹어
                                // "파형이 블록과 함께 이동". 평소엔 outline 만(뒤 연속 파형 노출).
                                when {
                                    isDragging -> Modifier.background(accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                    dragSegId != null -> Modifier.background(trackColor, RoundedCornerShape(4.dp))
                                    else -> Modifier
                                }
                            )
                            .border(
                                if (isDragging) 2.dp else 1.dp,
                                if (isDragging) accent else segmentColor.copy(alpha = 0.85f),
                                RoundedCornerShape(4.dp),
                            )
                    ) {
                        // 재정렬 드래그 중엔 각 블록이 자기 세그먼트의 파형 슬라이스를 품어 파형이 블록과 함께
                        // 떠오르고/reflow → 연속 파형이 카드만 따라 못 움직여 "점프" 하던 혼란 없이 맥락 유지.
                        // 평소(dragSegId==null)엔 비워 두고 뒤 TimelineWaveformBackground 가 노출됨.
                        // primarySource 가 아닌 세그먼트는 peak 없음 → 단색 카드 그대로.
                        if (dragSegId != null && videoPeaks.isNotEmpty() && primarySourceDurationMs > 0L &&
                            seg.sourceUri == primarySourceUri) {
                            BgmClipWaveform(
                                peaks = videoPeaks,
                                trimStartFrac = (seg.trimStartMs.toFloat() / primarySourceDurationMs)
                                    .coerceIn(0f, 1f),
                                trimEndFrac = (seg.effectiveTrimEndMs.toFloat() / primarySourceDurationMs)
                                    .coerceIn(0f, 1f),
                                // muted(volume 0) 세그먼트는 옅게 — 연속 파형의 volume 반영을 색으로 근사.
                                color = markerColor.copy(alpha = if (seg.volumeScale <= 0f) 0.25f else 0.65f),
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                }

            }

            // 진행 중 음원분리 overlay — 반투명 fill + 중앙 무한 회전 스피너 (사용자 요청). 좁은 구간은
            // 스피너 생략하고 fill 만 (넘침 방지).
            if (totalMs > 0L) {
                val procFillHeight = TimelineBarSpec.WaveformHeight
                processingSeparations.forEach { p ->
                    if (p.endMs <= p.startMs) return@forEach
                    val pStart = (p.startMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    val pEnd = (p.endMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    val pStartDp = totalWidthDp * pStart
                    val pWidthDp = totalWidthDp * (pEnd - pStart).coerceAtLeast(0f)
                    Box(
                        modifier = Modifier
                            .offset(x = pStartDp)
                            .width(pWidthDp)
                            .height(procFillHeight)
                            .align(Alignment.CenterStart)
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pWidthDp >= TimelineBarSpec.ProcessingSpinnerMinWidth) {
                            ProcessingSpinner(color = accent)
                        }
                    }
                }
            }

            // Layer 2 — 파형(WaveformHeight 40dp) 전체를 아우르는 fill + chevron 트림 핸들. BGM 클립 선택과
            // 동일한 시각 언어 — 사용자가 음원분리 구간 선택을 "BGM 편집과 같은 방식" 으로 인지하도록.
            // tap absorber 없음 → 자식 segment.clickable / parent free-interval tap 모두 살림.
            // rangeEndMs <= rangeStartMs (zero-width) = "선택 없음" 상태 → range 시각 모두 숨김 (mode 유지).
            // bgmRangeMode 면 영상 strip 의 fill/handles 안 그림 — BGM lane 에 같은 시각이 노출.
            if (showRange && totalMs > 0L && rangeEndMs > rangeStartMs && !bgmRangeMode) {
                val startFrac = (rangeStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                val endFrac = (rangeEndMs.toFloat() / totalMs).coerceIn(0f, 1f)
                val rangeStartDp = totalWidthDp * startFrac
                val rangeWidthDp = totalWidthDp * (endFrac - startFrac).coerceAtLeast(0f)
                val rangeFillHeight = TimelineBarSpec.WaveformHeight

                // 세그먼트 편집 모드(영상 다듬기, showSegments=isSegmentEditMode)에선 구간 조정 비활성 —
                // 선택은 탭한 세그먼트/구간으로 고정하고, fill 하이라이트는 선택 표시로 유지. 음원분리 구간
                // 선택 모드(isSegmentEditMode=false)에선 종전대로 조정 가능.
                val rangeAdjustable = !showSegments

                var fillBaseStartMs by remember { mutableLongStateOf(0L) }
                var fillAccumPx by remember { mutableFloatStateOf(0f) }
                RangeFillStrip(
                    startDp = rangeStartDp,
                    widthDp = rangeWidthDp,
                    height = rangeFillHeight,
                    accent = accent,
                    fillAlpha = 0.32f,
                    fillModifier = if (!rangeAdjustable) Modifier else
                        Modifier.pointerInput(totalWidthPx, totalMs) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    fillBaseStartMs = currentRangeStart
                                    fillAccumPx = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    fillAccumPx += dragAmount
                                    if (totalWidthPx > 0f && totalMs > 0L) {
                                        val deltaMs = (fillAccumPx / totalWidthPx) * totalMs
                                        onTranslateRange((fillBaseStartMs + deltaMs).toLong())
                                    }
                                }
                            )
                        },
                )

                // 좌/우 chevron 핸들 — BGM 트림 핸들과 동일 디자인. hit zone 은 파형 높이만큼만.
                // hit zone offsetX 는 [0, totalWidthDp - hit] 로 clamp — 풀 선택 시 핸들이 컨테이너
                // 바깥으로 절반 잘려 chevron 이 안 보이던 문제 차단. boundary 닿을 때 chevron 정렬을
                // Center → CenterStart/CenterEnd 로 바꿔, chevron 의 바깥 엣지가 영상 시작·끝 라인에
                // flush. range 가운데 영역에선 기존처럼 boundary 에 centered (straddle).
                // chevron 트림 핸들은 구간 조정 가능할 때만 노출 — 세그먼트 편집 모드에선 숨겨 선택 고정.
                if (rangeAdjustable) {
                    val minGap = TimelineBarSpec.MinRangeGapMs
                    val rangeHandleOffsetY = (playbackRegionHeight - rangeFillHeight) / 2
                    val maxHandleOffsetX = (totalWidthDp - handleHitWidth).coerceAtLeast(0.dp)
                    val atLeftBoundary = rangeStartMs <= 0L
                    val atRightBoundary = rangeEndMs >= totalMs
                    RangeHandle(
                        offsetX = (rangeStartDp - handleHitWidth / 2)
                            .coerceIn(0.dp, maxHandleOffsetX),
                        offsetY = rangeHandleOffsetY,
                        hitWidth = handleHitWidth,
                        hitHeight = rangeFillHeight,
                        visualWidth = handleVisualWidth,
                        handleColor = accent,
                        gripColor = markerColor,
                        gripHeight = rangeFillHeight,
                        chevron = BgmTrimSide.Start,
                        contentAlignment = if (atLeftBoundary) Alignment.CenterStart else Alignment.Center,
                        totalWidthPx = totalWidthPx,
                        totalMs = totalMs,
                        baseMsProvider = { currentRangeStart },
                        clamp = { it.coerceIn(0L, (currentRangeEnd - minGap).coerceAtLeast(0L)) },
                        onChange = onRangeStartChange,
                    )
                    RangeHandle(
                        offsetX = (rangeStartDp + rangeWidthDp - handleHitWidth / 2)
                            .coerceIn(0.dp, maxHandleOffsetX),
                        offsetY = rangeHandleOffsetY,
                        hitWidth = handleHitWidth,
                        hitHeight = rangeFillHeight,
                        visualWidth = handleVisualWidth,
                        handleColor = accent,
                        gripColor = markerColor,
                        gripHeight = rangeFillHeight,
                        chevron = BgmTrimSide.End,
                        contentAlignment = if (atRightBoundary) Alignment.CenterEnd else Alignment.Center,
                        totalWidthPx = totalWidthPx,
                        totalMs = totalMs,
                        baseMsProvider = { currentRangeEnd },
                        clamp = { it.coerceIn((currentRangeStart + minGap).coerceAtMost(totalMs), totalMs) },
                        onChange = onRangeEndChange,
                    )
                }
            }

        }

        // === BGM region — top playback region 바로 아래. 같은 x 좌표계로 lane 막대 렌더 ===
        if (showBgmRegion) {
            val laneWidthDp = totalWidthDp
            val laneWidthPx = totalWidthPx
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = rulerHeight + playbackRegionHeight)
                    .fillMaxWidth()
                    .height(bgmRegionHeight),
            ) {
                // BGM 타깃 모드 + range 선택 상태면 lane 위에 영상 strip 과 같은 디자인의 fill+border 표시.
                // 막대들 아래 layer 라 BGM bar 가 위에 그려져 막대 시각은 보존된다. 핸들은 bgmClips.forEach 뒤에 그려 top-most.
                val bgmRangeStartFrac = if (bgmRangeMode && rangeEndMs > rangeStartMs) {
                    (rangeStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                } else 0f
                val bgmRangeEndFrac = if (bgmRangeMode && rangeEndMs > rangeStartMs) {
                    (rangeEndMs.toFloat() / totalMs).coerceIn(0f, 1f)
                } else 0f
                val bgmFillStartDp = laneWidthDp * bgmRangeStartFrac
                val bgmFillWidthDp = laneWidthDp * (bgmRangeEndFrac - bgmRangeStartFrac).coerceAtLeast(0f)
                // 선택된 BGM 의 lane y offset 계산 — multi-lane 일 때 overlay 가 그 행의 정확한 위치·높이에 맞도록.
                val selectedBgmForOverlay = bgmClips.firstOrNull { it.id == selectedBgmClipId }
                // fill/핸들 Y 는 클립 블록과 동일하게 **표시 lane**(자동 pack 결과)을 써야 한다 — 겹쳐서
                // 아래로 펼쳐진 경우 clip.lane(저장값)과 표시 lane 이 달라 핸들이 다른 줄에 떠 있던 문제.
                val selectedDisplayLane = selectedBgmClipId?.let { bgmLaneByClipId[it] }
                    ?: selectedBgmForOverlay?.lane ?: 0
                val bgmLaneYDp = bgmRowStrideDp * selectedDisplayLane.coerceAtLeast(0)
                // 선택된 BGM 클립을 좌우 드래그하는 **동안** range overlay(fill/핸들)를 클립과 함께 따라오게 하는
                // 로컬 라이브 위치. (선택클립 id, 라이브 startMs). RangeHandle 자기 드래그와 동일한 optimistic
                // 패턴 — VM 라운드트립(매 tick uiState emit → 전체 bar 재구성 "버벅임") 대신 graphicsLayer
                // translationX 로 draw phase 에서만 평행 이동. 이 state 는 graphicsLayer 람다 **안에서만** 읽혀
                // (아래 [bgmRangeLiveTranslateX]) 매 tick 갱신돼도 bar 재구성은 일어나지 않는다. 커밋(드래그 끝)
                // 은 onBgmUpdateStart 가 pendingRange 를 동일 delta 로 한 번에 옮기므로 점프 없이 인계된다.
                var bgmDragLive by remember { mutableStateOf<Pair<String, Long>?>(null) }
                val bgmRangeLiveTranslateX: () -> Float = {
                    val live = bgmDragLive
                    val base = selectedBgmForOverlay?.startMs
                    if (live != null && base != null && live.first == selectedBgmClipId && totalMs > 0L)
                        ((live.second - base).toFloat() / totalMs) * laneWidthPx
                    else 0f
                }
                // (range fill+border 는 BGM 막대 위에 보이도록 bgmClips.forEach 뒤에서 그린다.)
                // BGM 클립 색은 클립 id 해시 기준 안정 슬롯으로 BgmPalette cycle (4색). 같은 통합 매핑을
                // SoundCard chip 도 사용 — 사용자가 timeline 위 블록 색과 deck 카드 색을 매칭. id 기준이라
                // 드래그(위치 변경)·다른 클립 삭제에도 색이 절대 안 변함 (createdAt dense rank 의 삭제 시
                // 색 당김 버그 해결).
                val bgmIndexByClipId: Map<String, Int> = remember(bgmClips) {
                    bgmClips.associate { it.id to com.vibi.cmp.theme.BgmPalette.stableIndexForClipId(it.id) }
                }
                bgmClips.forEach { clip ->
                    // 각 클립을 별도 [BgmClipBlock] 으로 — 드래그/트림 시 recomposition 을 그 한 클립으로
                    // 격리 (부모 UnifiedTimelineBar 전체 recompose 방지). key(clip.id) 로 재정렬 시 override
                    // state 보존.
                    key(clip.id) {
                        BgmClipBlock(
                            clip = clip,
                            isSelected = clip.id == selectedBgmClipId,
                            lane = (bgmLaneByClipId[clip.id] ?: 0).coerceAtLeast(0),
                            paletteIndex = bgmIndexByClipId[clip.id],
                            peaks = bgmPeaksByUri[clip.sourceUri].orEmpty(),
                            label = bgmClipLabelText(clip, bgmClips),
                            totalMs = totalMs,
                            laneWidthDp = laneWidthDp,
                            laneWidthPx = laneWidthPx,
                            bgmRowHeight = bgmRowHeight,
                            bgmRowStrideDp = bgmRowStrideDp,
                            bgmRangeMode = bgmRangeMode,
                            accent = accent,
                            markerColor = markerColor,
                            // 분리 진행 중인 클립은 재선택(→트림)만 잠금 — 위치 드래그는 유지(막지 않음).
                            bgmTapEnabled = bgmTapEnabled && clip.id !in separatingBgmClipIds,
                            bgmDragEnabled = bgmDragEnabled,
                            locked = clip.id in separatingBgmClipIds,
                            onBgmSelectClip = onBgmSelectClip,
                            onBgmUpdateStart = { id, finalMs ->
                                // 커밋 시 라이브 override 해제 → translationX 0 으로 복귀. 동시에 VM 이
                                // pendingRange 를 같은 delta 로 옮기므로(handles base offsetX 갱신) 점프 없음.
                                bgmDragLive = null
                                onBgmUpdateStart(id, finalMs)
                            },
                            // 드래그 중(라이브): VM 으로 보내지 않고 로컬 state 만 갱신 — 선택된 클립일 때만
                            // overlay 가 따라오면 되므로 그 경우만 기록.
                            onBgmDragLive = { id, next ->
                                if (id == selectedBgmClipId) bgmDragLive = id to next
                            },
                            onBgmUpdateTrim = onBgmUpdateTrim,
                        )
                    }
                }
                // range fill + 상·하 border — BGM 막대 **위에** 그려 영상 strip 처럼 반투명하게 보이도록
                // (이전엔 막대 아래라 트랙에 가려 안 보였음). fill drag → 구간 전체 평행 이동.
                if (bgmRangeMode && showRange && rangeEndMs > rangeStartMs) {
                    // 시각 전용 fill — 드래그(이동)는 아래 클립 본체가 받아 클립을 옮긴다. 드래그 중엔
                    // graphicsLayer translationX([bgmRangeLiveTranslateX]) 로 클립과 함께 따라오고(draw phase
                    // 만, 재구성 없음), 커밋 시 pendingRange 가 같은 delta 로 옮겨지며 인계된다. pointerInput
                    // 없음 → 터치는 아래 클립 블록으로 통과(클립 본체 드래그 = 위치 이동).
                    Box(
                        modifier = Modifier
                            .offset(x = bgmFillStartDp, y = bgmLaneYDp)
                            .graphicsLayer { translationX = bgmRangeLiveTranslateX() }
                            .width(bgmFillWidthDp)
                            .height(bgmRowHeight)
                            .background(accent.copy(alpha = 0.32f))
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = bgmFillStartDp, y = bgmLaneYDp)
                            .graphicsLayer { translationX = bgmRangeLiveTranslateX() }
                            .width(bgmFillWidthDp)
                            .height(TimelineBarSpec.RangeBorderThickness)
                            .background(accent)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = bgmFillStartDp, y = bgmLaneYDp + bgmRowHeight - TimelineBarSpec.RangeBorderThickness)
                            .graphicsLayer { translationX = bgmRangeLiveTranslateX() }
                            .width(bgmFillWidthDp)
                            .height(TimelineBarSpec.RangeBorderThickness)
                            .background(accent)
                    )
                }
                // BGM lane 의 range handles — top-most layer 로 BGM bar 위에 표시. hit zone 은 그 행 높이만큼만
                // 잡아 다른 lane 행 / playback strip 의 핸들과 충돌 안 함.
                // 선택된 BGM 의 bounds 안에서만 움직이도록 clamp — BGM 길이 넘어선 range 방지.
                if (bgmRangeMode && showRange && rangeEndMs > rangeStartMs) {
                    val minGap = TimelineBarSpec.MinRangeGapMs
                    val bgmMinMs = selectedBgmForOverlay?.startMs ?: 0L
                    val bgmMaxMs = selectedBgmForOverlay?.let {
                        it.startMs + it.effectiveDurationMs
                    } ?: totalMs
                    RangeHandle(
                        offsetX = bgmFillStartDp - handleHitWidth / 2,
                        offsetY = bgmLaneYDp,
                        hitWidth = handleHitWidth,
                        hitHeight = bgmRowHeight,
                        visualWidth = handleVisualWidth,
                        handleColor = accent,
                        gripColor = markerColor,
                        gripHeight = bgmRowHeight,
                        totalWidthPx = totalWidthPx,
                        totalMs = totalMs,
                        baseMsProvider = { currentRangeStart },
                        clamp = { it.coerceIn(bgmMinMs, (currentRangeEnd - minGap).coerceAtLeast(bgmMinMs)) },
                        onChange = onRangeStartChange,
                        liveTranslateX = bgmRangeLiveTranslateX,
                    )
                    RangeHandle(
                        offsetX = bgmFillStartDp + bgmFillWidthDp - handleHitWidth / 2,
                        offsetY = bgmLaneYDp,
                        hitWidth = handleHitWidth,
                        hitHeight = bgmRowHeight,
                        visualWidth = handleVisualWidth,
                        handleColor = accent,
                        gripColor = markerColor,
                        gripHeight = bgmRowHeight,
                        totalWidthPx = totalWidthPx,
                        totalMs = totalMs,
                        baseMsProvider = { currentRangeEnd },
                        clamp = { it.coerceIn((currentRangeStart + minGap).coerceAtMost(bgmMaxMs), bgmMaxMs) },
                        onChange = onRangeEndChange,
                        liveTranslateX = bgmRangeLiveTranslateX,
                    )
                }
            }
        }

        // === 재생 헤드 시각 line — ruler 아래부터 시작해 BGM 까지 관통. drag 안 받음 ===
        // marker visual 은 ruler 영역 (라벨/tick) 과 겹치지 않게 y 오프셋에 rulerHeight 추가.
        if (totalMs > 0L) {
            val frac = (playbackPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val visualX = totalWidthDp * frac - TimelineBarSpec.GripWidth / 2
            val topInset = rulerHeight + TimelineBarSpec.PlaybackMarkerVerticalInset / 2
            val markerHeight = (playbackRegionHeight + bgmRegionHeight -
                TimelineBarSpec.PlaybackMarkerVerticalInset).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = visualX, y = topInset)
                    .width(TimelineBarSpec.GripWidth)
                    .height(markerHeight)
                    .background(markerColor)
            )
        }

        // 재생 헤드 drag hit zone — playback + BGM region 전체 높이를 커버. BGM 위에서도 scrub 가능.
        // 이전엔 playback Box 안에만 있어 BGM 레인 위에선 잡히지 않았다 (사용자 보고).
        // 마지막 child 로 두어 z-order 최상단, 그래야 BGM clip drag 와 겹쳐도 column 안에선 scrub 가 우선.
        if (totalMs > 0L) {
            val frac = (playbackPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val hitWidth = TimelineBarSpec.PlaybackHitWidth
            val visualX = totalWidthDp * frac - hitWidth / 2
            val currentPosMs by rememberUpdatedState(playbackPositionMs)
            var basePosMs by remember { mutableLongStateOf(0L) }
            var accumPx by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = visualX)
                    .width(hitWidth)
                    .height(totalHeight)
                    .pointerInput(totalWidthPx, totalMs) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                basePosMs = currentPosMs
                                accumPx = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                accumPx += dragAmount
                                if (totalWidthPx > 0f && totalMs > 0L) {
                                    val deltaMs = (accumPx / totalWidthPx) * totalMs
                                    val newMs = (basePosMs + deltaMs).toLong().coerceIn(0L, totalMs)
                                    onScrub(newMs)
                                }
                            }
                        )
                    }
            )
        }

        // BGM lane 수동 조절 pill 은 폐기됨 — lane 은 선택 시 자동 pack 으로 결정.
        }
        }
    }
}

/**
 * 재생바 strip 위에 directive 구간을 가로 띠로 깔고 탭 핸들러 부착. 파형 모드(barColor=null)에선
 * invisible overlay 로 동작해 시각은 [TimelineWaveformBackground] 의 컬러 분기에 위임. fallback
 * 모드(barColor=directiveColor)에선 막대 자체로 분리 영역을 표시.
 */
@Composable
private fun BoxScope.DirectiveOverlayRow(
    directives: List<com.vibi.shared.domain.model.SeparationDirective>,
    totalMs: Long,
    height: androidx.compose.ui.unit.Dp,
    barColor: Color?,
    tapEnabled: Boolean,
    onDirectiveTap: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(height),
    ) {
        var prevEnd = 0L
        directives.forEach { directive ->
            val gap = (directive.rangeStartMs - prevEnd).coerceAtLeast(0L)
            if (gap > 0L) Spacer(Modifier.weight(gap.toFloat()))
            val w = (directive.rangeEndMs - directive.rangeStartMs).coerceAtLeast(1L)
            val boxMod = Modifier
                .weight(w.toFloat())
                .fillMaxHeight()
                .let { if (barColor != null) it.background(barColor) else it }
                .let { if (tapEnabled) it.clickable { onDirectiveTap(directive.id) } else it }
            Box(modifier = boxMod)
            prevEnd = directive.rangeEndMs
        }
        val tail = (totalMs - prevEnd).coerceAtLeast(0L)
        if (tail > 0L) Spacer(Modifier.weight(tail.toFloat()))
    }
}

/**
 * 재생바 strip 배경에 영상 audio 파형을 그린다. directive 구간에 속하는 막대는 highlightBarColor 로,
 * 그 외는 defaultBarColor 로 칠해 음원분리 적용 영역을 시각 구분.
 *
 * 각 막대의 시각 높이에 다음을 모두 반영:
 *  - **Segment trim/speed**: timeline 위치 → segment 내부 sourceMs 역매핑 후 peak lookup. 한 timeline 위치가
 *    어느 source frame 을 가리키는지 정확히 계산해 trim 잘라낸 영역은 노출 안 되고 speed=2x 면 같은 시간 폭에
 *    두 배 분량의 source peak 가 압축돼 보임.
 *  - **Segment 볼륨**: 해당 segment 의 volumeScale 을 peak amplitude 에 곱한다.
 *  - **Directive stem 볼륨**: directive 구간 안에서는 (원음 mute 여부 + 선택된 stem 중 최대 volume) 합산
 *    스칼라를 peak 에 곱한다 — voice 만 1.0 + 원음 mute → 분리된 voice 만 들리는 상태가 1.0 으로 보존.
 *
 * Peak 원본은 [primarySourceUri] 와 매칭되는 segment 에만 적용 (다중 source 영상의 다른 source 영역은 0).
 *
 * - 파형 amplitude 는 sqrt 보정해 작은 peak 도 살짝 보이게 — WaveformPlayBar 와 같은 톤.
 * - tap/drag 는 본 composable 안에서 처리 안 함. 호출자(UnifiedTimelineBar) 가 invisible overlay 로 처리.
 */
@Composable
private fun TimelineWaveformBackground(
    sourcePeaks: List<Float>,
    segments: List<com.vibi.shared.domain.model.Segment>,
    primarySourceUri: String,
    sourceDurationMs: Long,
    totalMs: Long,
    directives: List<com.vibi.shared.domain.model.SeparationDirective>,
    stemPeaksByUrl: Map<String, List<Float>>,
    defaultBarColor: Color,
    highlightBarColor: Color,
    trackBg: Color,
    modifier: Modifier = Modifier,
) {
    // directive 별 effective scale + stem 기여도. directive 영역의 bar 높이는 선택된 모든 stem 의 peak 를
    // volume 으로 가중 후 에너지 합산 (sqrt(Σ(p_i·v_i)²)) — 화자+배경 둘 다 켜면 mix shape 이 보임.
    // muteOriginalSegmentAudio=false 면 source peak 도 추가 항으로 합산.
    val tokens = LocalVibiColors.current
    // stemPeaksByUrl 는 SnapshotStateMap — peak 가 async 로 채워져도 맵 인스턴스 identity 는 안 바뀌어
    // remember(stemPeaksByUrl) 의 key equals 가 변하지 않아 overlay 가 재계산되지 않는다. 활성 stem 별
    // peak size 핑거프린트를 key 로 써서, peak 가 로드되는 순간(0→N) overlay 가 재계산되도록 한다.
    // (종전엔 expand 토글이 우연히 recompute 를 유발해서만 색이 떴고 — expand 게이팅 제거 후 그 트리거가
    // 사라져 펼쳐도/닫아도 색이 안 뜨던 회귀 fix. fingerprint 의 stemPeaksByUrl 읽기는 snapshot-tracked
    // 라 peak 로드 시 recomposition 도 함께 유발.)
    val stemPeaksFingerprint = directives.flatMap { d ->
        d.selections.mapNotNull { it.audioUrl?.takeIf { u -> u.isNotBlank() } }
    }.distinct().map { it to (stemPeaksByUrl[it]?.size ?: 0) }
    val directiveOverlays = remember(directives, stemPeaksFingerprint, tokens) {
        // 같은 stem audio URL 을 공유하는 모든 piece (split 결과) 중 (sourceOffset + duration) 의 max
        // 를 stem audio 전체 길이로 추정. waveform peak idx 매핑이 piece 길이가 아니라 stem 전체 길이
        // 기준이어야 split 뒷 piece (sourceOffset > 0) 가 stem 의 올바른 구간을 보여줌.
        val stemTotalDurByUrl: Map<String, Long> = buildMap {
            for (d in directives) {
                val pieceEnd = d.sourceOffsetMs + d.durationMs
                for (sel in d.selections) {
                    val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: continue
                    val prev = get(url) ?: 0L
                    if (pieceEnd > prev) put(url, pieceEnd)
                }
            }
        }
        directives.map { d ->
            val originalContrib = if (d.muteOriginalSegmentAudio) 0f else 1f
            val selectedStems = d.selections.filter { it.selected }
            val stemContrib = selectedStems.maxOfOrNull { it.volume } ?: 0f
            val contributions = selectedStems.mapNotNull { sel ->
                val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val peaks = stemPeaksByUrl[url] ?: return@mapNotNull null
                if (peaks.isEmpty()) null
                else {
                    val isSpeaker = com.vibi.shared.domain.model.Stem.kindFromId(sel.stemId) ==
                        com.vibi.shared.domain.model.StemKind.SPEAKER
                    StemPeakContribution(
                        peaks = peaks,
                        volume = sel.volume,
                        totalDurMs = (stemTotalDurByUrl[url] ?: d.durationMs).coerceAtLeast(1L),
                        // 분리된 음원은 펼침 여부와 무관하게 항상 화자별 색 — 닫혀 있어도 파형에서 화자 구분.
                        color = SpeakerPalette.stemColor(sel.stemId, tokens, fallback = highlightBarColor),
                        // dominant 색 후보는 speaker 만 — background 가 peak 가 항상 커서 화자 색을 묻는 사고 방지.
                        colorCandidate = isSpeaker,
                    )
                }
            }
            DirectiveScaleOverlay(
                startMs = d.rangeStartMs,
                endMs = d.rangeEndMs,
                scale = (originalContrib + stemContrib).coerceIn(0f, 1.5f),
                stemContributions = contributions,
                includeOriginal = !d.muteOriginalSegmentAudio,
                sourceOffsetMs = d.sourceOffsetMs,
            )
        }
    }
    // segment 누적 (acc, segment) — timelineMs → segment 역검색을 N=240 번 돌릴 때 O(N×S) 보다 빠르도록 미리 펼침.
    val segmentSpans = remember(segments) {
        var acc = 0L
        segments.map { seg ->
            val span = SegmentSpan(acc, acc + seg.effectiveDurationMs, seg)
            acc += seg.effectiveDurationMs
            span
        }
    }
    Box(
        modifier = modifier
            // 덱 카드와 동일 라운드(VibiShape.lg) + panelBgSoft 배경. 테두리는 흰 패널 위에서 따로 놀아 제거.
            .clip(VibiShape.lg)
            .background(trackBg),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val peakCount = sourcePeaks.size
            if (peakCount == 0 || totalMs <= 0L) return@Canvas
            // 시각 막대 수는 canvas width 기반 (1dp bar + 1dp gap — hair-thin). peaks 인덱싱은
            // 절대 fraction 으로 유지 — segment trim/speed 매핑이 sourcePeaks.size 에 의존.
            val barPx = 1.dp.toPx()
            val gapPx = 1.dp.toPx()
            val slotPx = barPx + gapPx
            val barCount = (size.width / slotPx).toInt().coerceAtLeast(1)
            val cornerR = CornerRadius(barPx / 2f, barPx / 2f)
            val cy = size.height / 2f
            val maxHalfHeight = size.height / 2f - 3f
            for (i in 0 until barCount) {
                val timelineMs = ((i + 0.5f) / barCount * totalMs).toLong()

                // segment 역매핑: timelineMs 가 속한 segment 의 volumeScale + sourceMs 위치 → peak.
                var segVolume = 1f
                var sourcePeak = 0f
                for (idx in segmentSpans.indices) {
                    val span = segmentSpans[idx]
                    val inside = if (idx == segmentSpans.lastIndex) {
                        timelineMs in span.startMs..span.endMs
                    } else {
                        timelineMs in span.startMs until span.endMs
                    }
                    if (inside) {
                        segVolume = span.segment.volumeScale
                        if (span.segment.sourceUri == primarySourceUri && sourceDurationMs > 0L) {
                            val rel = (timelineMs - span.startMs).coerceAtLeast(0L)
                            val sourceMs = (rel * span.segment.speedScale).toLong() + span.segment.trimStartMs
                            val peakIdx = ((sourceMs.toDouble() / sourceDurationMs) * peakCount).toInt()
                                .coerceIn(0, peakCount - 1)
                            sourcePeak = sourcePeaks[peakIdx]
                        }
                        break
                    }
                }

                // directive overlay scale + 시각 컬러 분기 결정. directive 안일 때 선택된 모든 stem 의 peak 를
                // volume 가중 후 에너지 합산 (sqrt(Σ(p_i·v_i)²)) → 화자+배경 둘 다 켜면 합쳐진 mix 가 보임.
                // mute 안 한 경우 source peak 도 추가 항으로 더함. stem peaks 없으면 기존 source × scale fallback.
                // 또 dominant contribution(peak×volume 최대) 의 색을 bar 색으로 채택해 화자별 시각 분리.
                var directiveScale = 1f
                var inDirective = false
                var directivePeak: Float? = null
                var directiveBarColor: Color? = null
                for (ov in directiveOverlays) {
                    if (timelineMs in ov.startMs..ov.endMs) {
                        directiveScale = ov.scale
                        inDirective = true
                        if (ov.stemContributions.isNotEmpty()) {
                            val rel = (timelineMs - ov.startMs).coerceAtLeast(0L) + ov.sourceOffsetMs
                            var sumSq = 0.0
                            var dominantWeight = 0f
                            for (c in ov.stemContributions) {
                                val idx = ((rel.toDouble() / c.totalDurMs) * c.peaks.size).toInt()
                                    .coerceIn(0, c.peaks.size - 1)
                                val v = c.peaks[idx] * c.volume
                                sumSq += v * v
                                // dominant 색은 colorCandidate (=speaker) 들 중에서만 선택.
                                // background 가 peak 항상 커서 화자 색 묻는 사고 차단.
                                if (c.colorCandidate && v > dominantWeight) {
                                    dominantWeight = v
                                    directiveBarColor = c.color
                                }
                            }
                            if (ov.includeOriginal) {
                                val v = sourcePeak * segVolume
                                sumSq += v * v
                            }
                            directivePeak = kotlin.math.sqrt(sumSq).toFloat()
                        }
                        break
                    }
                }

                val effectivePeak = if (directivePeak != null) {
                    directivePeak.coerceIn(0f, 1f)
                } else {
                    (sourcePeak * segVolume * directiveScale).coerceIn(0f, 1f)
                }
                val h = maxOf(barPx / 2f, kotlin.math.sqrt(effectivePeak) * maxHalfHeight)
                val x = slotPx * i
                drawRoundRect(
                    color = when {
                        !inDirective -> defaultBarColor
                        directiveBarColor != null -> directiveBarColor
                        else -> highlightBarColor
                    },
                    topLeft = Offset(x, cy - h),
                    size = Size(barPx, h * 2f),
                    cornerRadius = cornerR,
                )
            }
        }
    }
}

private data class DirectiveScaleOverlay(
    val startMs: Long,
    val endMs: Long,
    val scale: Float,
    val stemContributions: List<StemPeakContribution> = emptyList(),
    val includeOriginal: Boolean = true,
    val sourceOffsetMs: Long = 0L,
)

/** 현재 하단 액션바의 표시 대상 — 단일 진실. TimelineScreen 의 padding 계산 + 바 내부 분기 양쪽 공유. */
private sealed interface BottomActionTarget {
    data object None : BottomActionTarget
    data object Video : BottomActionTarget
    data class Bgm(val clipId: String) : BottomActionTarget
}

private fun com.vibi.shared.ui.timeline.TimelineUiState.bottomActionTarget(): BottomActionTarget {
    if (isSegmentEditMode) {
        // range 가 비면 편집 의미 없음 — 바 숨김. (VM 의 onClearRangeSelection 이 모드 자체도 종료하지만
        // 다른 경로로 빈 상태가 들어와도 동일 동작 보장.)
        return if (pendingRangeEndMs > pendingRangeStartMs) BottomActionTarget.Video
        else BottomActionTarget.None
    }
    // BGM clip 이 선택돼 있으면 BGM 편집 토글 노출 — 볼륨/속도/삭제. 같은 [EditActionsPanel] 슬롯을
    // 재사용해 영상 편집 토글과 thumb 도달 거리·인터랙션이 동일.
    selectedBgmClipId?.let { return BottomActionTarget.Bgm(it) }
    return BottomActionTarget.None
}

/**
 * 통합 하단 액션바 — 영상 다듬기 모드면 영상 range 편집 액션, BGM 선택 상태면 BGM 편집 액션을
 * 같은 [EditActionsPanel] 슬롯으로 렌더. 둘 다 아니면 미렌더. 사용자가 timeline 위에서 영상/BGM 을
 * 탭해 진입 → 하단바로 thumb 도달 거리에서 조작.
 */
@Composable
private fun TimelineActionBottomBar(
    target: BottomActionTarget,
    state: com.vibi.shared.ui.timeline.TimelineUiState,
    viewModel: com.vibi.shared.ui.timeline.TimelineViewModel,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = target !is BottomActionTarget.None,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        ) { it },
        exit = androidx.compose.animation.slideOutVertically(
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
        ) { it },
    ) {
        // 회색 backdrop 제거 — 흰 EditActionsPanel(패널 자체 배경) 만 노출. in-flow 라 덱을 밀어올림.
        // navbar 여백은 부모 메인 Column 이 이미 처리하므로 여기선 없음. 좌우 여백 둔 floating 카드.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VibiSpacing.base, vertical = VibiSpacing.xs),
        ) {
            when (target) {
                is BottomActionTarget.None -> Unit
                is BottomActionTarget.Video -> com.vibi.cmp.ui.timeline.sounddeck.EditActionsPanel(
                    title = "",
                    speed = state.pendingRangeSpeed,
                    onSpeedChange = { viewModel.onUpdatePendingRangeSpeed(it) },
                    onApplySpeed = { viewModel.onApplyRangeSpeed(it) },
                    secondaryActionIcon = Icons.Filled.ContentCopy,
                    secondaryActionContentDescription = "Duplicate",
                    onSecondaryAction = { viewModel.onDuplicateRange() },
                    onDelete = { viewModel.onDeleteRange() },
                    onCancel = null,
                )
                is BottomActionTarget.Bgm -> {
                    // 선택된 BGM 의 현 값에서 슬라이더 시작 — 외부 (deck 카드 등) 에서 같은 clip 의
                    // volume/speed 가 바뀌어도 패널이 그 값을 반영. clip 이 사라지면 (삭제) 패널 자체가
                    // bottomActionTarget None 으로 떨어져 사라짐 — 본 분기는 안전하게 firstOrNull 가드.
                    val clip = state.bgmClips.firstOrNull { it.id == target.clipId }
                    if (clip != null) {
                        // 배경음 제거 토글 라벨/상태 — 진행 중이면 "처리 중..." 비활성, 캐시된 voice-only
                        // 활성 시 "원래대로", 그 외엔 "배경음 제거". 5번째 슬롯(tertiary) 으로 노출.
                        val removalProgress = state.bgmBackgroundRemovalProgress[clip.id]
                        val bgRemovalLabel: String
                        val bgRemovalEnabled: Boolean
                        when {
                            removalProgress is com.vibi.shared.ui.timeline.BgmRemovalProgress.Processing -> {
                                bgRemovalLabel = "Processing"
                                bgRemovalEnabled = false
                            }
                            clip.isBackgroundRemoved -> {
                                bgRemovalLabel = "Restore"
                                bgRemovalEnabled = true
                            }
                            else -> {
                                bgRemovalLabel = "Isolate"
                                bgRemovalEnabled = true
                            }
                        }
                        com.vibi.cmp.ui.timeline.sounddeck.EditActionsPanel(
                            title = "",
                            // 영상 다듬기와 동일한 pending-range/Apply 패턴 — 선택 구간에만 적용(클립 분할).
                            // 볼륨/속도는 pendingRange* 값을 슬라이더로 잡고 Apply 로 onApplyRange* 호출 →
                            // VM 의 clip-local 분할 헬퍼가 가운데 조각만 편집. 복제/삭제도 구간 기준.
                            volume = state.pendingRangeVolume,
                            speed = state.pendingRangeSpeed,
                            onVolumeChange = { viewModel.onUpdatePendingRangeVolume(it) },
                            onSpeedChange = { viewModel.onUpdatePendingRangeSpeed(it) },
                            onApplyVolume = { viewModel.onApplyRangeVolume(it) },
                            onApplySpeed = { viewModel.onApplyRangeSpeed(it) },
                            // secondary = 선택 구간 복제
                            secondaryActionIcon = Icons.Filled.ContentCopy,
                            secondaryActionContentDescription = "Duplicate",
                            onSecondaryAction = { viewModel.onDuplicateRange() },
                            // delete = 선택 구간 삭제
                            onDelete = { viewModel.onDeleteRange() },
                            // tertiary = 배경음 제거 ↔ 원래대로 토글 (BGM 고유 — 유지)
                            tertiaryActionLabel = bgRemovalLabel,
                            onTertiaryAction = { viewModel.onToggleBgmBackgroundRemoval(clip.id) },
                            tertiaryActionEnabled = bgRemovalEnabled,
                            onCancel = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * CapCut 식 시간 눈금자 — 줌 레벨에 따라 nice-interval (0.5/1/2/5/10/30/60/...) 로 major tick + 라벨,
 * 그 사이를 5분할한 minor tick. 가로 스크롤 콘텐츠의 폭 ([contentWidthDp]) 안에서 좌표 계산.
 */
@Composable
private fun BoxScope.TimelineRuler(
    totalMs: Long,
    contentWidthDp: androidx.compose.ui.unit.Dp,
    contentWidthPx: Float,
    tickColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    if (totalMs <= 0L || contentWidthPx <= 0f) {
        Box(modifier = modifier)
        return
    }
    val density = LocalDensity.current
    val targetPx = with(density) { TimelineBarSpec.RulerLabelTargetSpacing.toPx() }
    val pxPerSec = contentWidthPx / (totalMs / 1000.0).toFloat()
    val desiredSec = (targetPx / pxPerSec).toDouble()
    val niceIntervals = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0)
    val majorIntervalSec = niceIntervals.firstOrNull { it >= desiredSec } ?: niceIntervals.last()
    val majorIntervalMs = (majorIntervalSec * 1000).toLong().coerceAtLeast(1L)
    val minorIntervalMs = (majorIntervalMs / 5).coerceAtLeast(1L)
    val majorTickPx = with(density) { TimelineBarSpec.RulerMajorTickHeight.toPx() }
    val minorTickPx = with(density) { TimelineBarSpec.RulerMinorTickHeight.toPx() }
    val tickStrokePx = with(density) { 1.dp.toPx() }

    Box(modifier = modifier) {
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            if (w <= 0f) return@Canvas
            // minor ticks 먼저 — 옅은 색.
            var ms = 0L
            while (ms <= totalMs) {
                val x = (ms.toFloat() / totalMs) * w
                drawRect(
                    color = tickColor.copy(alpha = 0.5f),
                    topLeft = Offset(x - tickStrokePx / 2f, 0f),
                    size = Size(tickStrokePx, minorTickPx),
                )
                ms += minorIntervalMs
            }
            // major ticks — minor 위에 덮어 그리기.
            ms = 0L
            while (ms <= totalMs) {
                val x = (ms.toFloat() / totalMs) * w
                drawRect(
                    color = tickColor,
                    topLeft = Offset(x - tickStrokePx / 2f, 0f),
                    size = Size(tickStrokePx, majorTickPx),
                )
                ms += majorIntervalMs
            }
        }
        // 라벨 — major tick 위치마다 Text. 마지막 (ms == totalMs) 은 우측 overflow 방지 위해 생략.
        var labelMs = 0L
        while (labelMs < totalMs) {
            val frac = labelMs.toFloat() / totalMs
            val xDp = contentWidthDp * frac
            Text(
                text = formatRulerLabel(labelMs, majorIntervalSec),
                color = labelColor,
                fontSize = 9.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = xDp + 2.dp),
            )
            labelMs += majorIntervalMs
        }
    }
}

/**
 * 음원 추가 버튼 — 음표(MusicNote)에 작은 + 배지를 얹어 "오디오 추가"를 표현. 사운드덱 Audio 헤더
 * 우측에 컴팩트하게 배치. 텍스트 글리프 대신 벡터 아이콘 합성. 배지 바탕은 [accent], + 글리프는
 * [backgroundPrimary] — 두 토큰이 라이트·다크에서 서로 반전이라 어느 테마에서도 + 가 대비된다.
 */
@Composable
private fun AddAudioButton(enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalVibiColors.current
    // 완전한 검정 대신 살짝 투명을 섞어 톤을 누그러뜨림(음표 tint · 배지 바탕 공통).
    val ink = tokens.onBackgroundPrimary.copy(alpha = 0.88f)
    Box(
        modifier = Modifier
            .size(VibiSpacing.touchMin)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 음표 + 작은 + 배지 — 26dp 시각 클러스터로 모음. 음표는 좌하단, 배지는 우상단 코너에 둬
        // 서로 떨어지게(이전엔 음표 stem 과 + 가 겹쳤음).
        Box(modifier = Modifier.size(26.dp)) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Add audio",
                tint = ink,
                modifier = Modifier.align(Alignment.BottomStart).size(19.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(tokens.accent.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = tokens.backgroundPrimary,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}

/**
 * 파형 strip 폭만큼 채우는 fill. [fillModifier] 로 fill Box 에 gesture/interaction 부착
 * (구간 선택은 drag 평행 이동). 상/하 border 는 두지 않음 — fill 하이라이트만으로 선택 표시.
 */
@Composable
private fun BoxScope.RangeFillStrip(
    startDp: androidx.compose.ui.unit.Dp,
    widthDp: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    accent: Color,
    fillAlpha: Float,
    fillModifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .offset(x = startDp)
            .width(widthDp)
            .height(height)
            .align(Alignment.CenterStart)
            .background(accent.copy(alpha = fillAlpha))
            .then(fillModifier)
    )
}

private data class StemPeakContribution(
    val peaks: List<Float>,
    val volume: Float,
    val totalDurMs: Long,
    /** 시각 색 — SPEAKER 는 [SpeakerPalette], BACKGROUND/VOICE_ALL 은 기존 highlight 색. */
    val color: Color,
    /** dominant 색 픽에서 후보 여부. BACKGROUND 가 peak 가 항상 커서 화자 색을 묻는 것 방지 위해 false. */
    val colorCandidate: Boolean,
)
private data class SegmentSpan(
    val startMs: Long,
    val endMs: Long,
    val segment: com.vibi.shared.domain.model.Segment,
)

/**
 * BGM 클립 액션 시트 — 타임라인 lane 의 막대 탭 시 ModalBottomSheet 로 표시.
 *  - 4 액션 버튼 (음원분리 / 미리듣기 / 삽입 / 삭제) + 볼륨/속도 슬라이더 항상 노출.
 *  - "삽입" = 현재 playhead 로 startMs 재고정.
 *  - "미리듣기" = TODO (별도 BGM playback 인프라 필요 — stub).
 */
/**
 * 헤더의 "내보내기" 아이콘 탭 시 노출. 저장(갤러리)·공유 두 옵션. 시트 자체는 단순 메뉴 — 실제 progress
 * 는 헤더 아이콘이 percent 텍스트로 그대로 표시하므로 별도 progress UI 없음.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExportOptionsSheet(
    saved: Boolean,
    shared: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.panelBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VibiSpacing.base, vertical = VibiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
        ) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = VibiShape.lg,
                onClick = onSave,
            ) {
                // Row.fillMaxWidth + Arrangement.Start — OutlinedButton 의 default content slot 이
                // 중앙 정렬이라 명시적으로 좌측 정렬.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Save,
                        contentDescription = null,
                        tint = tokens.onBackgroundPrimary,
                    )
                    Spacer(Modifier.width(VibiSpacing.xs))
                    Text("Save", style = typo.bodySm)
                    // 현재 편집 상태가 이미 갤러리에 저장됐음을 표시. 편집하면 VM 이 해제(체크 사라짐).
                    if (saved) {
                        Spacer(Modifier.weight(1f))
                        ExportDoneCheck(tokens.accent)
                    }
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = VibiShape.lg,
                onClick = onShare,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Share,
                        contentDescription = null,
                        tint = tokens.onBackgroundPrimary,
                    )
                    Spacer(Modifier.width(VibiSpacing.xs))
                    Text("Share", style = typo.bodySm)
                    if (shared) {
                        Spacer(Modifier.weight(1f))
                        ExportDoneCheck(tokens.accent)
                    }
                }
            }
            Spacer(Modifier.height(VibiSpacing.sm))
        }
    }
}

/** 저장/공유 완료 체크 마크 — "현재 편집 상태가 이미 export 됨" 표시. 편집으로 출력이 바뀌면 숨김. */
@Composable
private fun ExportDoneCheck(tint: Color) {
    Icon(
        imageVector = androidx.compose.material.icons.Icons.Filled.CheckCircle,
        contentDescription = "완료됨",
        tint = tint,
    )
}

// BgmActionSheet 폐기 — 선택된 BGM 의 볼륨/속도/배경분리/삭제 액션은 SoundDeck 카드 가 담당하고,
// 트림은 타임라인 위 좌·우 핸들이 담당한다. 화면 하단에서 sheet 가 올라오는 동선이 새 인터랙션과
// 중복돼 사용자가 같은 작업을 두 군데서 보게 되던 문제를 해소.
//
// 함께 폐기된 helper: InlineSliderRow / LabeledActionButton / RoundIconButton — 모두 BgmActionSheet
// 내부 전용이었다.

/**
 * 타임라인 BGM 레인 블록 안 mini 파형 + (구) BgmActionSheet 미리듣기 파형이 공유하던 모듈 레벨 캐시.
 * sourceUri 키로 1회 추출 후 영속 (프로세스 lifetime) — 같은 클립 재진입 시 즉시 표시. iOS 만 동작
 * (extractAudioPeaks android stub).
 */
private val bgmPeaksCache = mutableMapOf<String, List<Float>>()

/**
 * UnifiedTimelineBar 재생바 배경 파형용 캐시. videoUri 키로 1회 추출 후 영속 — 화면 재진입 / state
 * 갱신 시 재추출 없이 즉시 표시. iOS 만 동작(extractAudioPeaks android stub).
 */
private val videoPeaksCache = mutableMapOf<String, List<Float>>()

/**
 * Timeline 의 directive 영역 파형용 — stem audioUrl → peaks. 모듈 레벨이라 화면 재진입에도 보존.
 */
private val stemPeaksCacheTimeline = mutableMapOf<String, List<Float>>()

/**
 * 양쪽 range 핸들 공통 — accumulator 패턴으로 drag delta 추적, clamp 후 onChange 호출.
 * baseMsProvider 는 drag start 시점의 기준 ms (rangeStart 또는 rangeEnd) 를 lazily 반환.
 * clamp 는 새 ms 를 모드별 허용 범위로 변환.
 */
@Composable
private fun RangeHandle(
    offsetX: androidx.compose.ui.unit.Dp,
    hitWidth: androidx.compose.ui.unit.Dp,
    hitHeight: androidx.compose.ui.unit.Dp,
    visualWidth: androidx.compose.ui.unit.Dp,
    handleColor: Color,
    gripColor: Color,
    gripHeight: androidx.compose.ui.unit.Dp,
    totalWidthPx: Float,
    totalMs: Long,
    baseMsProvider: () -> Long,
    clamp: (Long) -> Long,
    onChange: (Long) -> Unit,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp,
    /**
     * null 이면 기존 visual (얇은 막대 + 가운데 grip 선) — segment edit 등 좁은 strip 에 쓰임.
     * Start/End 면 CapCut 스타일 chevron 막대 (BgmTrimHandle 와 동일 시각) — 음원분리 range 가 파형 높이를
     * 가득 채우면서 트림 핸들도 같은 언어로 보이도록.
     */
    chevron: BgmTrimSide? = null,
    /**
     * hit zone 안에서 chevron 시각의 정렬. 기본 Center — chevron 이 boundary 에 straddle (절반 안/절반 밖).
     * boundary 가 timeline 끝에 닿아 hit zone 이 clamp 된 경우 호출부에서 CenterStart/CenterEnd 로 넘겨
     * chevron 이 영상 끝 라인에 flush 하게.
     */
    contentAlignment: Alignment = Alignment.Center,
    /**
     * 외부(예: BGM 클립 본체 드래그)에서 핸들을 라이브로 평행 이동시키는 px translate. 람다 안에서만 읽혀
     * graphicsLayer(draw phase)로만 반영 → 재구성 없이 따라옴. 기본 0(영향 없음). 핸들 자체 드래그 중엔
     * 외부 드래그가 없어 0 이므로 [renderOffsetX] optimistic 과 충돌하지 않는다.
     */
    liveTranslateX: () -> Float = { 0f },
) {
    // hitHeight 는 명시 파라미터 — UnifiedTimelineBar 가 BGM region 까지 컨테이너가 늘어난 뒤에도
    // range 핸들 hit zone 이 BGM lane drag 와 충돌하지 않게 top playback region 만큼만 잡도록 한다.
    var baseMs by remember { mutableLongStateOf(0L) }
    var accumPx by remember { mutableFloatStateOf(0f) }
    // 드래그 중에는 핸들 위치를 전역 state(offsetX) 라운드트립이 아니라 로컬 optimistic offset 으로 렌더 —
    // 매 프레임 onChange→VM→uiState→거대한 UnifiedTimelineBar 재구성을 기다리면 프레임 드랍("지지직")이
    // 나기 때문. 드래그 끝나면 offsetX(=커밋값) 로 복귀 — onChange 가 동일 clamp 값을 보냈으니 점프 없음.
    val density = LocalDensity.current
    val currentOffsetX by rememberUpdatedState(offsetX)
    var dragging by remember { mutableStateOf(false) }
    var baseOffsetX by remember { mutableStateOf(0.dp) }
    var visualDeltaDp by remember { mutableStateOf(0.dp) }
    val renderOffsetX = if (dragging) baseOffsetX + visualDeltaDp else offsetX
    Box(
        modifier = Modifier
            .offset(x = renderOffsetX, y = offsetY)
            .graphicsLayer { translationX = liveTranslateX() }
            .width(hitWidth)
            .height(hitHeight)
            .pointerInput(totalWidthPx, totalMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        baseMs = baseMsProvider()
                        accumPx = 0f
                        baseOffsetX = currentOffsetX
                        visualDeltaDp = 0.dp
                        dragging = true
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { _, dragAmount ->
                        accumPx += dragAmount
                        if (totalWidthPx > 0f && totalMs > 0L) {
                            val deltaMs = (accumPx / totalWidthPx) * totalMs
                            val clamped = clamp((baseMs + deltaMs).toLong())
                            onChange(clamped)
                            // clamp 적용된 ms 를 px→dp 로 환산해 핸들을 그 위치로 (clamp 경계에서 손가락보다
                            // 안 넘어가게 — 경계 도달 시 핸들이 멈춰 보임).
                            val clampedDeltaPx = ((clamped - baseMs).toFloat() / totalMs) * totalWidthPx
                            visualDeltaDp = with(density) { clampedDeltaPx.toDp() }
                        }
                    }
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
        contentAlignment = contentAlignment,
    ) {
        if (chevron != null) {
            // contentAlignment 가 boundary 쪽 (CenterStart/CenterEnd) 으로 강제된 케이스 = chevron 외곽이
            // timeline 끝 라인에 flush — 그 쪽 모서리를 직각으로 만들어 fill/top·bottom bar 와 시각 단차 차단.
            val outerSharp = (chevron == BgmTrimSide.Start && contentAlignment == Alignment.CenterStart) ||
                (chevron == BgmTrimSide.End && contentAlignment == Alignment.CenterEnd)
            ChevronThumb(
                side = chevron,
                height = gripHeight,
                handleColor = handleColor,
                gripColor = gripColor,
                outerCornerSharp = outerSharp,
            )
        } else {
            // 기존 시각 — 얇은 막대 두 개. hit zone 은 fillMaxHeight 유지.
            Box(
                Modifier
                    .width(visualWidth)
                    .height(gripHeight)
                    .clip(RoundedCornerShape(TimelineBarSpec.HandleCornerRadius))
                    .background(handleColor)
            )
            Box(
                Modifier
                    .width(TimelineBarSpec.GripWidth)
                    .height(gripHeight)
                    .background(gripColor)
            )
        }
    }
}

/**
 * 단일 BGM/녹음 클립 블록 — 본체 막대 + 내부 mini 파형 + 라벨 + (선택 시) 트림 고스트·좌우 트림 핸들.
 *
 * **별도 @Composable 로 추출한 이유 = recomposition 격리.** 위치 드래그/트림 드래그는 매 프레임 local
 * override state(`dragOverrideMs` / `trimOverride*`)를 갱신하는데, 이 state read 가 부모
 * [UnifiedTimelineBar](룰러·재생바·전 클립을 한 번에 그리는 거대 컴포저블) scope 안에 있으면 손가락이
 * 한 번 움직일 때마다 타임라인 바 전체가 recompose 돼 클립/오버레이가 많을수록 심하게 버벅인다. 블록을
 * 떼어내면 드래그 중 invalidation 이 이 함수 scope 로 한정 — 끌고 있는 한 클립만 recompose 된다.
 * 호출부에서 `key(clip.id)` 로 감싸 리스트 재정렬 시에도 각 블록의 override state 가 보존된다.
 */
@Composable
private fun BgmClipBlock(
    clip: com.vibi.shared.domain.model.BgmClip,
    isSelected: Boolean,
    lane: Int,
    paletteIndex: Int?,
    peaks: List<Float>,
    label: String,
    totalMs: Long,
    laneWidthDp: androidx.compose.ui.unit.Dp,
    laneWidthPx: Float,
    bgmRowHeight: androidx.compose.ui.unit.Dp,
    bgmRowStrideDp: androidx.compose.ui.unit.Dp,
    bgmRangeMode: Boolean,
    accent: Color,
    markerColor: Color,
    bgmTapEnabled: Boolean,
    bgmDragEnabled: Boolean,
    /** 음원분리 진행 중 — true 면 위에 옅은 scrim 을 덮어 "처리 중" 시그널. 재선택/트림은 호출부에서
     *  [bgmTapEnabled] = false 로 막지만, 위치 드래그([bgmDragEnabled])는 유지한다. */
    locked: Boolean,
    onBgmSelectClip: (String) -> Unit,
    onBgmUpdateStart: (String, Long) -> Unit,
    onBgmDragLive: (String, Long) -> Unit = { _, _ -> },
    onBgmUpdateTrim: (clipId: String, sourceTrimStartMs: Long, sourceTrimEndMs: Long, newStartMs: Long?) -> Unit,
) {
    val tokens = LocalVibiColors.current
    // 드래그 중에는 local override 만 갱신 → 시각이 손가락 따라 즉시. drag end 시점에 한 번만
    // VM commit. 매 tick 마다 DB write/Flow emit 하던 lag 제거.
    var dragOverrideMs by remember(clip.id) { mutableStateOf<Long?>(null) }
    var dragBaseStartMs by remember(clip.id) { mutableLongStateOf(0L) }
    var dragAccumPx by remember(clip.id) { mutableFloatStateOf(0f) }
    // trim override — start 핸들 드래그 시 trimStart + startMs 동시 갱신, end 핸들은 trimEnd 만.
    var trimOverrideStart by remember(clip.id) { mutableStateOf<Long?>(null) }
    var trimOverrideEnd by remember(clip.id) { mutableStateOf<Long?>(null) }
    var trimOverrideStartMs by remember(clip.id) { mutableStateOf<Long?>(null) }
    // pointerInput 의 코루틴 closure 가 옛 clip 을 capture 하지 않도록 항상 최신 clip 참조 — 특히
    // startMs 가 ViewModel 측 갱신 후 onDragStart 의 base 값으로 stale 한 옛 값을 잡던 버그 방지.
    val currentClip by rememberUpdatedState(clip)
    // trim override 가 있으면 그 trim 으로 effectiveDuration 재계산. 없으면 clip 값 그대로.
    val effTrimStart = trimOverrideStart ?: clip.sourceTrimStartMs
    val effTrimEndRaw = trimOverrideEnd ?: clip.sourceTrimEndMs
    val effTrimEnd = if (effTrimEndRaw > 0L) effTrimEndRaw else clip.sourceDurationMs
    val effSrcDur = (effTrimEnd - effTrimStart).coerceAtLeast(1L)
    val speed = if (clip.speedScale > 0f) clip.speedScale else 1f
    val globalDurMs = (effSrcDur.toFloat() / speed).toLong().coerceAtLeast(1L)
    // 위치 드래그 clamp 가 stale globalDurMs (pointerInput 첫 셋업 시점 값) 를 잡으면, trim 후 사용 구간이
    // 짧아져도 옛 (더 긴) 길이로 maxStart 를 계산 → 사용 구간 끝이 timeline 끝에 못 닿는다 (잘린 tail 까지
    // 다 안에 들어가야 하는 것처럼 동작). [currentClip] 과 동일하게 State 로 감싸 onDrag 가 항상 최신
    // 사용 구간 길이를 읽도록.
    val currentGlobalDurMs by rememberUpdatedState(globalDurMs)
    // 위치 드래그(dragOverrideMs)는 layout(effectiveStartMs)에서 제외하고 graphicsLayer translationX
    // ([dragDeltaPx], draw phase)로만 반영 — range overlay(fill/핸들)도 동일 draw-phase 라 빠른 드래그에서도
    // 둘이 같은 프레임에 정확히 일치(이전엔 본체만 .offset=recompose 경로라 한 프레임 어긋났음). trim 드래그
    // (trimOverrideStartMs)는 자체 핸들도 recompose 경로라 그대로 layout 에 반영해 서로 일치 유지.
    val effectiveStartMs = trimOverrideStartMs ?: clip.startMs
    val startFrac = (effectiveStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val widthFrac = (globalDurMs.toFloat() / totalMs).coerceIn(0f, 1f - startFrac)
    val offsetXDp = laneWidthDp * startFrac
    val offsetYDp = bgmRowStrideDp * lane.coerceAtLeast(0)
    val widthDp = (laneWidthDp * widthFrac).coerceAtLeast(6.dp)
    // 위치 드래그 delta(px). graphicsLayer 람다 안에서만 dragOverrideMs 를 읽어 recompose 를 일으키지 않고
    // 매 프레임 draw 단계에서만 본체/고스트를 평행 이동 → overlay 와 frame-perfect 동기.
    val dragDeltaPx: () -> Float = {
        dragOverrideMs?.let { ((it - effectiveStartMs).toFloat() / totalMs) * laneWidthPx } ?: 0f
    }
    val isMuted = clip.volumeScale <= 0f
    val isRecording = isBgmRecording(clip)
    // 클립 색은 timeline 순서로 cycle (BgmPalette = 4색 muted gradient). 모든 BGM 이 같은 팔레트라
    // 양 테마 모두 밝은 톤 → contentColor 는 어두운 잉크로 고정 (대비 안정).
    val clipBaseColor = com.vibi.cmp.theme.BgmPalette.colorFor(paletteIndex, tokens)
    val clipContentColor = Color(0xFF0C0A09)
    // (선택 시) trim 으로 잘려나간 원본 head/tail 을 연한 고스트로 — "원래 얼마나 길었는지" +
    // "핸들을 어디까지 되돌릴 수 있는지" 를 동시에 시그널. head/tail 각각 source-ms 를 speed 로
    // 나눠 timeline-space 로 환산 후 [0, totalMs] 안으로 clip → 보이는 고스트 폭이 곧 실제 복원
    // 가능 drag 범위 (BgmTrimHandle 의 clamp 규칙과 동일). trim override 가 살아있으면 effTrim*
    // 가 이미 live 값이라 핸들 드래그 중에도 고스트가 즉시 늘고 줄어든다. 본체에 flush 하게 붙도록
    // 바깥 모서리만 둥글게(xs), 본체와 맞닿는 안쪽 모서리는 직각.
    if (isSelected && !bgmRangeMode && clip.sourceDurationMs > 0L) {
        val headGhostMs = (effTrimStart.toFloat() / speed).toLong()
        val tailGhostMs = ((clip.sourceDurationMs - effTrimEnd).toFloat() / speed).toLong()
        val ghostFill = if (isMuted) markerColor.copy(alpha = 0.14f)
            else clipBaseColor.copy(alpha = 0.24f)
        val ghostWaveColor = clipContentColor.copy(alpha = 0.16f)
        if (headGhostMs > 0L) {
            val ghostStartFrac = ((effectiveStartMs - headGhostMs).toFloat() / totalMs)
                .coerceIn(0f, 1f)
            val headWidthDp = laneWidthDp * (startFrac - ghostStartFrac)
            if (headWidthDp > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = laneWidthDp * ghostStartFrac, y = offsetYDp)
                        .graphicsLayer { translationX = dragDeltaPx() }
                        .width(headWidthDp)
                        .height(bgmRowHeight)
                        .clip(
                            RoundedCornerShape(
                                topStart = VibiRadius.xs,
                                bottomStart = VibiRadius.xs,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp,
                            )
                        )
                        .background(ghostFill),
                ) {
                    if (peaks.isNotEmpty()) {
                        BgmClipWaveform(
                            peaks = peaks,
                            trimStartFrac = 0f,
                            trimEndFrac = (effTrimStart.toFloat() / clip.sourceDurationMs)
                                .coerceIn(0f, 1f),
                            color = ghostWaveColor,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
        if (tailGhostMs > 0L) {
            val tailStartFrac = (startFrac + widthFrac).coerceIn(0f, 1f)
            val tailEndFrac = ((effectiveStartMs + globalDurMs + tailGhostMs).toFloat() / totalMs)
                .coerceIn(0f, 1f)
            val tailWidthDp = laneWidthDp * (tailEndFrac - tailStartFrac)
            if (tailWidthDp > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = laneWidthDp * tailStartFrac, y = offsetYDp)
                        .graphicsLayer { translationX = dragDeltaPx() }
                        .width(tailWidthDp)
                        .height(bgmRowHeight)
                        .clip(
                            RoundedCornerShape(
                                topStart = 0.dp,
                                bottomStart = 0.dp,
                                topEnd = VibiRadius.xs,
                                bottomEnd = VibiRadius.xs,
                            )
                        )
                        .background(ghostFill),
                ) {
                    if (peaks.isNotEmpty()) {
                        BgmClipWaveform(
                            peaks = peaks,
                            trimStartFrac = (effTrimEnd.toFloat() / clip.sourceDurationMs)
                                .coerceIn(0f, 1f),
                            trimEndFrac = 1f,
                            color = ghostWaveColor,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
    }
    // 본체 막대 — 선택 시 영상 range 선택과 동일 visual(accent fill 0.32a + top/bottom 2dp accent 바).
    // bgmRangeMode 에선 per-clip selection visual 미렌더 (range 바와 겹쳐 noisy).
    Box(
        modifier = Modifier
            .offset(x = offsetXDp, y = offsetYDp)
            .graphicsLayer { translationX = dragDeltaPx() }
            .width(widthDp)
            .height(bgmRowHeight)
            .clip(VibiShape.xs)
            // alpha 0.9 = 거의 solid — 캡컷의 단단한 컬러 블록 느낌. muted (볼륨 0) 는 회색조로 구분.
            .background(
                if (isMuted) markerColor.copy(alpha = 0.30f)
                else clipBaseColor.copy(alpha = 0.90f)
            )
            .pointerInput(clip.id, bgmTapEnabled) {
                // tap 은 BGM 바 자체 영역에서만 인식 — lane 빈 공간은 탭 안 됨.
                detectTapGestures(onTap = {
                    if (bgmTapEnabled) onBgmSelectClip(clip.id)
                })
            }
            .pointerInput(clip.id, totalMs, laneWidthPx, bgmDragEnabled) {
                // segment edit 모드에선 drag 미장착 — key 에 포함해 모드 진입/이탈 시 detector 재등록.
                if (!bgmDragEnabled) return@pointerInput
                // X axis 만 처리 — startMs 갱신 (lane 은 선택 시 자동 pack).
                detectDragGestures(
                    onDragStart = {
                        dragBaseStartMs = currentClip.startMs
                        dragAccumPx = 0f
                        dragOverrideMs = currentClip.startMs
                    },
                    onDrag = { change: PointerInputChange, drag: Offset ->
                        change.consume()
                        dragAccumPx += drag.x
                        if (laneWidthPx > 0f && totalMs > 0L) {
                            val deltaMs = (dragAccumPx / laneWidthPx) * totalMs
                            val maxStart = (totalMs - currentGlobalDurMs).coerceAtLeast(0L)
                            val next = (dragBaseStartMs + deltaMs).toLong().coerceIn(0L, maxStart)
                            dragOverrideMs = next
                            // 라이브로 구간 핸들/fill 도 같이 이동 (선택된 BGM range 일 때).
                            onBgmDragLive(currentClip.id, next)
                        }
                    },
                    onDragEnd = {
                        dragOverrideMs?.let { onBgmUpdateStart(currentClip.id, it) }
                        dragOverrideMs = null
                    },
                    onDragCancel = {
                        dragOverrideMs = null
                    },
                )
            },
    ) {
        // (a) 블록 내부 mini 파형 — trim 적용된 source 구간만 슬라이스. 라벨 가독성 위해 alpha 0.4.
        if (peaks.isNotEmpty() && clip.sourceDurationMs > 0L) {
            BgmClipWaveform(
                peaks = peaks,
                trimStartFrac = (effTrimStart.toFloat() / clip.sourceDurationMs).coerceIn(0f, 1f),
                trimEndFrac = (effTrimEnd.toFloat() / clip.sourceDurationMs).coerceIn(0f, 1f),
                color = clipContentColor.copy(alpha = 0.40f),
                modifier = Modifier.matchParentSize(),
            )
        }
        // 선택 시 accent fill — 영상 RangeFillStrip 의 fillAlpha=0.32 와 동일.
        if (isSelected && !bgmRangeMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accent.copy(alpha = 0.32f))
            )
        }
        // (b) 라벨 — 수직 중앙, 좌측 시작. 옅은 흰색 plate 위에 얹어 가독성 확보.
        if (widthDp > 36.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 4.dp)
                    .clip(VibiShape.xs)
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isRecording) Icons.Filled.Mic else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = clipContentColor,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = label,
                    color = clipContentColor,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        // 선택 시 top/bottom accent 바 — 영상 RangeFillStrip 가장자리 바와 동일 두께.
        if (isSelected && !bgmRangeMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TimelineBarSpec.RangeBorderThickness)
                    .align(Alignment.TopStart)
                    .background(accent)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TimelineBarSpec.RangeBorderThickness)
                    .align(Alignment.BottomStart)
                    .background(accent)
            )
        }
        // 음원분리(배경음 제거) 진행 중인 클립 — 위에 옅은 scrim + 중앙 무한 회전 스피너로 "처리 중" 표시.
        // 재선택/트림은 호출부에서 막지만 위치 드래그는 유지되므로 scrim 은 가볍게(완전 잠금 인상 회피).
        // 채도 있는 팔레트 블록 위 가시성 확보를 위해 스피너 뒤에 backgroundPrimary backing 을 깐다
        // (라이트/다크 모두 accent 와 반대 명도 → 흰-위-흰 안보임 회피). 좁은 클립은 스피너 생략.
        if (locked) {
            val tokens = LocalVibiColors.current
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(markerColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                if (widthDp >= TimelineBarSpec.ProcessingSpinnerMinWidth) {
                    ProcessingSpinner(
                        color = accent,
                        backing = tokens.backgroundPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
    // (c) 트림 핸들 — 선택된 클립의 좌·우 엣지. bgmRangeMode 면 미렌더 (range 핸들과 충돌 방지).
    if (isSelected && bgmDragEnabled && !bgmRangeMode) {
        // 영상 range 선택의 chevron 핸들과 동일 디자인 — hit zone 이 boundary 에 centered (straddle).
        // 클립이 timeline 끝에 닿아 hit zone 이 컨테이너 바깥으로 잘릴 땐 [0, laneWidthDp - hit] 로
        // clamp + chevron 정렬을 boundary 쪽(CenterStart/CenterEnd)으로 바꿔 바깥 엣지가 끝 라인에 flush.
        val maxHandleOffsetX = (laneWidthDp - TimelineBarSpec.HandleHitWidth)
            .coerceAtLeast(0.dp)
        val rawLeftOffsetX = offsetXDp - TimelineBarSpec.HandleHitWidth / 2
        val rawRightOffsetX = offsetXDp + widthDp - TimelineBarSpec.HandleHitWidth / 2
        val leftHandleOffsetX = rawLeftOffsetX.coerceIn(0.dp, maxHandleOffsetX)
        val rightHandleOffsetX = rawRightOffsetX.coerceIn(0.dp, maxHandleOffsetX)
        val atLeftBoundary = effectiveStartMs <= 0L
        val atRightBoundary = effectiveStartMs + globalDurMs >= totalMs
        val leftVisualAlign = if (atLeftBoundary) Alignment.CenterStart else Alignment.Center
        val rightVisualAlign = if (atRightBoundary) Alignment.CenterEnd else Alignment.Center
        // 좌 핸들 — sourceTrimStartMs + startMs 동시 갱신.
        BgmTrimHandle(
            side = BgmTrimSide.Start,
            clipId = clip.id,
            currentClip = currentClip,
            offsetX = leftHandleOffsetX,
            visualAlignment = leftVisualAlign,
            offsetY = offsetYDp,
            height = bgmRowHeight,
            handleColor = accent,
            gripColor = markerColor,
            laneWidthPx = laneWidthPx,
            totalMs = totalMs,
            onLiveUpdate = { newTrimStart, newStartMs ->
                trimOverrideStart = newTrimStart
                trimOverrideStartMs = newStartMs
            },
            onCommit = { newTrimStart, newStartMs ->
                onBgmUpdateTrim(currentClip.id, newTrimStart, currentClip.sourceTrimEndMs, newStartMs)
                trimOverrideStart = null
                trimOverrideStartMs = null
            },
            onCancel = {
                trimOverrideStart = null
                trimOverrideStartMs = null
            },
            onTap = { onBgmSelectClip(currentClip.id) },
        )
        // 우 핸들 — sourceTrimEndMs 만. startMs 불변.
        BgmTrimHandle(
            side = BgmTrimSide.End,
            clipId = clip.id,
            currentClip = currentClip,
            offsetX = rightHandleOffsetX,
            visualAlignment = rightVisualAlign,
            offsetY = offsetYDp,
            height = bgmRowHeight,
            handleColor = accent,
            gripColor = markerColor,
            laneWidthPx = laneWidthPx,
            totalMs = totalMs,
            onLiveUpdate = { newTrimEnd, _ ->
                trimOverrideEnd = newTrimEnd
            },
            onCommit = { newTrimEnd, _ ->
                onBgmUpdateTrim(currentClip.id, currentClip.sourceTrimStartMs, newTrimEnd, null)
                trimOverrideEnd = null
            },
            onCancel = {
                trimOverrideEnd = null
            },
            onTap = { onBgmSelectClip(currentClip.id) },
        )
    }
}

/**
 * BGM clip 블록 내부에 trim 적용된 source 파형 구간을 mini bar 로 그림. peaks 는 sourceUri 전체에
 * 대해 추출된 normalized [0..1] list — [trimStartFrac, trimEndFrac) 비율 구간만 추려 블록 폭에 맞춰
 * 균등 분배해 그린다. trim 으로 잘려나간 부분은 시각화에서 빠져 effectiveDuration 과 블록 폭이 일치.
 */
@Composable
private fun BgmClipWaveform(
    peaks: List<Float>,
    trimStartFrac: Float,
    trimEndFrac: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (peaks.isEmpty() || trimEndFrac <= trimStartFrac) return
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val startIdx = (peaks.size * trimStartFrac).toInt().coerceIn(0, peaks.size)
        val endIdx = (peaks.size * trimEndFrac).toInt().coerceIn(startIdx, peaks.size)
        val slice = if (endIdx > startIdx) peaks.subList(startIdx, endIdx) else return@Canvas
        if (slice.isEmpty()) return@Canvas
        val barWidth = w / slice.size.toFloat()
        val gap = (barWidth * 0.25f).coerceAtMost(1f)
        val drawW = (barWidth - gap).coerceAtLeast(0.6f)
        val centerY = h / 2f
        for ((i, p) in slice.withIndex()) {
            val barH = (h * 0.8f * p.coerceIn(0f, 1f)).coerceAtLeast(1f)
            val x = i * barWidth + (barWidth - drawW) / 2f
            drawRect(
                color = color,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(drawW, barH),
            )
        }
    }
}

/**
 * BGM 트랙의 display layout — 선택된 클립이 있을 때만 시간상 겹치는 클립들이 자동으로 별도 lane 으로
 * 분리되어 expand 된다 (CapCut "탭하면 펼침" 패턴). 선택 없으면 전부 lane 0 (한 줄 collapsed).
 *
 * [laneByClipId] 는 id → 표시할 lane (0-base). [laneCount] 는 사용 중인 lane 의 max+1.
 */
private data class BgmDisplayLayout(
    val laneByClipId: Map<String, Int>,
    val laneCount: Int,
)

/**
 * 선택된 클립 (= [selectedClipId]) 이 있을 때만 greedy interval scheduling 으로 lane pack —
 * startMs 오름차순 순회하며 각 클립을 "현재까지 끝난 시점이 startMs 이하" 인 가장 낮은 lane 에 배치.
 * 없는 lane 이면 새로 만든다. 선택 없으면 전부 lane 0 (한 줄 collapsed).
 */
private fun computeBgmDisplayLayout(
    clips: List<com.vibi.shared.domain.model.BgmClip>,
    selectedClipId: String?,
): BgmDisplayLayout {
    if (selectedClipId == null || clips.size <= 1) {
        return BgmDisplayLayout(
            laneByClipId = clips.associate { it.id to 0 },
            laneCount = 1,
        )
    }
    val sorted = clips.sortedBy { it.startMs }
    val laneByClipId = HashMap<String, Int>(clips.size)
    // laneEndTimes[i] = lane i 에 마지막으로 배치된 클립의 effective end (timeline ms).
    val laneEndTimes = mutableListOf<Long>()
    for (clip in sorted) {
        val clipEnd = clip.startMs + clip.effectiveDurationMs
        var assigned = -1
        for (i in laneEndTimes.indices) {
            if (clip.startMs >= laneEndTimes[i]) {
                laneByClipId[clip.id] = i
                laneEndTimes[i] = clipEnd
                assigned = i
                break
            }
        }
        if (assigned == -1) {
            laneByClipId[clip.id] = laneEndTimes.size
            laneEndTimes.add(clipEnd)
        }
    }
    return BgmDisplayLayout(
        laneByClipId = laneByClipId,
        laneCount = laneEndTimes.size.coerceAtLeast(1),
    )
}

/**
 * BGM clip 이 즉석 녹음 결과인지 (vs. 외부 파일). sandbox path 패턴으로 판정 — recording_ / rec_
 * prefix 또는 /recordings/ 경로 컴포넌트. Android/iOS 양쪽 recorder 가 이 패턴으로 저장.
 */
private fun isBgmRecording(clip: com.vibi.shared.domain.model.BgmClip): Boolean {
    val name = clip.sourceUri.substringAfterLast('/').substringBeforeLast('.')
    return name.startsWith("recording_", ignoreCase = true) ||
        name.startsWith("rec_", ignoreCase = true) ||
        clip.sourceUri.contains("/recordings/", ignoreCase = true)
}

/**
 * BGM clip 의 표시 라벨 text. 녹음은 추가 순서대로 "녹음1" / "녹음2"... (위치 바꿔도 번호 고정).
 * 파일은 filename (18자 truncate). 아이콘은 호출부에서.
 */
private fun bgmClipLabelText(
    clip: com.vibi.shared.domain.model.BgmClip,
    allClips: List<com.vibi.shared.domain.model.BgmClip>,
): String {
    // 사용자가 지정한 이름 우선 — SoundDeck 카드(SoundCardModel)와 동일 규칙. (rename 이 바에도 반영되도록.)
    clip.customName?.takeIf { it.isNotBlank() }?.let { return it }
    if (isBgmRecording(clip)) {
        val ordered = allClips
            .filter { isBgmRecording(it) }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
        val idx = ordered.indexOfFirst { it.id == clip.id }
        return if (idx >= 0) "Recording ${idx + 1}" else "Recording"
    }
    val name = clip.sourceUri.substringAfterLast('/').substringBeforeLast('.')
    return name.take(18)
}

private enum class BgmTrimSide { Start, End }

/**
 * 선택된 BGM clip 의 좌·우 트림 핸들. detectHorizontalDragGestures 로 drag 받아 source-space trim
 * 값과 (Start 면) timeline-space startMs 를 동시에 계산. drag 중에는 onLiveUpdate 만, release 시
 * onCommit. 클램프 규칙은 본문 주석 참조.
 *
 * 의미 (CapCut 동일):
 *  - Start 핸들 우측 드래그 → trimStart 증가 + startMs 증가 (콘텐츠 head 잘림, 시각 좌측 엣지가 손가락 따라감, 우측 엣지는 그 자리)
 *  - Start 핸들 좌측 드래그 → trimStart 감소 + startMs 감소 (head 복원). trimStart ≥ 0, startMs ≥ 0 으로 clamp.
 *  - End 핸들 좌측 드래그 → trimEnd 감소 (tail 잘림, startMs 불변)
 *  - End 핸들 우측 드래그 → trimEnd 증가 (tail 복원). trimEnd ≤ sourceDuration, 블록 우측이 totalMs 안에 머물도록 clamp.
 */
@Composable
private fun BgmTrimHandle(
    side: BgmTrimSide,
    clipId: String,
    currentClip: com.vibi.shared.domain.model.BgmClip,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    handleColor: Color,
    gripColor: Color,
    laneWidthPx: Float,
    totalMs: Long,
    /** Start 면 (newSourceTrimStartMs, newStartMs). End 면 (newSourceTrimEndMs, null). */
    onLiveUpdate: (newTrimMs: Long, newStartMs: Long?) -> Unit,
    onCommit: (newTrimMs: Long, newStartMs: Long?) -> Unit,
    onCancel: () -> Unit,
    /**
     * 핸들 hit zone 안에서 가벼운 탭(드래그 슬롭 미만) — 클립 본체 탭과 동일하게 선택 토글로 흘려보냄.
     * 본체가 핸들 hit zone 으로 일부 가려진 wide path 에서 사용자가 가장자리를 눌러도 선택 해제 가능.
     */
    onTap: () -> Unit,
    /**
     * hit zone 안에서 시각 chevron 을 정렬할 위치. 기본 Center — 좁은 클립 path 에서 hit zone 이 클립
     * 본체 바깥으로 통째 밀려난 경우엔 visual 까지 같이 떨어져 보이지 않도록 CenterEnd(좌핸들) /
     * CenterStart(우핸들) 로 inner-edge 정렬해 본체 엣지에 그대로 붙여 그린다.
     */
    visualAlignment: Alignment = Alignment.Center,
) {
    val clipRef by rememberUpdatedState(currentClip)
    var accumPx by remember(clipId, side) { mutableFloatStateOf(0f) }
    // base 는 onDragStart 시점의 trim/startMs snapshot. drag 중 absolute clamp 계산을 위해 보관.
    var baseTrimStart by remember(clipId, side) { mutableLongStateOf(0L) }
    var baseTrimEnd by remember(clipId, side) { mutableLongStateOf(0L) }
    var baseStartMs by remember(clipId, side) { mutableLongStateOf(0L) }
    val minEffSrcDurMs = 200L
    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .width(TimelineBarSpec.HandleHitWidth)
            .height(height)
            .pointerInput(clipId, side) {
                // 드래그 슬롭 미만의 탭은 클립 본체 tap 과 동일하게 선택 토글 — wide path 에서 핸들이
                // 본체 가장자리 16dp 를 덮는 구간에서도 사용자가 한번 더 눌러 선택 해제 가능.
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(clipId, side, laneWidthPx, totalMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        val c = clipRef
                        accumPx = 0f
                        baseTrimStart = c.sourceTrimStartMs
                        baseTrimEnd = if (c.sourceTrimEndMs > 0L) c.sourceTrimEndMs else c.sourceDurationMs
                        baseStartMs = c.startMs
                    },
                    onDragEnd = {
                        // commit — onLiveUpdate 가 이미 마지막 값을 emit 한 상태와 같지만, 부모는
                        // onCommit 에서 VM 호출 + override clear 를 단일 트랜잭션처럼 처리.
                        val c = clipRef
                        val speed = if (c.speedScale > 0f) c.speedScale else 1f
                        val totalWidthPx = laneWidthPx
                        if (totalWidthPx <= 0f || totalMs <= 0L) {
                            onCancel()
                            return@detectHorizontalDragGestures
                        }
                        val deltaTimelineMs = (accumPx / totalWidthPx) * totalMs
                        if (side == BgmTrimSide.Start) {
                            // 좌측 드래그 음수, 우측 드래그 양수. timeline-space 에서 처리한 뒤 source-space 로 환산.
                            val effDurBase = ((baseTrimEnd - baseTrimStart).toFloat() / speed).toLong()
                                .coerceAtLeast(1L)
                            val minDelta = maxOf(
                                -baseStartMs.toFloat(),
                                -(baseTrimStart.toFloat() / speed),
                            )
                            val minEffDur = (minEffSrcDurMs.toFloat() / speed).toLong().coerceAtLeast(50L)
                            val maxDelta = (effDurBase - minEffDur).toFloat().coerceAtLeast(0f)
                            val applied = deltaTimelineMs.coerceIn(minDelta, maxDelta)
                            val newStartMs = (baseStartMs + applied).toLong().coerceAtLeast(0L)
                            val newTrimStart = (baseTrimStart + applied * speed).toLong().coerceAtLeast(0L)
                            onCommit(newTrimStart, newStartMs)
                        } else {
                            val effDurBase = ((baseTrimEnd - baseTrimStart).toFloat() / speed).toLong()
                                .coerceAtLeast(1L)
                            val minEffDur = (minEffSrcDurMs.toFloat() / speed).toLong().coerceAtLeast(50L)
                            val maxRightExtendTimeline = (totalMs - (baseStartMs + effDurBase))
                                .toFloat()
                                .coerceAtLeast(0f)
                            val maxRightExtendSource = (c.sourceDurationMs - baseTrimEnd).toFloat()
                                .coerceAtLeast(0f) / speed
                            val maxDelta = minOf(maxRightExtendTimeline, maxRightExtendSource)
                            val minDelta = (minEffDur - effDurBase).toFloat()
                            val applied = deltaTimelineMs.coerceIn(minDelta, maxDelta)
                            val newTrimEnd = (baseTrimEnd + applied * speed).toLong()
                                .coerceIn(0L, c.sourceDurationMs)
                            onCommit(newTrimEnd, null)
                        }
                    },
                    onDragCancel = { onCancel() },
                    onHorizontalDrag = { _, drag ->
                        accumPx += drag
                        val c = clipRef
                        val speed = if (c.speedScale > 0f) c.speedScale else 1f
                        if (laneWidthPx <= 0f || totalMs <= 0L) return@detectHorizontalDragGestures
                        val deltaTimelineMs = (accumPx / laneWidthPx) * totalMs
                        if (side == BgmTrimSide.Start) {
                            val effDurBase = ((baseTrimEnd - baseTrimStart).toFloat() / speed).toLong()
                                .coerceAtLeast(1L)
                            val minDelta = maxOf(
                                -baseStartMs.toFloat(),
                                -(baseTrimStart.toFloat() / speed),
                            )
                            val minEffDur = (minEffSrcDurMs.toFloat() / speed).toLong().coerceAtLeast(50L)
                            val maxDelta = (effDurBase - minEffDur).toFloat().coerceAtLeast(0f)
                            val applied = deltaTimelineMs.coerceIn(minDelta, maxDelta)
                            val newStartMs = (baseStartMs + applied).toLong().coerceAtLeast(0L)
                            val newTrimStart = (baseTrimStart + applied * speed).toLong().coerceAtLeast(0L)
                            onLiveUpdate(newTrimStart, newStartMs)
                        } else {
                            val effDurBase = ((baseTrimEnd - baseTrimStart).toFloat() / speed).toLong()
                                .coerceAtLeast(1L)
                            val minEffDur = (minEffSrcDurMs.toFloat() / speed).toLong().coerceAtLeast(50L)
                            val maxRightExtendTimeline = (totalMs - (baseStartMs + effDurBase))
                                .toFloat()
                                .coerceAtLeast(0f)
                            val maxRightExtendSource = (c.sourceDurationMs - baseTrimEnd).toFloat()
                                .coerceAtLeast(0f) / speed
                            val maxDelta = minOf(maxRightExtendTimeline, maxRightExtendSource)
                            val minDelta = (minEffDur - effDurBase).toFloat()
                            val applied = deltaTimelineMs.coerceIn(minDelta, maxDelta)
                            val newTrimEnd = (baseTrimEnd + applied * speed).toLong()
                                .coerceIn(0L, c.sourceDurationMs)
                            onLiveUpdate(newTrimEnd, null)
                        }
                    },
                )
            },
        contentAlignment = visualAlignment,
    ) {
        // boundary flush (CenterStart/CenterEnd) 일 땐 outer 모서리만 직각 — fill/top·bottom bar 와 단차 차단.
        val outerSharp = (side == BgmTrimSide.Start && visualAlignment == Alignment.CenterStart) ||
            (side == BgmTrimSide.End && visualAlignment == Alignment.CenterEnd)
        ChevronThumb(
            side = side,
            height = height,
            handleColor = handleColor,
            gripColor = gripColor,
            outerCornerSharp = outerSharp,
        )
    }
}

/** ARGB hex (#AARRGGBB or #RRGGBB) → Compose Color. 잘못된 입력은 white fallback. */
/** 전체화면 transport 의 시각 표시 — "M:SS" 또는 "MM:SS" 포맷. */
private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/**
 * 눈금자 라벨 — interval 이 1초 이상이면 정수 초의 [formatMmSs]. 0.5초 단위 라벨이면 ".5" 접미.
 */
private fun formatRulerLabel(ms: Long, intervalSec: Double): String {
    val base = formatMmSs(ms)
    if (intervalSec >= 1.0) return base
    val hasHalf = ((ms % 1000) / 100) >= 5
    return if (hasHalf) "$base.5" else base
}
