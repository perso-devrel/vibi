package com.vibi.cmp.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.DropdownMenu
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.cmp.platform.StemMixerSource
import com.vibi.cmp.platform.rememberAudioPicker
import com.vibi.cmp.platform.rememberAudioRecorder
import com.vibi.cmp.platform.rememberStemMixer
import com.vibi.cmp.ui.chat.ChatPanel
import com.vibi.shared.domain.model.AutoJobStatus
import com.vibi.shared.domain.model.hasNonTrivialEdits
import com.vibi.shared.ui.timeline.AudioSeparationStep
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.vibi.cmp.platform.VideoPlayer
import com.vibi.shared.ui.chat.ChatViewModel
import com.vibi.shared.ui.timeline.EditTarget
import com.vibi.shared.ui.timeline.SaveStatus
import com.vibi.shared.ui.timeline.ShareStatus
import com.vibi.shared.ui.timeline.hasBgm
import com.vibi.shared.ui.timeline.StepTransitionWarning
import com.vibi.shared.ui.timeline.PreviewMode
import com.vibi.shared.ui.timeline.TimelineStep
import com.vibi.shared.ui.timeline.TimelineViewModel
import com.vibi.shared.ui.timeline.isLocalizationBusy
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
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
 * Timeline 화면 — 6 핵심 기능 (업로드·TTS·자막·더빙·음성분리·구간선택) 통합.
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

    // unified mode 게이트 — stepper UI 가 숨겨지면 5~8단계가 한 스크롤에 모두 노출. AudioSources /
    // SubtitleDub 전용 분기는 본 두 boolean 으로 묶어 stepper-on 경로도 동시에 유지.
    val unifiedScroll = com.vibi.cmp.platform.RuntimeFlags.stepperHidden
    val showAudioSourcesContent = unifiedScroll || state.currentStep == TimelineStep.EditAudio
    val showSubtitleDubContent = unifiedScroll || state.currentStep == TimelineStep.SubtitleDub

    // ChatVM — FAB unread badge 와 panel 양쪽이 같은 인스턴스 공유. bindProject / bindTimelineEvents
    // 를 panel 가시성과 무관하게 호출해 panel 닫힌 상태에서도 chatAssistantEvents 수신 → unread 토글.
    val chatViewModel: ChatViewModel = koinViewModel()
    val chatState by chatViewModel.state.collectAsState()
    LaunchedEffect(projectId) {
        chatViewModel.bindProject(projectId)
    }
    LaunchedEffect(viewModel) {
        chatViewModel.bindTimelineEvents(viewModel.chatAssistantEvents)
    }

    // 자연어 confirm 흐름 — Gemini 가 채팅으로 동의를 받은 후 emit 한 proposal 을 자동으로 dispatch.
    // 적용/수정/취소 버튼이 없으므로 panel 가시성과 무관하게 본 화면에서 트리거.
    val chatDispatcher: com.vibi.shared.domain.chat.ChatToolDispatcher = koinInject()
    LaunchedEffect(chatState.pending) {
        val proposal = chatState.pending ?: return@LaunchedEffect
        chatViewModel.applyProposal(proposal.steps, chatDispatcher, viewModel)
    }

    // 채팅 패널 토글 — FAB (헤더 우측) 로 열고 ModalBottomSheet 가 자체적으로 dismiss 처리.
    var chatSheetVisible by remember { mutableStateOf(false) }

    // 저장 완료 → InputScreen 복귀. ViewModel 의 _navigateBackHome SharedFlow 가 1회성 신호.
    LaunchedEffect(viewModel) {
        viewModel.navigateBackHome.collect { onSaved() }
    }

    // ── 분리된 stem 동시 재생 mixer (Phase 2) ──
    // 첫 directive 의 selections 기준으로 stem 들을 ExoPlayer (Android) 다중 인스턴스에 로드하고,
    // 영상 재생/일시정지/seek 에 동기화. directive range 밖 위치에서는 mute 효과 (volume 0).
    // iOS 는 cinterop 한계로 no-op fallback (Swift bridge 도입 시 활성화).
    val stemMixer = rememberStemMixer()
    // 모든 directive 의 stems 를 한 번에 load — directive 별 group 으로 prepare. transition 시
    // setActiveGroup 만 변경, 다운로드 끊김 없음.
    val allDirectives = state.separationDirectives
    val directivesKey = remember(allDirectives) {
        allDirectives.joinToString("|") { d ->
            "${d.id}:${d.selections.joinToString(",") { "${it.stemId}=${it.audioUrl}" }}"
        }
    }
    LaunchedEffect(directivesKey) {
        val sources = allDirectives.flatMap { dir ->
            dir.selections.mapNotNull { sel ->
                val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                StemMixerSource(stemId = sel.stemId, audioUrl = url, groupId = dir.id)
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

    // EditAudio (편집·음원) 단계는 자동 segment edit 모드 진입을 하지 않는다 —
    // 사용자가 BGM 작업 + 영상 segment 작업을 자유롭게 섞을 수 있도록. segment 편집은
    // 영상 위 우상단 연필 버튼(onEnterSegmentEditMode) 명시 진입.

    // 자막/더빙 단계 진입 시 생성 패널 자동 열기. 사용자가 닫으면 같은 단계 안에서 다시 열리지 않고,
    // 다른 단계 갔다 돌아오면 다시 열림 (currentStep 키 변경으로 재발화).
    // stepper 가 숨겨진 unified 모드(RuntimeFlags.stepperHidden) 에선 명시적 진입(SubtitleDubCard
    // 버튼)으로만 열림 — 자동 열기 비활성. 상세는 RuntimeFlags 주석 참조.
    LaunchedEffect(state.currentStep) {
        if (com.vibi.cmp.platform.RuntimeFlags.stepperHidden) return@LaunchedEffect
        if (state.currentStep == TimelineStep.SubtitleDub && !state.localizationOpen) {
            viewModel.onShowLocalization()
        }
    }

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
            val extracted = com.vibi.cmp.platform.extractAudioPeaks(state.videoUri, samples = 240)
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
                    val extracted = com.vibi.cmp.platform.extractAudioPeaks(url, samples = 240)
                    if (extracted.isNotEmpty()) {
                        stemPeaksCacheTimeline[url] = extracted
                        stemPeaks[url] = extracted
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEDEBE9))) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(VibiSpacing.base),
        verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
    ) {
        // 헤더: 뒤로 + 단계 타이틀 + 공유/저장. 백그라운드 잡 진행 중이면 저장 disabled.
        // 저장 버튼이 자체적으로 모든 variant 렌더 → 갤러리 저장 → EditProject 삭제 → InputScreen 복귀를
        // 호출하므로 별도 ExportScreen 으로 이동하는 흐름은 폐기됐다.
        val saveAnyJobRunning = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
            state.autoDubStatus == AutoJobStatus.RUNNING ||
            state.audioSeparation?.step == AudioSeparationStep.PROCESSING ||
            state.processingSeparations.isNotEmpty() ||
            state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
            state.sttPreflightStatus == AutoJobStatus.RUNNING
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(VibiSpacing.xxl)
                    .clip(CircleShape)
                    .background(tokens.chipBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = tokens.onBackgroundPrimary, style = typo.titleLg)
            }
            Text(
                text = state.currentStep.label,
                style = typo.displaySm,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = tokens.onBackgroundPrimary,
                modifier = Modifier.weight(1f),
            )
            val saving = state.saveStatus is SaveStatus.RUNNING
            val savingPercent = (state.saveStatus as? SaveStatus.RUNNING)?.progress ?: 0
            val sharing = state.shareStatus is ShareStatus.RUNNING
            val sharingPercent = (state.shareStatus as? ShareStatus.RUNNING)?.progress ?: 0
            var exportSheetOpen by remember { mutableStateOf(false) }
            // 내보내기 진입점 — 텍스트 라벨 버튼. 진행 중이면 라벨 자리에 progress percent 노출.
            OutlinedButton(
                enabled = !sharing && !saving && !saveAnyJobRunning && state.segments.isNotEmpty(),
                onClick = { exportSheetOpen = true },
                shape = VibiShape.lg,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VibiSpacing.sm, vertical = 0.dp),
                modifier = Modifier.height(VibiSpacing.xxl),
            ) {
                val label = when {
                    saving -> "${savingPercent}%"
                    sharing -> "${sharingPercent}%"
                    else -> "Export"
                }
                Text(label, style = typo.bodySm, color = tokens.onBackgroundPrimary)
            }
            if (exportSheetOpen) {
                ExportOptionsSheet(
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


        // 3단계 stepper — RuntimeFlags.stepperHidden 가 false 일 때만 노출. unified Sound Deck UI
        // 에서는 5~8단계가 한 스크롤에 모두 보이므로 stepper 가 불필요.
        // 클릭 차단은 VM 의 onSelectStep 가 처리 (in-flight job 등). 채팅 dispatcher 실행 중이면 stepper
        // 이동 차단 — 처리 중 step 이 바뀌면 UI 가 새 step 의 진입 sheet 를 띄워 dispatch 결과 화면이 가려진다.
        if (!com.vibi.cmp.platform.RuntimeFlags.stepperHidden) {
            TimelineStepperRow(
                currentStep = state.currentStep,
                onStepClick = { target ->
                    if (chatState.isApplying) return@TimelineStepperRow
                    viewModel.onSelectStep(target)
                },
            )
        }

        // 버전 선택 — DropdownMenu 대신 inline pill row. 가로 스크롤 가능.
        // "" sentinel = 원본 자막 (lang="" 클립). review 완료 후에만 chip 노출.
        // SSOT — TimelineViewModel 이 hasConfirmedOriginalSubtitle 헬퍼로 set. export variant
        // 산출 (SaveAllVariantsUseCase.computeAllVariantKeys) 도 같은 헬퍼를 본다.
        val hasOriginalSubtitleChip = state.hasOriginalSubtitleVariant
        // chip 라벨 prefix: 더빙 있는 lang → "DUB_", 자막만 있는 lang → "SUB_", 원본 자막 → "SUB_ORIGINAL".
        // 같은 lang 에 자막+더빙 둘 다 있어도 더빙 우선 (BFF 가 자막 함께 burn).
        val langsWithDub = state.dubbedAudioPaths.keys + state.dubbedVideoPaths.keys
        val langsWithSubtitle = state.subtitleClips.map { it.languageCode }.filter { it.isNotBlank() }.toSet()
        val versions = buildList<Pair<String?, String>> {
            add(null to "기본")
            if (hasOriginalSubtitleChip) add("" to "SUB_ORIGINAL")
            state.targetLanguageCodes.forEach { code ->
                val label = when {
                    code in langsWithDub -> "DUB_${code.uppercase()}"
                    code in langsWithSubtitle -> "SUB_${code.uppercase()}"
                    else -> code.uppercase()
                }
                add(code to label)
            }
        }
        val isJobRunning = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
            state.autoDubStatus == AutoJobStatus.RUNNING ||
            state.audioSeparation?.step == AudioSeparationStep.PROCESSING ||
            state.regenerateSubtitleStatus == AutoJobStatus.RUNNING
        // 변종 선택은 자막/더빙 단계에서만 의미 있음 (Edit·음원 단계에선 "기본" 만 존재).
        // unified 모드에선 stepper 가 없으므로 실제로 변종이 둘 이상일 때만 노출.
        val showVersionPicker = if (unifiedScroll) versions.size > 1
                                else state.currentStep == TimelineStep.SubtitleDub
        if (showVersionPicker) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            val currentLabel = versions.firstOrNull { it.first == state.previewLangCode }?.second ?: "기본"
            Box {
                Row(
                    modifier = Modifier
                        .clip(VibiShape.pill)
                        .background(tokens.chipBg)
                        .clickable(enabled = !isJobRunning) { menuOpen = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(currentLabel, color = tokens.onBackgroundPrimary, style = typo.bodySm)
                    if (isJobRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(VibiSpacing.sm),
                            color = tokens.onBackgroundPrimary,
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text("▾", color = tokens.onBackgroundPrimary, style = typo.bodySm)
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    versions.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onSelectPreviewLang(code)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }

        // 비디오 프리뷰
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(VibiShape.lg)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (state.videoUri.isNotEmpty()) {
                // 미리보기 언어 선택 시 BFF 가 mux 한 단일 mp4 (전체 timeline 더빙 결과) 사용.
                // BFF AutoDubService 는 영상 전체를 한 번에 더빙해 timeline 전체용 결과 1 개를 반환 →
                // split 으로 segment 가 여러개여도 미리보기는 단일 player item 으로 collapse.
                // (이전: 첫 segment 만 swap → 후반부 원본 사운드로 재생되던 결함)
                val previewMuxUri = state.previewLangCode?.let { state.dubbedVideoPaths[it] }
                val videoSegs = state.segments.filter {
                    it.type == com.vibi.shared.domain.model.SegmentType.VIDEO
                }
                val playerItems = if (previewMuxUri != null) {
                    listOf(
                        com.vibi.cmp.platform.VideoPlayerItem(
                            sourceUri = previewMuxUri,
                            trimStartMs = 0L,
                            trimEndMs = 0L,
                            speedScale = 1f,
                            volumeScale = 1f,
                        )
                    )
                } else {
                    // segments 를 VideoPlayerItem playlist 로 변환 — multi-segment / 복제 / 삭제 결과
                    // 모두 미리보기에 즉시 반영. trim/speed/volume per-item 적용.
                    videoSegs.map { seg ->
                        com.vibi.cmp.platform.VideoPlayerItem(
                            sourceUri = seg.sourceUri,
                            trimStartMs = seg.trimStartMs,
                            // iOS 는 trimEndMs=0 일 때 asset.duration 을 동기 읽기 → lazy load
                            // 로 0/indefinite 반환 시 composition 0-길이 → 검정. caller 에서
                            // effectiveTrimEndMs (durationMs fallback) 으로 항상 실값 전달.
                            trimEndMs = seg.effectiveTrimEndMs,
                            speedScale = seg.speedScale,
                            // directive range 안에서 stem 재생 중이면 원본 audio mute — segment.volumeScale 은
                            // 사용자 설정값 보존하고 player 에만 일시 0 적용. 파형 amplitude 는 영향 없음.
                            volumeScale = if (state.runtimeVideoMutedForDirective) 0f else seg.volumeScale,
                        )
                    }
                }
                // BgmClip 들을 video 재생에 sync — clip startMs 부터 자동 재생, video pause/seek 시 같이.
                // 영상편집 모드에선 사용자가 video 자체에 집중하므로 BGM mix-in 차단 (빈 리스트 전달
                // → 기존 player 정리됨).
                com.vibi.cmp.platform.BgmPlaybackSync(
                    clips = if (state.isSegmentEditMode) emptyList() else state.bgmClips,
                    isPlaying = state.isPlaying,
                    currentMs = state.playbackPositionMs,
                )
                VideoPlayer(
                    items = playerItems,
                    isPlaying = state.isPlaying,
                    seekToMs = state.playbackPositionMs.takeIf { state.videoDurationMs > 0 },
                    onPositionChanged = { ms -> viewModel.onUpdatePlaybackPosition(ms) },
                    // 영상 끝 도달 → ① 재생/멈춤 버튼을 ▶ 로 (isPlaying=false) ② 위치를 0 으로 reset
                    // 해서 사용자가 다시 ▶ 누르면 처음부터 재생.
                    onEnded = {
                        if (state.isPlaying) viewModel.onTogglePlayback()
                        viewModel.onUpdatePlaybackPosition(0L)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 영상 Box bg 가 항상 검정이므로 라이트 모드에서도 흰색 텍스트 유지.
                Text("비디오 없음", color = Color.White)
            }

            // 자막 overlay — 비디오 위 하단에 오버레이. Compose 의 Box 스택. previewLangCode 의
            // 자막만 싱크 맞춰 표시. (iOS UIKitView z-order 한계로 Android 에서만 시각적으로 정확히
            // 영상 위에 그려지고, iOS 는 Swift bridge 도입 시까지 가시성 제한적.)
            // previewLangCode null = "기본" (자막 X). "" = "원본 자막" chip (lang="" 매칭). "ko"/"en" = 해당 lang.
            val activeSubtitleClip = run {
                val lang = state.previewLangCode
                if (lang == null) null
                else state.subtitleClips
                    .filter { it.languageCode == lang }
                    .firstOrNull { clip -> state.playbackPositionMs in clip.startMs..clip.endMs }
            }
            if (activeSubtitleClip != null) {
                val anchor = activeSubtitleClip.position.anchor
                val align = when (anchor) {
                    com.vibi.shared.domain.model.Anchor.TOP -> Alignment.TopCenter
                    com.vibi.shared.domain.model.Anchor.MIDDLE -> Alignment.Center
                    com.vibi.shared.domain.model.Anchor.BOTTOM -> Alignment.BottomCenter
                }
                Box(
                    modifier = Modifier
                        .align(align)
                        .fillMaxWidth()
                        .padding(horizontal = VibiSpacing.base, vertical = VibiSpacing.sm)
                        .background(parseArgbHexColor(activeSubtitleClip.backgroundColorHex), VibiShape.sm)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeSubtitleClip.text,
                        color = parseArgbHexColor(activeSubtitleClip.colorHex),
                        fontSize = activeSubtitleClip.fontSizeSp.sp,
                    )
                }
            }

            // 영상편집 진입은 SoundDeck 의 "영상 다듬기" 카드(EditEntryCard)로 일원화.
        }

        // Transport row: play/pause + 시간 표시
        if (state.videoUri.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(tokens.onBackgroundPrimary)
                        .clickable { viewModel.onTogglePlayback() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "일시정지" else "재생",
                        tint = tokens.backgroundPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "${state.playbackPositionMs / 1000}s / ${state.videoDurationMs / 1000}s",
                    color = tokens.onBackgroundPrimary,
                    style = typo.bodyMd,
                    modifier = Modifier.weight(1f)
                )
                // Undo / Redo — 작은 아이콘만, transport row 우측에 inline
                Box(
                    modifier = Modifier
                        .size(VibiSpacing.xxl)
                        .clip(CircleShape)
                        .background(if (state.canUndo) tokens.chipBg else tokens.chipBgDisabled)
                        .clickable(enabled = state.canUndo) { viewModel.onUndo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "실행 취소",
                        tint = if (state.canUndo) tokens.onBackgroundPrimary else tokens.chipContentDisabled,
                        modifier = Modifier.size(VibiSpacing.md)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(VibiSpacing.xxl)
                        .clip(CircleShape)
                        .background(if (state.canRedo) tokens.chipBg else tokens.chipBgDisabled)
                        .clickable(enabled = state.canRedo) { viewModel.onRedo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "다시 실행",
                        tint = if (state.canRedo) tokens.onBackgroundPrimary else tokens.chipContentDisabled,
                        modifier = Modifier.size(VibiSpacing.md)
                    )
                }
            }
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
            val processingOverlays = remember(state.processingSeparations, state.videoDurationMs) {
                state.processingSeparations
                    .filter { it.segmentId.isNotBlank() }
                    .map { p ->
                        val s = p.rangeStartMs
                        val e = p.rangeEndMs
                        val (start, end) = if (s != null && e != null && e > s) s to e
                        else 0L to state.videoDurationMs
                        ProcessingSeparationOverlay(start, end, p.progress)
                    }
            }
            // 단일 통합 타임라인 바 — 재생/구간선택/segment·directive + BGM lane 까지 한 컴포넌트.
            // AudioSources 단계 + 음원분리 진행 가능 상태(=비 segment edit) 에서만 BGM region 노출.
            // SubtitleDub 단계에서는 BFF render 가 BGM 포함해 합치므로 별도 시각 불필요 — 데이터는 보존.
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
                onSegmentTap = { viewModel.onSelectSegmentInEdit(it) },
                // directive 탭 시 AudioSeparationSheet 띄우지 않음 — 편집은 SoundDeck 의 stem 카드에서 처리.
                onDirectiveTap = {},
                onScrub = { viewModel.onUpdatePlaybackPosition(it) },
                onRangeStartChange = { viewModel.onSetPendingRangeStart(it) },
                onRangeEndChange = { viewModel.onSetPendingRangeEnd(it) },
                onTranslateRange = { viewModel.onTranslateRange(it) },
                onFreeIntervalTap = { s, e -> viewModel.onSelectVideoRange(s, e) },
                onRangeTapToggle = { viewModel.onClearRangeSelection() },
                bgmClips = state.bgmClips,
                bgmLaneCount = state.bgmLaneCount,
                selectedBgmClipId = state.selectedBgmClipId,
                // segment edit 모드에서만 BGM 탭 허용 — 탭 시 그 BGM 의 timeline bounds 로 range 스냅해
                // 위 EditActionsPanel 로 volume/speed/duplicate/delete 적용. 시각 highlight 도 함께 부여.
                bgmTapEnabled = state.isSegmentEditMode,
                // segment edit (영상 다듬기) 모드에선 BGM 위치/lane drag + lane 수 조절 pill 모두 잠금.
                // 영상 편집 중 BGM 이 같이 따라 움직이면 사용자 의도와 어긋나는 사고가 잦아, 다듬기
                // 활성 동안엔 BGM 트랙은 read-only (탭으로 BGM range 편집 진입은 그대로 허용).
                bgmDragEnabled = !state.isSegmentEditMode,
                onBgmSelectClip = viewModel::onSelectBgmForRangeEdit,
                onBgmUpdateStart = viewModel::onUpdateBgmStartMs,
                onBgmUpdateLane = viewModel::onUpdateBgmLane,
                onBgmSetLaneCount = viewModel::onSetBgmLaneCount,
                // segment edit 모드에서도 BGM 표시 — range-edit (volume/speed/duplicate/delete) 가
                // applyBgmRange* 헬퍼로 BGM 까지 적용하므로 사용자가 lane 을 보면서 편집 가능.
                showBgm = showAudioSourcesContent,
                // BGM 타깃 모드 — editTargets 에 Bgm 포함 시 영상 strip 의 range overlay 숨기고 BGM lane
                // 에 동일 디자인의 range fill 노출. 영상/BGM 동시 선택 막아 사용자 의도가 분명한 트랙에만 apply.
                bgmRangeMode = state.editTargets.hasBgm(),
            )
            if (state.isRangeSelecting) {
                Text(
                    "구간 ${state.pendingRangeStartMs / 1000}s ~ ${state.pendingRangeEndMs / 1000}s · 재생 ${state.playbackPositionMs / 1000}s",
                    style = typo.bodySm,
                    color = tokens.accent
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
                ) {
                    if (!state.isSegmentEditMode) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val segId = state.segments.firstOrNull()?.id ?: return@Button
                                viewModel.onCancelRangeMode()
                                // 시트 안 띄우고 바로 분리 시작 — Perso 가 화자 자동 감지라 추가 입력 불필요.
                                viewModel.onShowAudioSeparationSheet(segId)
                                viewModel.onStartSeparation()
                            }
                        ) { Text("이 구간 음원분리") }
                        OutlinedButton(onClick = { viewModel.onCancelRangeMode() }) { Text("취소") }
                    }
                }
                // SegmentEditActionPanel 은 음원분리/음원삽입 행 아래로 이동 — 사용자 요청.
            }
        }

        // 더빙/자막 트랙 막대는 일단 숨김 — 음성분리 한 기능만 노출하는 단순화 단계.
        // 선택된 클립 액션 row 제거 — 액션은 자막 편집 패널 우상단의 "적용" 버튼 한 군데로 통합.

        // 자막/더빙 진행 상태는 상단 버전 chip 의 spinner 로만 표시.

        // 진입점 버튼들 (음성 분리 + 자막/더빙 생성)
        val firstSegId = state.segments.firstOrNull()?.id
        // 백그라운드 잡 진행 중 — 새 잡 시작/내보내기 막기 위한 게이팅 플래그.
        val anyJobRunning = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
            state.autoDubStatus == AutoJobStatus.RUNNING ||
            state.audioSeparation?.step == AudioSeparationStep.PROCESSING ||
            state.processingSeparations.isNotEmpty() ||
            state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
            state.sttPreflightStatus == AutoJobStatus.RUNNING
        // segment edit 모드 (isSegmentEditMode=true) 일 때도 isRangeSelecting 이 true 라
        // 음원분리 단독 range mode (isSegmentEditMode=false) 일 때만 숨김.
        // 영상편집 진입은 SoundDeck 의 "영상 다듬기" 카드, 종료는 SegmentEditActionPanel cancel.
        if ((!state.isRangeSelecting || state.isSegmentEditMode) && !state.localizationOpen) {
            // 음원 단계 — 음원분리/음원삽입 두 버튼을 weight(1f) 로 균등 분배.
            // 진행 상태는 timeline accent overlay 가 표시 — 버튼 라벨은 새 분리 진입점으로 고정.
            // FAILED 만 예외 — "다시 시도" 클릭 시 FAILED 비우고 새 분리 흐름.
            if (showAudioSourcesContent) {
                val sepLabel = when (state.separationStatus) {
                    AutoJobStatus.FAILED -> "❌ 다시 시도"
                    else -> "음원 분리"
                }
                val audioPicker = rememberAudioPicker { uri -> viewModel.onPickBgmAudio(uri) }
                val recorder = rememberAudioRecorder(
                    onRecorded = { uri, _ -> viewModel.onPickBgmAudio(uri) },
                    onError = { /* TODO: bgmError 상태로 토스트 */ },
                )
                val recording = recorder.isRecording
                var pendingRecord by remember { mutableStateOf(false) }
                LaunchedEffect(recording) { if (recording) pendingRecord = false }
                var micLevel by remember { mutableStateOf(0f) }
                LaunchedEffect(recording) {
                    if (!recording) { micLevel = 0f; return@LaunchedEffect }
                    while (recording) {
                        micLevel = recorder.currentLevel
                        kotlinx.coroutines.delay(100)
                    }
                    micLevel = 0f
                }
                var audioMenuOpen by remember { mutableStateOf(false) }
                // 편집 토글 — segment edit 모드 내내 표시. range 미선택 상태 (start==end) 면 apply 류는
                // VM 가드에서 no-op 처리되고 onCancel 만 활성. 사용자가 토글 사라짐 → 다시 진입 반복하지 않도록.
                if (state.isSegmentEditMode) {
                    com.vibi.cmp.ui.timeline.sounddeck.EditActionsPanel(
                        title = "구간 편집",
                        volume = state.pendingRangeVolume,
                        speed = state.pendingRangeSpeed,
                        onVolumeChange = { viewModel.onUpdatePendingRangeVolume(it) },
                        onSpeedChange = { viewModel.onUpdatePendingRangeSpeed(it) },
                        onApplyVolume = { viewModel.onApplyRangeVolume(it) },
                        onApplySpeed = { viewModel.onApplyRangeSpeed(it) },
                        secondaryActionLabel = "복제",
                        onSecondaryAction = { viewModel.onDuplicateRange() },
                        onDelete = { viewModel.onDeleteRange() },
                        onCancel = { viewModel.onFinishSegmentEdit() },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                ) {
                    OutlinedButton(
                        // RUNNING 시에도 enable — 사용자가 진행 중 잡 외에 다른 구간 분리를 동시에 시작할 수 있어야 함.
                        enabled = firstSegId != null && !state.isSegmentEditMode,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = VibiShape.lg,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VibiSpacing.base, vertical = 0.dp),
                        onClick = {
                            val segId = firstSegId ?: return@OutlinedButton
                            when (state.separationStatus) {
                                AutoJobStatus.FAILED -> {
                                    viewModel.onClearSeparation()
                                    viewModel.onEnterRangeMode(segId)
                                }
                                else -> viewModel.onEnterRangeMode(segId)
                            }
                        }
                    ) { Text(sepLabel, style = typo.bodySm) }
                    // Box wrap — DropdownMenu (audioMenuOpen) 가 buttom 부모 박스를 anchor 로
                    // 위치 계산. 음원분리 버튼처럼 OutlinedButton 에 weight 만 걸면 메뉴 위치가 어긋남.
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            enabled = !state.isAddingBgm && !state.isSegmentEditMode,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = VibiShape.lg,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VibiSpacing.base, vertical = 0.dp),
                            onClick = {
                                if (recording) recorder.stop()
                                else audioMenuOpen = true
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        state.isAddingBgm -> "⏳ 추가 중"
                                        recording -> "⏹ 녹음 중"
                                        pendingRecord -> "⏳ 준비 중"
                                        else -> "음원 삽입"
                                    },
                                    style = typo.bodySm,
                                )
                                if (recording) {
                                    Spacer(Modifier.width(VibiSpacing.xs))
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .width(36.dp)
                                            .background(tokens.timelineBarTrack, VibiShape.xs),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(micLevel.coerceIn(0f, 1f))
                                                .background(tokens.accent, VibiShape.xs),
                                        )
                                    }
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = audioMenuOpen,
                            onDismissRequest = { audioMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("파일 업로드") },
                                onClick = {
                                    audioMenuOpen = false
                                    audioPicker.launch()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("즉시 녹음") },
                                onClick = {
                                    audioMenuOpen = false
                                    pendingRecord = true
                                    recorder.start()
                                },
                            )
                        }
                    }
                }
                // SoundDeck — 분리된 stem + BGM 을 세로 카드 스택으로. 기존 AudioSeparationSheet
                // 와 같은 state 를 공유하므로 한쪽 토글이 다른 쪽에도 즉시 반영.
                if (com.vibi.cmp.platform.RuntimeFlags.soundDeckEnabled) {
                    val deckGroups = remember(state.separationDirectives, state.bgmClips) {
                        com.vibi.cmp.ui.timeline.sounddeck.buildSoundDeckGroups(
                            separations = state.separationDirectives,
                            bgmClips = state.bgmClips,
                        )
                    }
                    val deckDisabled = state.audioSeparation?.step ==
                        com.vibi.shared.ui.timeline.AudioSeparationStep.PROCESSING ||
                        state.isLocalizationBusy()
                    Spacer(Modifier.height(VibiSpacing.xs))
                    com.vibi.cmp.ui.timeline.sounddeck.SoundDeck(
                        groups = deckGroups,
                        disabled = deckDisabled,
                        onToggleStem = { directiveId, stemId, selected ->
                            viewModel.onSetStemSelectionForDirective(directiveId, stemId, selected)
                        },
                        onUpdateStemVolume = { directiveId, stemId, volume ->
                            viewModel.onSetStemVolumeForDirective(directiveId, stemId, volume)
                        },
                        onUpdateBgmVolume = { clipId, v -> viewModel.onUpdateBgmVolume(clipId, v) },
                        onApplyBgmSpeed = { clipId, v -> viewModel.onApplyBgmClipSpeed(clipId, v) },
                        onRemoveBgmBackground = { clipId -> viewModel.onStartBgmSeparation(clipId) },
                        onDeleteBgm = { clipId -> viewModel.onDeleteBgmClip(clipId) },
                        // 분리/BGM 진입은 위 버튼 row 가 담당 — deck add 슬롯은 사용 안 함.
                        onAddSeparation = null,
                        onAddBgm = null,
                    )
                    // 영상 다듬기 진입 — stepper 가 사라진 unified 모드에선 본 카드가 유일한 진입점.
                    // 분리/자막/더빙 진행 중엔 disabled (commit 시 산출물 wipe 위험 방지).
                    if (unifiedScroll && !state.isSegmentEditMode && !state.isRangeSelecting) {
                        val firstSegIdForEdit = state.segments.firstOrNull {
                            it.type == com.vibi.shared.domain.model.SegmentType.VIDEO
                        }?.id
                        com.vibi.cmp.ui.timeline.sounddeck.EditEntryCard(
                            enabled = firstSegIdForEdit != null && !deckDisabled,
                            onClick = {
                                val segId = firstSegIdForEdit ?: return@EditEntryCard
                                viewModel.onEnterSegmentEditMode(segId)
                            },
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
            ) {
                // 자막/더빙 진입 버튼 제거 — 기능 비활성화.
                // 음원 단계 (음원분리 + 음원삽입) 는 위 Row 에서 이미 처리.
            }
        }

        // BGM panel 은 inline list 가 아닌, lane 의 막대 탭 → ModalBottomSheet 로 전환.
        // 아래 `state.selectedBgmClipId` 분기가 sheet 를 띄움.

        // 상세 편집 패널 — lang chip 으로 lang 필터 + cue list + 선택 cue 의 스타일/텍스트 조정.
        // 영상 미리보기 위에 자막 overlay 가 즉시 반영되어 사용자가 보면서 조정 가능.
        if (showSubtitleDubContent &&
            state.showDetailEdit && !state.isRangeSelecting && !state.localizationOpen) {
            DetailEditPanel(
                state = state,
                onSelectLang = { viewModel.onSetDetailEditLang(it) },
                onSelectClip = { viewModel.onSelectSubtitleClip(it) },
                onUpdateText = { id, text -> viewModel.onUpdateSubtitleText(id, text) },
                onUpdateStyle = { id, size, color, bg ->
                    viewModel.onUpdateSubtitleStyle(
                        clipId = id,
                        fontSizeSp = size,
                        colorHex = color,
                        backgroundColorHex = bg,
                        applyToAllLanguages = false,
                    )
                },
                onSeekToClip = { viewModel.onUpdatePlaybackPosition(it) },
            )
        }

        // 자막/더빙 생성 인라인 패널 — 자막/더빙 단계 + 영상편집 모드 아닐 때.
        if (showSubtitleDubContent &&
            state.localizationOpen && !state.isSegmentEditMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.panelBg, VibiShape.lg)
                    .padding(VibiSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("자막/더빙 생성", style = typo.titleSm, color = tokens.onBackgroundPrimary)
                // 모드 선택 (자막 / 더빙 — 둘 중 하나)
                Row(horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
                    FilterChip(
                        selected = state.localizationMode == "subtitle",
                        onClick = { viewModel.onSetLocalizationMode("subtitle") },
                        label = { Text("자막") }
                    )
                    FilterChip(
                        selected = state.localizationMode == "dub",
                        onClick = { viewModel.onSetLocalizationMode("dub") },
                        label = { Text("더빙") }
                    )
                }

                // 원본 언어는 Perso STT 가 자동 감지함 — 사용자 선택 불필요.

                // 자막 모드일 때 chip row 의 첫 항목에 "원본" chip 추가 (lang code "" sentinel).
                // 다른 lang chip 과 함께 다중 선택 가능 — 원본만 선택 시 STT only, 다른 lang 도 선택 시 번역 + 원본.
                // 더빙 모드는 원본 더빙 미지원 (Perso 가 source==target 안 받음) 이라 원본 chip 안 보임.
                Text("자막/더빙 언어 (다중)", style = typo.bodySm, color = tokens.mutedText)
                // chip enable/disable 정책: 이미 생성된 lang(자막 clip 또는 더빙 mp3/mp4 존재) 또는
                // 진행 중인 lang 은 chip 자체를 disabled + ✓ 아이콘으로 표시 → 중복 호출 차단.
                // 자막 모드의 RUNNING 신호는 per-lang 추적이 없어 보수적으로 "어떤 자막 잡이라도 RUNNING"
                // 이면 모든 자막 lang chip disabled (race 회피). 더빙은 autoDubStatusByLang 로 per-lang 식별.
                val anySubtitleRunning = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
                    state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
                    state.sttPreflightStatus == AutoJobStatus.RUNNING
                // "" sentinel = 원본 자막 — 변환된 lang clip 들도 default languageCode="" 로 만들어질 수
                // 있어 단순 any { languageCode == "" } 가 false-positive. SSOT 인 hasOriginalSubtitleVariant
                // (observeProject 가 hasConfirmedOriginalSubtitle 결과로 set) 로 판정.
                fun isChipBlocked(code: String): Boolean = when (state.localizationMode) {
                    "subtitle" -> {
                        val alreadyDone = if (code.isEmpty()) state.hasOriginalSubtitleVariant
                            else state.subtitleClips.any { it.languageCode == code }
                        alreadyDone || anySubtitleRunning
                    }
                    "dub" -> {
                        val alreadyDone = state.dubbedAudioPaths.containsKey(code) ||
                            state.dubbedVideoPaths.containsKey(code)
                        val running = state.autoDubStatusByLang[code] == AutoJobStatus.RUNNING
                        alreadyDone || running
                    }
                    else -> false
                }
                fun isChipDone(code: String): Boolean = when (state.localizationMode) {
                    "subtitle" -> if (code.isEmpty()) state.hasOriginalSubtitleVariant
                        else state.subtitleClips.any { it.languageCode == code }
                    "dub" -> state.dubbedAudioPaths.containsKey(code) ||
                        state.dubbedVideoPaths.containsKey(code)
                    else -> false
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
                ) {
                    if (state.localizationMode == "subtitle") {
                        val originalBlocked = isChipBlocked("")
                        val originalDone = isChipDone("")
                        LangFilterChip(
                            label = "원본",
                            selected = "" in state.localizationLangs || originalDone,
                            enabled = !originalBlocked,
                            onClick = { viewModel.onToggleLocalizationLang("") },
                        )
                    }
                    listOf(
                        "en" to "English",
                        "ko" to "한국어",
                        "ja" to "日本語",
                        "zh" to "中文",
                        "es" to "Español",
                        "fr" to "Français",
                        "de" to "Deutsch"
                    ).forEach { (code, label) ->
                        val blocked = isChipBlocked(code)
                        val done = isChipDone(code)
                        LangFilterChip(
                            label = label,
                            selected = code in state.localizationLangs || done,
                            enabled = !blocked,
                            onClick = { viewModel.onToggleLocalizationLang(code) },
                        )
                    }
                }
                // 자막 모드 한정: STT 결과 미리 검토 후 다국어 자막 생성. dub 는 BFF 추가 필요해 현재 미지원.
                if (state.localizationMode == "subtitle") {
                    FilterChip(
                        selected = state.reviewScriptBeforeGenerate,
                        onClick = { viewModel.onToggleReviewScriptBeforeGenerate() },
                        label = { Text("📝 스크립트 먼저 검토") }
                    )
                }
                // "생성 시작" enable 조건: 자막/번역 lang 1개 이상 선택 (자막 모드면 "원본" chip 도 포함).
                val startEnabled = state.localizationLangs.isNotEmpty()
                // 진행 단계별 라벨 (단일 버튼 내):
                //  편집 영상 render 중 → "편집 영상 준비 중 (XX%)"
                //  STT/번역/더빙 진행 중 → "자막/더빙 생성 중"
                //  자막 review pending (script ready) → "스크립트 생성 완료" → review sheet 재오픈
                //  idle → "생성 시작"
                val renderProgress = state.editedVideoRenderProgress
                val isJobRunning = state.sttPreflightStatus == AutoJobStatus.RUNNING ||
                    state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
                    state.autoDubStatus == AutoJobStatus.RUNNING
                val isGenerating = renderProgress != null || isJobRunning
                val showReviewReady = state.localizationMode == "subtitle" &&
                    state.subtitleReviewPending &&
                    !isGenerating
                val (buttonLabel, buttonEnabled, buttonOnClick) = when {
                    renderProgress != null -> Triple<String, Boolean, () -> Unit>(
                        "편집 영상 준비 중 ($renderProgress%)",
                        false,
                        { /* no-op */ },
                    )
                    isJobRunning -> Triple<String, Boolean, () -> Unit>(
                        "자막/더빙 생성 중",
                        false,
                        { /* no-op */ },
                    )
                    showReviewReady -> Triple<String, Boolean, () -> Unit>(
                        "스크립트 생성 완료",
                        true,
                        { viewModel.onReopenScriptReviewSheet() },
                    )
                    else -> Triple<String, Boolean, () -> Unit>(
                        "생성 시작",
                        startEnabled,
                        { viewModel.onStartLocalization() },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = buttonEnabled,
                        onClick = buttonOnClick
                    ) { Text(buttonLabel) }
                    OutlinedButton(onClick = { viewModel.onDismissLocalization() }) {
                        Text("닫기")
                    }
                }
            }
        }

        Spacer(Modifier.height(VibiSpacing.xs))

        // 편집 영상 render 진행률 + 자막/더빙 생성 진행 상태는 위 "생성 시작" 버튼 라벨에 통합.
        // 별도 progress card 폐기.

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
    }

    // 하단 A/B 미리듣기 바 가시성 — FAB 위치 계산이 이 플래그에 의존하므로 FAB 보다 먼저 계산.
    // directive 유무와 무관하게 항상 노출 — 사용자가 분리 전에도 토글 affordance 를 인지하도록.
    val abBarVisible = unifiedScroll &&
        !state.isSegmentEditMode &&
        !state.localizationOpen &&
        !state.showDetailEdit &&
        !state.showAudioSeparationSheet &&
        !state.showSubtitleSheet &&
        !state.showScriptReviewSheet &&
        !state.showAppendSheet &&
        !state.showFrameSheet &&
        !state.showTextOverlaySheet

    // 채팅 어시스턴트 FAB — 메인 타임라인 뷰 전용. range/segment edit/패널/시트 활성 시 숨김.
    // 자막/더빙 단계에서는 노출 안 함. unified 모드(stepper 숨김) 에선 단계 구분 없음 — localizationOpen
    // 같은 다른 가시성 가드가 이미 자막/더빙 패널 열린 시간 동안 FAB 를 가린다.
    val chatFabVisible = (unifiedScroll || state.currentStep != TimelineStep.SubtitleDub) &&
        !state.isSegmentEditMode &&
        !state.isRangeSelecting &&
        !state.localizationOpen &&
        !state.showDetailEdit &&
        !state.showAudioSeparationSheet &&
        !state.showSubtitleSheet &&
        !state.showScriptReviewSheet &&
        !state.showAppendSheet &&
        !state.showFrameSheet &&
        !state.showTextOverlaySheet &&
        !chatSheetVisible
    if (chatFabVisible) {
        // AB 바 노출 시 FAB 가 가리지 않도록 위로 들어올림. AB 바 높이 = chip(xxl 40dp) + 내부 padding(xxs*2 8dp) + 외부 padding(xxs*2 8dp) ≈ 56dp.
        // AB 바 위 여유 간격은 xs(8dp) — 이전 md(20dp) 보다 12dp 줄여 FAB 가 화면 하단에 더 가깝게.
        val fabBottomPadding = if (abBarVisible) VibiSpacing.xs + VibiSpacing.xxl + VibiSpacing.base else VibiSpacing.md
        BadgedBox(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(start = VibiSpacing.md, end = VibiSpacing.md, top = VibiSpacing.md, bottom = fabBottomPadding),
            badge = {
                if (chatState.hasUnreadMessages) {
                    // AI 메시지 도착 신호 — 기본 Material3 Badge 의 6dp dot 은 너무 작아 멀리서 안 보임.
                    // 16dp 빨간 원으로 키워서 알아보기 쉽게.
                    Box(
                        modifier = Modifier
                            .size(VibiSpacing.base)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
            },
        ) {
            FloatingActionButton(
                onClick = { chatSheetVisible = true },
                containerColor = tokens.accent,
                contentColor = tokens.backgroundPrimary,
            ) {
                Text("Chat", fontSize = 22.sp)
            }
        }
    }

    // 하단 고정 A/B 미리듣기 바 — directive 가 1 개 이상이고 다른 패널 안 떠 있을 때만 노출.
    // ChatFab 위가 아닌 화면 하단(navigationBarsPadding 영역 위) 고정. abBarVisible 은 FAB 위치
    // 계산을 위해 위에서 미리 선언.
    if (abBarVisible) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            com.vibi.cmp.ui.timeline.sounddeck.ABPreviewBar(
                mode = state.previewMode,
                onToggle = { viewModel.onTogglePreviewMode() },
            )
        }
    }
    } // close Box wrapper

    // STT 스크립트 검토 sheet — review 모드에서 STT 완료 후 표시. 영상편집 모드 진행 중엔 숨김.
    if (state.showScriptReviewSheet && !state.isSegmentEditMode) {
        val sourceClips = remember(state.subtitleClips) {
            state.subtitleClips.filter { it.languageCode.isBlank() }
        }
        // confirm 시 일괄 저장하기 위해 outer 에서 hoist — text/confirmButton 두 슬롯이 같은 map 공유.
        val scriptReviewEdits = remember(sourceClips.map { it.id }) {
            androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
                sourceClips.forEach { put(it.id, it.text) }
            }
        }
        AlertDialog(
            onDismissRequest = { viewModel.onDismissScriptReviewSheet() },
            title = { Text("스크립트 검토") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
                    Text(
                        "STT 결과를 확인하고 잘못 인식된 부분을 수정하세요. 확인 후 ${state.pendingReviewTargetLangs.joinToString(", ") { it.uppercase() }} 자막이 생성됩니다.",
                        style = typo.bodySm
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
                    ) {
                        items(items = sourceClips, key = { it.id }) { clip ->
                            val draft = scriptReviewEdits[clip.id] ?: clip.text
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs)
                            ) {
                                Text(
                                    text = "${clip.startMs / 1000}s\n${clip.endMs / 1000}s",
                                    style = typo.caption,
                                    modifier = Modifier.heightIn(min = 56.dp)
                                )
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { scriptReviewEdits[clip.id] = it },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.onConfirmScriptReview(scriptReviewEdits.toMap())
                }) { Text("자막 생성") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissScriptReviewSheet() }) { Text("취소") }
            }
        )
    }


    // 자막 sheet — 자막/더빙 단계 + 영상편집 모드 아닐 때만.
    if (showSubtitleDubContent &&
        state.showSubtitleSheet && !state.isSegmentEditMode) {
        InsertSubtitleSheet(
            playbackPositionMs = state.playbackPositionMs,
            videoDurationMs = state.videoDurationMs,
            onConfirm = { text, startMs, endMs, position, style ->
                viewModel.onAddSubtitle(
                    text = text,
                    startMs = startMs,
                    endMs = endMs,
                    position = position,
                    fontFamily = style.fontFamily,
                    fontSizeSp = style.fontSizeSp,
                    colorHex = style.colorHex,
                    backgroundColorHex = style.backgroundColorHex,
                )
            },
            onDismiss = { viewModel.onDismissSubtitleSheet() }
        )
    }

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

    // BGM 클립 액션 sheet — 음원 단계에서 lane 의 막대를 탭했을 때 selectedBgmClipId 가 set 되면 표시.
    // segment edit 중에는 selectedBgmClipId 가 시각 highlight 용으로만 set 되므로 sheet 안 띄움.
    if (showAudioSourcesContent && !state.isSegmentEditMode) {
        state.bgmClips.firstOrNull { it.id == state.selectedBgmClipId }?.let { selectedClip ->
            BgmActionSheet(
                clip = selectedClip,
                onUpdateVolume = { v -> viewModel.onUpdateBgmVolume(selectedClip.id, v) },
                onUpdateSpeed = { s -> viewModel.onUpdateBgmSpeed(selectedClip.id, s) },
                onSeparate = { viewModel.onStartBgmSeparation(selectedClip.id) },
                onDelete = { viewModel.onDeleteBgmClip(selectedClip.id) },
                onDismiss = { viewModel.onSelectBgmClip(null) },
            )
        }
    }

    // 음성분리 sheet — 음원 단계 + 영상편집 모드 아닐 때만.
    state.audioSeparation
        ?.takeIf {
            showAudioSourcesContent &&
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
        )
    }

    // 구간 선택 — popup sheet 대신 인라인 RangeSlider 로 처리하므로 sheet 제거

    // 저장/공유 variant picker sheet — variant 2+ 일 때만 ViewModel 이 state set.
    state.exportVariantPicker?.let { picker ->
        ExportVariantPickerSheet(
            picker = picker,
            onToggleSave = { key -> viewModel.onToggleSavePickerVariant(key) },
            onToggleShare = { key -> viewModel.onToggleSharePickerVariant(key) },
            onConfirm = {
                when (picker) {
                    is com.vibi.shared.ui.timeline.ExportVariantPickerState.Save ->
                        viewModel.onConfirmSavePicker()
                    is com.vibi.shared.ui.timeline.ExportVariantPickerState.Share ->
                        viewModel.onConfirmSharePicker()
                }
            },
            onCancel = { viewModel.onCancelExportVariantPicker() },
        )
    }

    // stepper 단계 전환 경고 다이얼로그 — onSelectStep 가 set 한 보류 경고를 사용자에게 보여줌.
    // "다시 묻지 않기" 체크 시 UserPreferencesStore 에 영속 → 다음 세션에도 안 뜸.
    state.pendingStepWarning?.let { warning ->
        StepTransitionWarningDialog(
            warning = warning,
            onConfirm = { dontAskAgain ->
                viewModel.confirmStepTransition(dontAskAgain)
            },
            onDismiss = { viewModel.dismissStepTransitionWarning() },
        )
    }

    // 채팅 어시스턴트 sheet — 영상편집 모드와 무관하게 띄울 수 있지만 위 FAB 가 모드 중엔 숨김.
    if (chatSheetVisible) {
        ChatPanel(
            timelineState = state,
            onDismiss = { chatSheetVisible = false },
            chatVm = chatViewModel,
        )
    }

}

private val TimelineStep.label: String
    get() = when (this) {
        TimelineStep.EditAudio -> "편집·음원"
        TimelineStep.SubtitleDub -> "자막/더빙"
    }

/**
 * 3단계 stepper row — 라벨 + 노드 + 커넥터. 노드 안 아이콘은 현재 단계 기준 방향 표시:
 * 과거(왼쪽)는 ← (ArrowBack), 미래(오른쪽)는 → (ArrowForward), 현재는 빈 원.
 * 모든 노드(현재 제외) 클릭으로 [TimelineViewModel.onSelectStep] — 양방향 즉시 이동 (산출물·undo 보존).
 */
@Composable
private fun TimelineStepperRow(
    currentStep: TimelineStep,
    onStepClick: (TimelineStep) -> Unit,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    // 자막/더빙 탭 숨김 — 기능 비활성화.
    val steps = TimelineStep.entries.filterNot { it == TimelineStep.SubtitleDub }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = VibiSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val isCurrent = step == currentStep
            val isPast = step.ordinal < currentStep.ordinal
            val nodeColor = when {
                isCurrent -> tokens.accent
                isPast -> tokens.accent.copy(alpha = 0.5f)
                else -> tokens.chipBgDisabled
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isCurrent) { onStepClick(step) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier.size(VibiSpacing.lg).clip(CircleShape).background(nodeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isCurrent) {
                        Icon(
                            imageVector = if (isPast) Icons.AutoMirrored.Filled.ArrowBack
                                          else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = if (isPast) "이전 단계로" else "다음 단계로",
                            tint = if (isPast) tokens.backgroundPrimary else tokens.onBackgroundPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(step.label, fontSize = 11.sp, color = tokens.onBackgroundPrimary)
            }
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .height(1.dp)
                        .background(
                            if (currentStep.ordinal > index) tokens.accent.copy(alpha = 0.5f)
                            else tokens.chipBgDisabled
                        ),
                )
            }
        }
    }
}

/**
 * 자막/더빙 생성 패널의 lang chip — 이미 생성된(done) lang 은 호출부에서 `selected=true, enabled=false`
 * 로 넘겨 selected 색칠 + 클릭 비활성. blocked 인 동안 onClick 비활성 (FilterChip enabled=false).
 */
@Composable
private fun LangFilterChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
    )
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
    val WaveformHeight = 40.dp
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
}

/**
 * UnifiedTimelineBar 에 전달되는 in-flight 음원분리 overlay 1건. ViewModel 의
 * [com.vibi.shared.ui.timeline.ProcessingSeparation] 가 UI 좌표로 펴진 형태.
 */
data class ProcessingSeparationOverlay(
    val startMs: Long,
    val endMs: Long,
    val progress: Int,
)

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
    onSegmentTap: (String) -> Unit = {},
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
    bgmLaneCount: Int = 1,
    selectedBgmClipId: String? = null,
    bgmTapEnabled: Boolean = true,
    /** false 면 BGM clip 의 위치/lane drag + lane 수 조절 pill 까지 disable. 탭(선택)은 분리 — `bgmTapEnabled` 가 담당.
     *  segment edit (영상 다듬기) 모드에서 BGM 트랙을 read-only 로 잠그기 위함. */
    bgmDragEnabled: Boolean = true,
    onBgmSelectClip: (String) -> Unit = {},
    onBgmUpdateStart: (String, Long) -> Unit = { _, _ -> },
    onBgmUpdateLane: (String, Int) -> Unit = { _, _ -> },
    onBgmSetLaneCount: (Int) -> Unit = {},
    showBgm: Boolean = false,
    /** true 면 영상 strip 의 range overlay 숨기고 BGM lane 에 range fill 노출. mutual exclusion. */
    bgmRangeMode: Boolean = false,
) {
    val density = LocalDensity.current
    val currentRangeStart by rememberUpdatedState(rangeStartMs)
    val currentRangeEnd by rememberUpdatedState(rangeEndMs)

    val playbackRegionHeight = TimelineBarSpec.BarHeight
    val contentHeight = TimelineBarSpec.ContentHeight
    val handleHitWidth = TimelineBarSpec.HandleHitWidth
    val handleVisualWidth = TimelineBarSpec.HandleVisualWidth

    // BGM region metric — clips 가 있을 때만 렌더. 없으면 전체 높이 = playbackRegionHeight (기존과 동일).
    val showBgmRegion = showBgm && bgmClips.isNotEmpty() && totalMs > 0L
    val bgmRowHeight = 10.dp
    val bgmRowGap = 2.dp
    val bgmRowCount = bgmLaneCount.coerceAtLeast(1)
    val bgmRegionHeight = if (showBgmRegion) {
        bgmRowHeight * bgmRowCount + bgmRowGap * (bgmRowCount - 1).coerceAtLeast(0)
    } else 0.dp
    val bgmRowStrideDp = bgmRowHeight + bgmRowGap
    val bgmRowStridePx = with(density) { bgmRowStrideDp.toPx() }
    // pill 미렌더 시 hit zone 도 0 으로 — 빈 22dp 공간이 timeline 하단에 dead 영역으로 남는 것 방지.
    val laneHandleHitHeight = if (showBgmRegion && bgmDragEnabled) 22.dp else 0.dp
    val laneHandleVisualHeight = 6.dp
    val playheadVisualBottom = playbackRegionHeight + bgmRegionHeight
    val totalHeight = playheadVisualBottom + laneHandleHitHeight

    // range 모드 (영상편집 + 음원분리) 양쪽 다 parent tap detector 단일화. segment 자체에 clickable
    // 두지 않고 ms 좌표로 segment id 를 역검색 → onSegmentTap 호출. 같은 segment 재탭 시 VM 의
    // onSelectSegmentInEdit 토글 로직이 처리. modifier 는 top playback region 안쪽에 부착해 BGM
    // lane drag 와 분리 — 빈 영역 탭은 영상 timeline 위에서만 잡힌다.
    val currentStartForTap by rememberUpdatedState(rangeStartMs)
    val currentEndForTap by rememberUpdatedState(rangeEndMs)
    // pointerInput key — progress 변경 (1~2초 폴링) 마다 processingSeparations ref 가 새로 생성되지만
    // tap 검출은 start/end 만 보므로 그 둘만 추출. equals 가 stable 이라 polling tick 동안 gesture
    // detector 가 재등록되지 않음.
    val processingRangesKey = processingSeparations.map { it.startMs to it.endMs }
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
                                onSegmentTap(seg.id)
                            }
                            return@detectTapGestures
                        }
                        acc = nextAcc
                    }
                    return@detectTapGestures
                }

                // 음원분리: directive / 진행 중 분리 위 ignore, 선택 영역 재탭 → toggle, 그 외 free interval → snap.
                // committed directive + 진행 중 잡 (processingSeparations) 모두 동일하게 점유로 취급해
                // 사용자가 분리 중인 구간을 탭/range 로 다시 잡지 못한다.
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
                onFreeIntervalTap(freeStart, freeEnd)
            })
        }
    } else Modifier

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(trackColor.copy(alpha = 0.45f))
    ) {
        val totalWidthDp = maxWidth
        val totalWidthPx = with(density) { totalWidthDp.toPx() }

        // === 상단 playback region — 기존 단일 바 56dp 의 모든 시각/제스처가 여기 안에서 동작 ===
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(playbackRegionHeight)
                .then(rangeTapModifier),
        ) {
            // Layer 1 — 영상 audio 파형. 영상편집 모드에서도 동일하게 — 사용자가 audio 위치를 보면서
            // 구간을 잡도록. segment 의 volume/speed 변경은 파형 bar 높이/밀도에 이미 반영됨
            // (TimelineWaveformBackground 가 seg.volumeScale, speedScale 을 곱해 매핑).
            // peaks 비면 (Android stub / 추출 실패) 회색 strip + directive 막대 fallback.
            val hasWaveform = videoPeaks.isNotEmpty() && totalMs > 0L
            if (hasWaveform) {
                    TimelineWaveformBackground(
                        sourcePeaks = videoPeaks,
                        segments = segments,
                        primarySourceUri = primarySourceUri,
                        sourceDurationMs = primarySourceDurationMs,
                        totalMs = totalMs,
                        directives = directives,
                        stemPeaksByUrl = stemPeaksByUrl,
                        defaultBarColor = markerColor.copy(alpha = 0.45f),
                        highlightBarColor = accent,
                        trackBg = trackColor.copy(alpha = 0.55f),
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
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(contentHeight)
                            .clip(RoundedCornerShape(TimelineBarSpec.ContentCornerRadius))
                            .background(trackColor)
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

            // 진행 중인 음원분리 overlay — directive 막대(짙은 회색) 와 다른 accent 컬러로 구별.
            // showRange / showSegments 와 무관하게 노출 — 어느 모드에서든 처리 중 구간 인지 가능. 클릭 비활성.
            if (totalMs > 0L) {
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
                            .height(contentHeight)
                            .align(Alignment.CenterStart)
                            .background(accent.copy(alpha = 0.25f))
                    ) {
                        val fill = (p.progress.coerceIn(0, 100)) / 100f
                        if (fill > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fill)
                                    .background(accent.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }

            // Layer 2 — 회색 타임라인 strip(=contentHeight 12dp)에 정렬. 트림 핸들도 동일 높이.
            // tap absorber 없음 → 자식 segment.clickable / parent free-interval tap 모두 살림.
            // rangeEndMs <= rangeStartMs (zero-width) = "선택 없음" 상태 → range 시각 모두 숨김 (mode 유지).
            // bgmRangeMode 면 영상 strip 의 fill/handles 안 그림 — BGM lane 에 같은 시각이 노출.
            if (showRange && totalMs > 0L && rangeEndMs > rangeStartMs && !bgmRangeMode) {
                val startFrac = (rangeStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                val endFrac = (rangeEndMs.toFloat() / totalMs).coerceIn(0f, 1f)
                val rangeStartDp = totalWidthDp * startFrac
                val rangeWidthDp = totalWidthDp * (endFrac - startFrac).coerceAtLeast(0f)
                // 회색 strip 높이 = contentHeight. 위/아래 inset = (playbackRegionHeight - contentHeight) / 2.
                val rangeStripInsetY = (playbackRegionHeight - contentHeight) / 2

                var fillBaseStartMs by remember { mutableStateOf(0L) }
                var fillAccumPx by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .offset(x = rangeStartDp)
                        .width(rangeWidthDp)
                        .height(contentHeight)
                        .align(Alignment.CenterStart)
                        .background(accent.copy(alpha = 0.32f))
                        .pointerInput(totalWidthPx, totalMs) {
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
                        }
                )
                // 상단/하단 border — 회색 strip 의 위/아래 모서리에 정렬.
                Box(
                    modifier = Modifier
                        .offset(x = rangeStartDp, y = rangeStripInsetY)
                        .width(rangeWidthDp)
                        .height(TimelineBarSpec.RangeBorderThickness)
                        .align(Alignment.TopStart)
                        .background(accent)
                )
                Box(
                    modifier = Modifier
                        .offset(x = rangeStartDp, y = -rangeStripInsetY)
                        .width(rangeWidthDp)
                        .height(TimelineBarSpec.RangeBorderThickness)
                        .align(Alignment.BottomStart)
                        .background(accent)
                )

                // 좌/우 bracket 핸들 — playback region 높이만큼 hit zone (BGM lane drag 와 충돌 회피).
                val minGap = TimelineBarSpec.MinRangeGapMs
                RangeHandle(
                    offsetX = rangeStartDp - handleHitWidth / 2,
                    hitWidth = handleHitWidth,
                    hitHeight = playbackRegionHeight,
                    visualWidth = handleVisualWidth,
                    handleColor = accent,
                    gripColor = markerColor,
                    gripHeight = contentHeight,
                    totalWidthPx = totalWidthPx,
                    totalMs = totalMs,
                    baseMsProvider = { currentRangeStart },
                    clamp = { it.coerceIn(0L, (currentRangeEnd - minGap).coerceAtLeast(0L)) },
                    onChange = onRangeStartChange,
                )
                RangeHandle(
                    offsetX = rangeStartDp + rangeWidthDp - handleHitWidth / 2,
                    hitWidth = handleHitWidth,
                    hitHeight = playbackRegionHeight,
                    visualWidth = handleVisualWidth,
                    handleColor = accent,
                    gripColor = markerColor,
                    gripHeight = contentHeight,
                    totalWidthPx = totalWidthPx,
                    totalMs = totalMs,
                    baseMsProvider = { currentRangeEnd },
                    clamp = { it.coerceIn((currentRangeStart + minGap).coerceAtMost(totalMs), totalMs) },
                    onChange = onRangeEndChange,
                )
            }

            // Layer 3 — 재생 헤드 drag hit zone — top playback region 안쪽에만. 시각 line 은 BGM 까지 관통.
            // 클램프 제거: hit zone 은 frac 위치 정확히 중심에 두고 좌우 끝에서 hit zone 일부가 바 밖으로
            // overflow 해도 OK (시각 marker line 은 항상 frac 위치 = 영상 길이와 정확히 일치).
            if (totalMs > 0L) {
                val frac = (playbackPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                val hitWidth = TimelineBarSpec.PlaybackHitWidth
                val visualX = totalWidthDp * frac - hitWidth / 2
                val currentPosMs by rememberUpdatedState(playbackPositionMs)
                var basePosMs by remember { mutableStateOf(0L) }
                var accumPx by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .offset(x = visualX)
                        .width(hitWidth)
                        .fillMaxHeight()
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
        }

        // === BGM region — top playback region 바로 아래. 같은 x 좌표계로 lane 막대 렌더 ===
        if (showBgmRegion) {
            val laneWidthDp = totalWidthDp
            val laneWidthPx = totalWidthPx
            val currentRowCount by rememberUpdatedState(bgmRowCount)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = playbackRegionHeight)
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
                val bgmLaneYDp = bgmRowStrideDp * (selectedBgmForOverlay?.lane?.coerceAtLeast(0) ?: 0)
                if (bgmRangeMode && showRange && rangeEndMs > rangeStartMs) {
                    var bgmFillBaseStartMs by remember { mutableStateOf(0L) }
                    var bgmFillAccumPx by remember { mutableStateOf(0f) }
                    // 평행 이동도 선택된 BGM 의 bounds 안에서만 — 영상 timeline 전체로 슬라이드되지 않도록.
                    val translateMin = selectedBgmForOverlay?.startMs ?: 0L
                    val translateMaxStart = selectedBgmForOverlay?.let {
                        (it.startMs + it.effectiveDurationMs - (rangeEndMs - rangeStartMs)).coerceAtLeast(it.startMs)
                    } ?: (totalMs - (rangeEndMs - rangeStartMs)).coerceAtLeast(0L)
                    Box(
                        modifier = Modifier
                            .offset(x = bgmFillStartDp, y = bgmLaneYDp)
                            .width(bgmFillWidthDp)
                            .height(bgmRowHeight)
                            .background(accent.copy(alpha = 0.32f))
                            .pointerInput(totalWidthPx, totalMs, translateMin, translateMaxStart) {
                                // fill drag → 영상 strip 의 onTranslateRange 와 동일 — range 전체 평행 이동.
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        bgmFillBaseStartMs = currentRangeStart
                                        bgmFillAccumPx = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        bgmFillAccumPx += dragAmount
                                        if (totalWidthPx > 0f && totalMs > 0L) {
                                            val deltaMs = (bgmFillAccumPx / totalWidthPx) * totalMs
                                            val newStart = (bgmFillBaseStartMs + deltaMs).toLong()
                                                .coerceIn(translateMin, translateMaxStart)
                                            onTranslateRange(newStart)
                                        }
                                    }
                                )
                            }
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = bgmFillStartDp, y = bgmLaneYDp)
                            .width(bgmFillWidthDp)
                            .height(TimelineBarSpec.RangeBorderThickness)
                            .background(accent)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = bgmFillStartDp, y = bgmLaneYDp + bgmRowHeight - TimelineBarSpec.RangeBorderThickness)
                            .width(bgmFillWidthDp)
                            .height(TimelineBarSpec.RangeBorderThickness)
                            .background(accent)
                    )
                }
                bgmClips.forEach { clip ->
                    // effectiveDurationMs 는 trim (sourceTrimStart/End) + speed 모두 반영 — 시각 막대 길이를
                    // BGM 의 실제 timeline 점유와 일치시켜야 사용자가 trim 결과를 즉시 확인 가능.
                    val globalDurMs = clip.effectiveDurationMs.coerceAtLeast(1L)
                    val isSelected = clip.id == selectedBgmClipId
                    // 드래그 중에는 local override 만 갱신 → 시각이 손가락 따라 즉시. drag end 시점에 한 번만
                    // VM commit. 매 tick 마다 DB write/Flow emit 하던 lag 제거.
                    var dragOverrideMs by remember(clip.id) { mutableStateOf<Long?>(null) }
                    var dragBaseStartMs by remember(clip.id) { mutableStateOf(0L) }
                    var dragAccumPx by remember(clip.id) { mutableStateOf(0f) }
                    // lane (vertical) override 도 동일 패턴 — drag 중 시각만, release 시 commit.
                    var laneOverride by remember(clip.id) { mutableStateOf<Int?>(null) }
                    var laneBase by remember(clip.id) { mutableStateOf(0) }
                    var dragAccumPyAbs by remember(clip.id) { mutableStateOf(0f) }
                    // pointerInput 의 코루틴 closure 가 forEach 의 옛 clip 을 capture 하지 않도록
                    // 항상 최신 clip 을 참조 — 특히 lane/startMs 가 ViewModel 측 갱신 후 onDragStart
                    // 의 base 값으로 stale 한 옛 lane 을 잡던 버그 방지.
                    val currentClip by rememberUpdatedState(clip)
                    val effectiveStartMs = dragOverrideMs ?: clip.startMs
                    val effectiveLane = (laneOverride ?: clip.lane).coerceAtLeast(0)
                    val startFrac = (effectiveStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
                    val widthFrac = (globalDurMs.toFloat() / totalMs).coerceIn(0f, 1f - startFrac)
                    val offsetXDp = laneWidthDp * startFrac
                    val offsetYDp = bgmRowStrideDp * effectiveLane
                    val widthDp = (laneWidthDp * widthFrac).coerceAtLeast(6.dp)
                    val isMuted = clip.volumeScale <= 0f
                    Box(
                        modifier = Modifier
                            .offset(x = offsetXDp, y = offsetYDp)
                            .width(widthDp)
                            .height(bgmRowHeight)
                            .clip(VibiShape.xs)
                            // selected 상태로 색을 진하게 하지 않음 — 사용자가 강렬한 시각 변화로 산만함을 느낀다는 피드백.
                            // 선택 시각은 BGM lane 의 range overlay (fill+border+handles) 가 단독으로 담당.
                            .background(
                                if (isMuted) markerColor.copy(alpha = 0.25f)
                                else accent.copy(alpha = 0.55f)
                            )
                            .pointerInput(clip.id, bgmTapEnabled) {
                                // tap 은 BGM 바 자체 영역 (= 10dp × clip width) 에서만 인식 — lane 의
                                // 빈 공간 (행 사이 gap, BGM 없는 x 구간) 은 탭 안 됨.
                                detectTapGestures(onTap = {
                                    if (bgmTapEnabled) onBgmSelectClip(clip.id)
                                })
                            }
                            .pointerInput(clip.id, totalMs, laneWidthPx, bgmRowStridePx, bgmDragEnabled) {
                                // segment edit 모드에선 drag 자체를 미장착 — pointerInput key 에 포함해
                                // 모드 진입/이탈 시 detector 재등록.
                                if (!bgmDragEnabled) return@pointerInput
                                // detectDragGestures 로 단일 제스처에서 dx/dy 를 모두 받음.
                                // dy → lane 변경, dx → startMs 변경. 각각 별도 accumulator.
                                detectDragGestures(
                                    onDragStart = {
                                        dragBaseStartMs = currentClip.startMs
                                        dragAccumPx = 0f
                                        dragOverrideMs = currentClip.startMs
                                        laneBase = currentClip.lane.coerceAtLeast(0)
                                        dragAccumPyAbs = 0f
                                        laneOverride = laneBase
                                    },
                                    onDrag = { change: PointerInputChange, drag: Offset ->
                                        change.consume()
                                        // X axis — startMs.
                                        dragAccumPx += drag.x
                                        if (laneWidthPx > 0f && totalMs > 0L) {
                                            val deltaMs = (dragAccumPx / laneWidthPx) * totalMs
                                            val maxStart = (totalMs - globalDurMs).coerceAtLeast(0L)
                                            dragOverrideMs = (dragBaseStartMs + deltaMs).toLong()
                                                .coerceIn(0L, maxStart)
                                        }
                                        // Y axis — lane. row 높이 단위로 step. 위로 끌면 lane 감소,
                                        // 아래로 끌면 증가. 영역 밖 (0 미만 / rowCount-1 초과) 으로는 못 끌림.
                                        dragAccumPyAbs += drag.y
                                        if (bgmRowStridePx > 0f) {
                                            val laneDelta = (dragAccumPyAbs / bgmRowStridePx).toInt()
                                            laneOverride = (laneBase + laneDelta).coerceIn(0, currentRowCount - 1)
                                        }
                                    },
                                    onDragEnd = {
                                        dragOverrideMs?.let { onBgmUpdateStart(currentClip.id, it) }
                                        laneOverride?.let { newLane ->
                                            if (newLane != currentClip.lane) onBgmUpdateLane(currentClip.id, newLane)
                                        }
                                        dragOverrideMs = null
                                        laneOverride = null
                                    },
                                    onDragCancel = {
                                        dragOverrideMs = null
                                        laneOverride = null
                                    },
                                )
                            },
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
                    )
                }
            }
        }

        // === 재생 헤드 시각 line — top + BGM region 관통. drag 안 받음 ===
        // marker visual 길이 = (playback + BGM) - 16dp (위/아래 8dp inset). BGM 없을 때는 기존 동작 동일.
        if (totalMs > 0L) {
            val frac = (playbackPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val visualX = totalWidthDp * frac - TimelineBarSpec.GripWidth / 2
            val topInset = TimelineBarSpec.PlaybackMarkerVerticalInset / 2
            val markerHeight = (playheadVisualBottom - TimelineBarSpec.PlaybackMarkerVerticalInset)
                .coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = visualX, y = topInset)
                    .width(TimelineBarSpec.GripWidth)
                    .height(markerHeight)
                    .background(markerColor)
            )
        }

        // === BGM lane 수 조절 drag pill — 컴포넌트 최하단. dy / rowStride 단위 step ===
        // bgmDragEnabled=false (segment edit 모드) 면 pill 자체 미렌더 → 시각 + 인터랙션 동시 잠금.
        if (showBgmRegion && bgmDragEnabled) {
            val maxOccupiedLane = bgmClips.maxOfOrNull { it.lane } ?: -1
            val canShrink = bgmLaneCount > maxOf(1, maxOccupiedLane + 1)
            val currentLaneCount by rememberUpdatedState(bgmLaneCount)
            var dragAccumPy by remember { mutableStateOf(0f) }
            var laneCountBase by remember { mutableStateOf(bgmLaneCount) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = playheadVisualBottom)
                    .fillMaxWidth()
                    .height(laneHandleHitHeight)
                    .pointerInput(bgmRowStridePx) {
                        detectDragGestures(
                            onDragStart = {
                                dragAccumPy = 0f
                                laneCountBase = currentLaneCount
                            },
                            onDrag = { change: PointerInputChange, drag: Offset ->
                                change.consume()
                                dragAccumPy += drag.y
                                if (bgmRowStridePx > 0f) {
                                    val laneDelta = (dragAccumPy / bgmRowStridePx).toInt()
                                    val targetCount = (laneCountBase + laneDelta)
                                        .coerceIn(1, TimelineBarSpec.MaxBgmLaneCount)
                                    if (targetCount != currentLaneCount) onBgmSetLaneCount(targetCount)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // 시각 pill — 축소 차단 시 흐리게 표시해 사용자에게 막힘 피드백.
                val pillAlpha = if (canShrink) 0.6f else 0.3f
                val pillBgAlpha = if (canShrink) 0.18f else 0.08f
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(laneHandleVisualHeight)
                        .clip(RoundedCornerShape(laneHandleVisualHeight / 2))
                        .background(accent.copy(alpha = pillBgAlpha))
                        .border(
                            width = 1.dp,
                            color = accent.copy(alpha = pillAlpha),
                            shape = RoundedCornerShape(laneHandleVisualHeight / 2),
                        ),
                )
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
    val directiveOverlays = remember(directives, stemPeaksByUrl) {
        directives.map { d ->
            val originalContrib = if (d.muteOriginalSegmentAudio) 0f else 1f
            val selectedStems = d.selections.filter { it.selected }
            val stemContrib = selectedStems.maxOfOrNull { it.volume } ?: 0f
            val contributions = selectedStems.mapNotNull { sel ->
                val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val peaks = stemPeaksByUrl[url] ?: return@mapNotNull null
                if (peaks.isEmpty()) null else StemPeakContribution(peaks, sel.volume)
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
            .clip(RoundedCornerShape(TimelineBarSpec.ContentCornerRadius))
            .background(trackBg),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = sourcePeaks.size
            if (n == 0 || totalMs <= 0L) return@Canvas
            val slot = size.width / n
            val barWidth = (slot * 0.6f).coerceAtLeast(1.5f)
            val cy = size.height / 2f
            val maxHalfHeight = size.height / 2f - 3f
            for (i in 0 until n) {
                val timelineMs = ((i + 0.5f) / n * totalMs).toLong()

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
                            val peakIdx = ((sourceMs.toDouble() / sourceDurationMs) * n).toInt()
                                .coerceIn(0, n - 1)
                            sourcePeak = sourcePeaks[peakIdx]
                        }
                        break
                    }
                }

                // directive overlay scale + 시각 컬러 분기 결정. directive 안일 때 선택된 모든 stem 의 peak 를
                // volume 가중 후 에너지 합산 (sqrt(Σ(p_i·v_i)²)) → 화자+배경 둘 다 켜면 합쳐진 mix 가 보임.
                // mute 안 한 경우 source peak 도 추가 항으로 더함. stem peaks 없으면 기존 source × scale fallback.
                var directiveScale = 1f
                var inDirective = false
                var directivePeak: Float? = null
                for (ov in directiveOverlays) {
                    if (timelineMs in ov.startMs..ov.endMs) {
                        directiveScale = ov.scale
                        inDirective = true
                        if (ov.stemContributions.isNotEmpty()) {
                            val durMs = (ov.endMs - ov.startMs).coerceAtLeast(1L)
                            val rel = (timelineMs - ov.startMs).coerceAtLeast(0L) + ov.sourceOffsetMs
                            var sumSq = 0.0
                            for (c in ov.stemContributions) {
                                val idx = ((rel.toDouble() / durMs) * c.peaks.size).toInt()
                                    .coerceIn(0, c.peaks.size - 1)
                                val v = c.peaks[idx] * c.volume
                                sumSq += v * v
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
                val h = maxOf(1.5f, kotlin.math.sqrt(effectivePeak) * maxHalfHeight)
                val x = slot * i + (slot - barWidth) / 2f
                drawRect(
                    color = if (inDirective) highlightBarColor else defaultBarColor,
                    topLeft = Offset(x, cy - h),
                    size = Size(barWidth, h * 2f),
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
private data class StemPeakContribution(val peaks: List<Float>, val volume: Float)
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
                    Text("저장", style = typo.bodySm)
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
                    Text("공유", style = typo.bodySm)
                }
            }
            Spacer(Modifier.height(VibiSpacing.sm))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BgmActionSheet(
    clip: com.vibi.shared.domain.model.BgmClip,
    onUpdateVolume: (Float) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onSeparate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previewer = com.vibi.cmp.platform.rememberAudioPreviewer()
    val displayName = clip.sourceUri.substringAfterLast('/').substringBeforeLast('.').take(28)
    var isPreviewing by remember(clip.id) { mutableStateOf(false) }
    // 파형 peaks — 모듈 레벨 캐시(bgmPeaksCache) 에서 즉시 초기값. 캐시 미스 시 LaunchedEffect 가 비동기
    // 추출 후 캐시 + state 갱신. sheet 닫고 다시 열어도 동일 sourceUri 면 즉시 표시 (composable scope 의
    // remember 만 쓰면 매 reopen 시 빈 list → 깜박임 / 추출 실패 노출).
    var peaks by remember(clip.id) { mutableStateOf(bgmPeaksCache[clip.sourceUri] ?: emptyList()) }
    LaunchedEffect(clip.sourceUri) {
        if (peaks.isEmpty()) {
            val extracted = com.vibi.cmp.platform.extractAudioPeaks(clip.sourceUri, samples = 120)
            if (extracted.isNotEmpty()) {
                bgmPeaksCache[clip.sourceUri] = extracted
                peaks = extracted
            }
        }
    }
    val dismissAndStop: () -> Unit = {
        if (isPreviewing) {
            previewer.stop()
            isPreviewing = false
        }
        onDismiss()
    }
    // 볼륨/속도 슬라이더는 기본 숨김 — 액션 버튼 클릭 시 해당 bar 만 펼침.
    var expanded by remember(clip.id) { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = dismissAndStop,
        sheetState = sheetState,
        containerColor = tokens.panelBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VibiSpacing.base, vertical = VibiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
        ) {
            Text(
                "🎵 $displayName",
                color = tokens.onBackgroundPrimary,
                style = typo.titleSm,
            )
            // 재생 버튼 + 파형. play 가 파형 앞(좌측) 에 위치 — 사용자 1차 액션 (재생/정지) 에 시선이 먼저 닿게.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xxs),
            ) {
                RoundIconButton(
                    icon = if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPreviewing) "정지" else "미리듣기",
                    selected = isPreviewing,
                    onClick = {
                        if (isPreviewing) {
                            previewer.stop()
                            isPreviewing = false
                        } else {
                            previewer.play(
                                url = clip.sourceUri,
                                volume = clip.volumeScale,
                                rate = clip.speedScale,
                                onComplete = { isPreviewing = false },
                            )
                            isPreviewing = true
                        }
                    },
                )
                WaveformPlayBar(
                    peaks = peaks,
                    progressMs = previewer.progressMs.value,
                    durationMs = previewer.durationMs.value,
                    isPlaying = isPreviewing,
                    modifier = Modifier.weight(1f),
                    onSeek = if (isPreviewing) { ms -> previewer.seekTo(ms) } else null,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
            ) {
                // 네 액션 모두 동일 라벨 버튼 패턴 — 볼륨/속도는 expand 토글 (selected 표시),
                // 배경음분리는 강조(accent), 삭제는 destructive. 이전 round-icon + text 혼용은
                // 사용자에게 시각 위계가 비일관적이라 모두 outline 라벨 버튼으로 통일.
                LabeledActionButton(
                    label = "볼륨",
                    selected = expanded == "volume",
                    onClick = { expanded = if (expanded == "volume") null else "volume" },
                )
                LabeledActionButton(
                    label = "속도",
                    selected = expanded == "speed",
                    onClick = { expanded = if (expanded == "speed") null else "speed" },
                )
                LabeledActionButton(
                    label = "배경음분리",
                    accent = true,
                    onClick = onSeparate,
                )
                LabeledActionButton(
                    label = "삭제",
                    accent = true,
                    onClick = {
                        if (isPreviewing) {
                            previewer.stop()
                            isPreviewing = false
                        }
                        onDelete()
                        onDismiss()
                    },
                )
            }
            if (expanded == "volume") {
                InlineSliderRow(
                    label = "볼륨",
                    value = clip.volumeScale,
                    range = com.vibi.shared.domain.model.BgmClip.MIN_VOLUME..com.vibi.shared.domain.model.BgmClip.MAX_VOLUME,
                    onChange = onUpdateVolume,
                )
            }
            if (expanded == "speed") {
                InlineSliderRow(
                    label = "속도",
                    value = clip.speedScale,
                    range = com.vibi.shared.domain.model.BgmClip.MIN_SPEED..com.vibi.shared.domain.model.BgmClip.MAX_SPEED,
                    onChange = onUpdateSpeed,
                )
            }
        }
    }
}

/**
 * BgmActionSheet 의 파형 peaks 모듈 레벨 캐시. composable scope (`remember`) 만 쓰면 sheet 닫고
 * 다시 열 때마다 빈 list 로 리셋되어 추출이 다시 시작 → 깜박임/실패 시 영영 안 보임. sourceUri 키로
 * 1회 추출 후 영속(프로세스 lifetime) — 같은 클립 재진입 시 즉시 표시.
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
 * BgmActionSheet 의 볼륨/속도 인라인 슬라이더 — 회전된 popup 대신 row 가 펼쳐지는 패턴.
 */
@Composable
private fun InlineSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val tokens = LocalVibiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.mutedText,
            modifier = Modifier.width(40.dp),
        )
        Slider(
            value = value,
            valueRange = range,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.onBackgroundPrimary,
            modifier = Modifier.width(44.dp),
        )
    }
}

/**
 * BgmActionSheet 액션 row 의 라벨 버튼. RoundIconButton 의 outline + selected 패턴을
 * 그대로 가져오되 아이콘 대신 텍스트 라벨로 노출 — 네 액션(볼륨/속도/배경음분리/삭제)을
 * 일관된 outline pill 로 통일.
 *
 * - selected=true: accent 테두리 + 살짝 채움 (볼륨/속도 expand 상태)
 * - accent=true:  accent 색 텍스트/테두리 (배경음분리·삭제 같은 강조 액션)
 */
@Composable
private fun LabeledActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Boolean = false,
) {
    val tokens = LocalVibiColors.current
    val borderColor = when {
        selected -> tokens.accent
        accent -> tokens.accent.copy(alpha = 0.6f)
        else -> tokens.onBackgroundPrimary.copy(alpha = 0.25f)
    }
    val bgColor = if (selected) tokens.accent.copy(alpha = 0.12f) else Color.Transparent
    val textColor = when {
        selected || accent -> tokens.accent
        else -> tokens.onBackgroundPrimary
    }
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = textColor,
        )
    }
}

/**
 * BgmActionSheet 액션 row 의 동그란 라운드 아이콘 버튼. selected=true 면 accent 테두리 + 채움.
 */
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val tokens = LocalVibiColors.current
    val borderColor = if (selected) tokens.accent else tokens.onBackgroundPrimary.copy(alpha = 0.25f)
    val bgColor = if (selected) tokens.accent.copy(alpha = 0.12f) else Color.Transparent
    val iconTint = if (selected) tokens.accent else tokens.onBackgroundPrimary
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

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
) {
    // hitHeight 는 명시 파라미터 — UnifiedTimelineBar 가 BGM region 까지 컨테이너가 늘어난 뒤에도
    // range 핸들 hit zone 이 BGM lane drag 와 충돌하지 않게 top playback region 만큼만 잡도록 한다.
    var baseMs by remember { mutableStateOf(0L) }
    var accumPx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .width(hitWidth)
            .height(hitHeight)
            .pointerInput(totalWidthPx, totalMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        baseMs = baseMsProvider()
                        accumPx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        accumPx += dragAmount
                        if (totalWidthPx > 0f && totalMs > 0L) {
                            val deltaMs = (accumPx / totalWidthPx) * totalMs
                            onChange(clamp((baseMs + deltaMs).toLong()))
                        }
                    }
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
        contentAlignment = Alignment.Center
    ) {
        // 시각 막대 두 개 모두 gripHeight (= 회색 strip 두께) 로 통일.
        // hit zone 자체는 fillMaxHeight 유지 — 드래그 잡기 영역은 풀 바 높이.
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

/**
 * stepper 단계 전환 경고 다이얼로그. 음원→자막/더빙 (LocalizationLock) 또는 →영상편집 (EditReset)
 * 진입 시 한 번 확인. "다시 묻지 않기" 체크박스 → UserPreferencesStore 영속화.
 */
@Composable
private fun StepTransitionWarningDialog(
    warning: StepTransitionWarning,
    onConfirm: (dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val typo = LocalVibiTypography.current
    var dontAskAgain by remember { mutableStateOf(false) }
    val (title, body) = when (warning) {
        is StepTransitionWarning.LocalizationLock ->
            "자막/더빙 단계로 이동" to
                "자막 또는 더빙을 생성하면 이전 단계의 음원 분리(분리 결과·stem 선택) 를 더 이상 수정할 수 없습니다. 음원 작업을 마무리했는지 확인해 주세요."
        is StepTransitionWarning.EditReset ->
            "영상 편집 단계로 이동" to
                "영상 편집(구간 삭제/복제/속도 변경 등)을 적용하면 기존에 생성한 음원 분리·자막·더빙·BGM 배치가 모두 초기화됩니다. 그래도 진행하시겠어요?"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm)) {
                Text(body, style = typo.bodyMd)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontAskAgain = !dontAskAgain },
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = { dontAskAgain = it },
                    )
                    Text("다시 묻지 않기", style = typo.bodySm)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(dontAskAgain) }) { Text("계속") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

/** ARGB hex (#AARRGGBB or #RRGGBB) → Compose Color. 잘못된 입력은 white fallback. */
private fun parseArgbHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val v = runCatching { cleaned.toLong(16) }.getOrNull() ?: return Color.White
    return when (cleaned.length) {
        8 -> Color(
            alpha = ((v shr 24) and 0xFF) / 255f,
            red = ((v shr 16) and 0xFF) / 255f,
            green = ((v shr 8) and 0xFF) / 255f,
            blue = (v and 0xFF) / 255f,
        )
        6 -> Color(
            alpha = 1f,
            red = ((v shr 16) and 0xFF) / 255f,
            green = ((v shr 8) and 0xFF) / 255f,
            blue = (v and 0xFF) / 255f,
        )
        else -> Color.White
    }
}
