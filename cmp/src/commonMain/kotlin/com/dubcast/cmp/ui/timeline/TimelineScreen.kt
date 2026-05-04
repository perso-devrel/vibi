package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.DropdownMenu
import com.dubcast.cmp.theme.LocalDubCastColors
import com.dubcast.cmp.platform.StemMixerSource
import com.dubcast.cmp.platform.rememberAudioPicker
import com.dubcast.cmp.platform.rememberAudioRecorder
import com.dubcast.cmp.platform.rememberStemMixer
import com.dubcast.cmp.ui.chat.ChatPanel
import com.dubcast.shared.domain.model.AutoJobStatus
import com.dubcast.shared.ui.timeline.AudioSeparationStep
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.dubcast.cmp.platform.VideoPlayer
import com.dubcast.cmp.ui.cupertino.StepHero
import com.dubcast.shared.ui.timeline.SaveStatus
import com.dubcast.shared.ui.timeline.TimelineViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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
    val activeDirective = state.separationDirectives.firstOrNull { d ->
        d.selections.any { !it.audioUrl.isNullOrBlank() }
    }
    LaunchedEffect(activeDirective?.id) {
        val dir = activeDirective
        if (dir == null) {
            stemMixer.load(emptyList())
            return@LaunchedEffect
        }
        // load 는 audioUrl 있는 모든 stem (selected 무관) — 사용자가 토글 키면 즉시 반영되도록
        // 모두 prepare. 선택 안 된 stem 은 아래 volume effect 에서 0 으로 mute.
        val sources = dir.selections.mapNotNull { sel ->
            val url = sel.audioUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StemMixerSource(stemId = sel.stemId, audioUrl = url)
        }
        stemMixer.load(sources)
        dir.selections.forEach { sel ->
            stemMixer.setVolume(sel.stemId, if (sel.selected) sel.volume else 0f)
        }
    }
    // 볼륨/선택 변화 실시간 전파 — slider 드래그 또는 체크 토글 → onUpdateStemVolume/onToggleStemSelection
    // 이 directive 를 upsert → observe Flow 가 새 selections 흘려줌 → 본 effect 가
    // stemMixer.setVolume 호출. selected=false 면 0f mute, true 면 볼륨 그대로.
    // load 와 분리한 이유: load(...) 는 player 인스턴스 재생성이라 매 변화마다 호출하면 재생이 끊김.
    LaunchedEffect(activeDirective?.id, activeDirective?.selections) {
        val dir = activeDirective ?: return@LaunchedEffect
        dir.selections.forEach { sel ->
            stemMixer.setVolume(sel.stemId, if (sel.selected) sel.volume else 0f)
        }
    }
    // 재생 토글 / 사용자 seek 시 mixer 도 같이 정렬. video 재생 자유진행 시 sample-accurate 동기화는
    // 못 하지만 사용자 체감 합리적 — 차후 더 정밀하면 정기 drift 보정 추가.
    LaunchedEffect(state.isPlaying, activeDirective?.id) {
        val dir = activeDirective ?: return@LaunchedEffect
        if (state.isPlaying) {
            val offset = (state.playbackPositionMs - dir.rangeStartMs).coerceAtLeast(0L)
            stemMixer.seekTo(offset)
            stemMixer.play()
        } else {
            stemMixer.pause()
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

    val tokens = LocalDubCastColors.current
    Box(modifier = Modifier.fillMaxSize().background(tokens.backgroundPrimary)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 헤더: 뒤로 + StepHero + 저장 버튼. 백그라운드 잡 진행 중이면 저장 disabled.
        // 저장 버튼이 자체적으로 모든 variant 렌더 → 갤러리 저장 → EditProject 삭제 → InputScreen 복귀를
        // 호출하므로 별도 ExportScreen 으로 이동하는 흐름은 폐기됐다.
        val saveAnyJobRunning = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
            state.autoDubStatus == AutoJobStatus.RUNNING ||
            state.audioSeparation?.step == AudioSeparationStep.PROCESSING ||
            state.separationStatus == AutoJobStatus.RUNNING ||
            state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
            state.sttPreflightStatus == AutoJobStatus.RUNNING
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tokens.chipBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = tokens.onBackgroundPrimary, style = MaterialTheme.typography.titleLarge)
            }
            val headerTitle = if (state.isSegmentEditMode) "타임라인: 영상편집" else "타임라인"
            StepHero(step = 2, title = headerTitle, modifier = Modifier.weight(1f), compact = true)
            val saving = state.saveStatus is SaveStatus.RUNNING
            val savingPercent = (state.saveStatus as? SaveStatus.RUNNING)?.progress ?: 0
            if (state.isSegmentEditMode) {
                // 영상편집 모드 — 취소(편집 무효) + 체크(편집 확정 → 타임라인 결과 초기화).
                IconButton(
                    onClick = { viewModel.onCancelSegmentEditChanges() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = "취소",
                        tint = tokens.onBackgroundPrimary,
                    )
                }
                IconButton(
                    onClick = { viewModel.onCommitSegmentEdit() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                        contentDescription = "적용",
                        tint = tokens.onBackgroundPrimary,
                    )
                }
            } else {
                // 공유 아이콘 — 추후 기능. 지금은 disabled icon button.
                // (분기 진입 시 isSegmentEditMode == false)
                IconButton(
                    onClick = { /* TODO: 공유 기능 */ },
                    enabled = false,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Outlined.Share,
                        contentDescription = "공유 (준비중)",
                        tint = tokens.onBackgroundPrimary.copy(alpha = 0.4f),
                    )
                }
                // 저장 아이콘 — 진행 중이면 percent 텍스트로 토글.
                // segments 비어있으면 ExportWithDubbingUseCase 가 require(isNotEmpty) 에서 throw →
                // 사용자가 silent crash 보기 전에 버튼 단계에서 차단.
                IconButton(
                    enabled = !saving && !saveAnyJobRunning && state.segments.isNotEmpty(),
                    onClick = { viewModel.onSaveAllVariants() },
                    modifier = Modifier.size(40.dp),
                ) {
                    if (saving) {
                        Text(
                            "${savingPercent}%",
                            fontSize = 11.sp,
                            color = tokens.onBackgroundPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Save,
                            contentDescription = "저장",
                            tint = tokens.onBackgroundPrimary,
                        )
                    }
                }
            }
        }

        // 영상편집 모드 안내 — 적용 시 결과(음원분리/자막/더빙) 가 모두 초기화됨을 사용자에게 명시.
        if (state.isSegmentEditMode) {
            Text(
                "영상 편집을 적용(✓)하면 음원 분리·자막·더빙 결과와 변경사항 되돌리기 스택이 모두 초기화됩니다. 다시 생성하려면 적용 후 각 단계를 처음부터 진행해야 합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.mutedText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // 버전 선택 — DropdownMenu 대신 inline pill row. 가로 스크롤 가능.
        val versions = buildList<Pair<String?, String>> {
            add(null to "기본")
            state.targetLanguageCodes.forEach { add(it to it.uppercase()) }
        }
        val isJobRunning = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
            state.autoDubStatus == AutoJobStatus.RUNNING ||
            state.audioSeparation?.step == AudioSeparationStep.PROCESSING ||
            state.regenerateSubtitleStatus == AutoJobStatus.RUNNING
        // 영상 편집 모드에선 변종 선택 숨김 — 원본만 표시.
        if (!state.isSegmentEditMode) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            val currentLabel = versions.firstOrNull { it.first == state.previewLangCode }?.second ?: "기본"
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(tokens.chipBg)
                        .clickable(enabled = !isJobRunning) { menuOpen = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(currentLabel, color = tokens.onBackgroundPrimary, fontSize = 14.sp)
                    if (isJobRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = tokens.onBackgroundPrimary,
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text("▾", color = tokens.onBackgroundPrimary, fontSize = 14.sp)
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
                .clip(RoundedCornerShape(12.dp))
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
                    it.type == com.dubcast.shared.domain.model.SegmentType.VIDEO
                }
                val playerItems = if (previewMuxUri != null) {
                    listOf(
                        com.dubcast.cmp.platform.VideoPlayerItem(
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
                        com.dubcast.cmp.platform.VideoPlayerItem(
                            sourceUri = seg.sourceUri,
                            trimStartMs = seg.trimStartMs,
                            trimEndMs = if (seg.trimEndMs > 0L) seg.trimEndMs else 0L,
                            speedScale = seg.speedScale,
                            volumeScale = seg.volumeScale,
                        )
                    }
                }
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
            val activeSubtitleClip = run {
                // 원본 미리보기(previewLangCode == null) 에는 자막 표시 안 함. lang="" SubtitleClip
                // 들은 STT 검토 용도라 timeline preview 에서 보이면 안 됨.
                val lang = state.previewLangCode
                if (lang.isNullOrBlank()) null
                else state.subtitleClips
                    .filter { it.languageCode == lang }
                    .firstOrNull { clip -> state.playbackPositionMs in clip.startMs..clip.endMs }
            }
            if (activeSubtitleClip != null) {
                val anchor = activeSubtitleClip.position.anchor
                val align = when (anchor) {
                    com.dubcast.shared.domain.model.Anchor.TOP -> Alignment.TopCenter
                    com.dubcast.shared.domain.model.Anchor.MIDDLE -> Alignment.Center
                    com.dubcast.shared.domain.model.Anchor.BOTTOM -> Alignment.BottomCenter
                }
                Box(
                    modifier = Modifier
                        .align(align)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(parseArgbHexColor(activeSubtitleClip.backgroundColorHex), RoundedCornerShape(8.dp))
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

            // 우상단 연필 버튼 — segment 편집 (복제/삭제/볼륨/속도) 진입. 첫 video segment 의 id 로
            // ViewModel.onEnterSegmentEditMode 호출. 음성분리(onEnterRangeMode) 와 명시적 분리.
            // (iOS UIKitView z-order 한계로 비디오 위 overlay 가 안 그려질 수 있음 — Android 우선 동작.)
            val firstVideoSegId = state.segments.firstOrNull { it.type == com.dubcast.shared.domain.model.SegmentType.VIDEO }?.id
            if (firstVideoSegId != null && !state.isRangeSelecting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { viewModel.onEnterSegmentEditMode(firstVideoSegId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "구간 편집",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Transport row: play/pause + 시간 표시
        if (state.videoUri.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // Undo / Redo — 작은 아이콘만, transport row 우측에 inline
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (state.canUndo) tokens.chipBg else tokens.chipBgDisabled)
                        .clickable(enabled = state.canUndo) { viewModel.onUndo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "실행 취소",
                        tint = if (state.canUndo) tokens.onBackgroundPrimary else tokens.chipContentDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (state.canRedo) tokens.chipBg else tokens.chipBgDisabled)
                        .clickable(enabled = state.canRedo) { viewModel.onRedo() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "다시 실행",
                        tint = if (state.canRedo) tokens.onBackgroundPrimary else tokens.chipContentDisabled,
                        modifier = Modifier.size(20.dp)
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
            // 단일 통합 타임라인 바 — 재생/구간선택/segment·directive 시각 모두 한 위치에.
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
                onSegmentTap = { viewModel.onSelectSegmentInEdit(it) },
                onDirectiveTap = { viewModel.onEditExistingSeparation(it) },
                onScrub = { viewModel.onUpdatePlaybackPosition(it) },
                onRangeStartChange = { viewModel.onSetPendingRangeStart(it) },
                onRangeEndChange = { viewModel.onSetPendingRangeEnd(it) },
                onTranslateRange = { viewModel.onTranslateRange(it) },
                onFreeIntervalTap = { s, e -> viewModel.onSelectFreeRange(s, e) },
                onRangeTapToggle = { viewModel.onClearRangeSelection() },
            )
            if (state.isRangeSelecting) {
                Text(
                    "구간 ${state.pendingRangeStartMs / 1000}s ~ ${state.pendingRangeEndMs / 1000}s · 재생 ${state.playbackPositionMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.accent
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!state.isSegmentEditMode) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val segId = state.segments.firstOrNull()?.id ?: return@Button
                                viewModel.onCancelRangeMode()
                                viewModel.onShowAudioSeparationSheet(segId)
                            }
                        ) { Text("이 구간 음원분리") }
                        OutlinedButton(onClick = { viewModel.onCancelRangeMode() }) { Text("취소") }
                    }
                }
                if (state.isSegmentEditMode) {
                    SegmentEditActionPanel(
                        volume = state.pendingRangeVolume,
                        speed = state.pendingRangeSpeed,
                        onVolumeChange = { viewModel.onUpdatePendingRangeVolume(it) },
                        onSpeedChange = { viewModel.onUpdatePendingRangeSpeed(it) },
                        onApplyVolume = { viewModel.onApplyRangeVolume(it) },
                        onApplySpeed = { viewModel.onApplyRangeSpeed(it) },
                        onDuplicate = { viewModel.onDuplicateRange() },
                        onDelete = { viewModel.onDeleteRange() },
                        onCancel = { viewModel.onFinishSegmentEdit() },
                    )
                }
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
            state.separationStatus == AutoJobStatus.RUNNING ||
            state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
            state.sttPreflightStatus == AutoJobStatus.RUNNING
        if (!state.isRangeSelecting && !state.localizationOpen) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 라벨 — RUNNING 만 진행 표시. READY/IDLE/FAILED 모두 "음성 분리" 로
                // (이미 분리 결과 있어도 새 분리 시작). 기존 결과 편집은 directive 막대 클릭으로.
                val sepLabel = when (state.separationStatus) {
                    AutoJobStatus.RUNNING -> "⏳ 분리 진행 중"
                    AutoJobStatus.FAILED -> "❌ 다시 시도"
                    else -> "음원 분리"
                }
                // 음성분리 버튼은 자기 자신 status 만 봄 — 자막/더빙 진행 중이라도 음성분리는 독립 실행 가능.
                // RUNNING 중엔 disabled (백그라운드 폴링은 계속 — sheet 자동 재오픈 X).
                // 진행 결과 확인은 timeline 의 directive 막대 클릭으로.
                OutlinedButton(
                    enabled = firstSegId != null && state.separationStatus != AutoJobStatus.RUNNING,
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
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
                ) { Text(sepLabel, fontSize = 14.sp) }
                // 자막/더빙 생성: 자막/더빙 잡만 진행 중이면 비활성 + 진행 라벨로 변경.
                // 음성분리는 독립이라 영향 X.
                val localizationBusy = state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
                    state.autoDubStatus == AutoJobStatus.RUNNING ||
                    state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
                    state.sttPreflightStatus == AutoJobStatus.RUNNING
                val localizationLabel = when {
                    state.autoSubtitleStatus == AutoJobStatus.RUNNING &&
                        state.autoDubStatus == AutoJobStatus.RUNNING -> "⏳ 자막·더빙 생성 중"
                    state.autoSubtitleStatus == AutoJobStatus.RUNNING ||
                        state.regenerateSubtitleStatus == AutoJobStatus.RUNNING ||
                        state.sttPreflightStatus == AutoJobStatus.RUNNING -> "⏳ 자막 생성 중"
                    state.autoDubStatus == AutoJobStatus.RUNNING -> "⏳ 더빙 생성 중"
                    else -> "자막/더빙 생성"
                }
                OutlinedButton(
                    enabled = !localizationBusy,
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    onClick = { viewModel.onShowLocalization() }
                ) { Text(localizationLabel, fontSize = 14.sp) }
                // 상세 편집 — 자막 cue 별 텍스트/스타일 inline 조정. 자막 1개 이상 + idle 시.
                val anySubtitle = remember(state.subtitleClips) {
                    state.subtitleClips.any { it.languageCode.isNotBlank() }
                }
                OutlinedButton(
                    enabled = anySubtitle && !anyJobRunning,
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    onClick = { viewModel.onToggleDetailEdit() }
                ) { Text(if (state.showDetailEdit) "자막 편집 닫기" else "자막 편집", fontSize = 14.sp) }
                // 임시 — 음성분리 mock 데이터 주입 (영상 전체 분리 가정). release 전 제거.
                OutlinedButton(
                    enabled = firstSegId != null,
                    modifier = Modifier.height(42.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    onClick = { viewModel.onMockSeparationReady() }
                ) { Text("분리 mock", fontSize = 14.sp) }

                // 음원 삽입 — 단일 진입점. 클릭 시 [업로드 / 즉시 녹음] DropdownMenu.
                // 녹음 진행 중에는 자체적으로 ⏹ 종료 버튼 라벨로 토글.
                val audioPicker = rememberAudioPicker { uri -> viewModel.onPickBgmAudio(uri) }
                val recorder = rememberAudioRecorder(
                    onRecorded = { uri, _ -> viewModel.onPickBgmAudio(uri) },
                    onError = { /* TODO: bgmError 상태로 토스트 */ },
                )
                val recording = recorder.isRecording
                var audioMenuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        enabled = !state.isAddingBgm,
                        modifier = Modifier.height(42.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        onClick = {
                            if (recording) recorder.stop()
                            else audioMenuOpen = true
                        },
                    ) {
                        Text(
                            when {
                                state.isAddingBgm -> "⏳ 추가 중"
                                recording -> "⏹ 녹음 종료"
                                else -> "음원 삽입"
                            },
                            fontSize = 14.sp,
                        )
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
                                recorder.start()
                            },
                        )
                    }
                }
            }
        }

        // 추가된 음원(BGM) 클립 목록 — 영상편집/range 모드/자막더빙 패널 활성 시는 숨김.
        if (state.bgmClips.isNotEmpty() &&
            !state.isRangeSelecting && !state.localizationOpen && !state.showDetailEdit
        ) {
            BgmClipsPanel(
                clips = state.bgmClips,
                onUpdateStart = { id, ms -> viewModel.onUpdateBgmStartMs(id, ms) },
                onUpdateVolume = { id, v -> viewModel.onUpdateBgmVolume(id, v) },
                onUpdateSpeed = { id, s -> viewModel.onUpdateBgmSpeed(id, s) },
                onSeparate = { id -> viewModel.onStartBgmSeparation(id) },
                onDelete = { id -> viewModel.onDeleteBgmClip(id) },
            )
        }

        // 상세 편집 패널 — lang chip 으로 lang 필터 + cue list + 선택 cue 의 스타일/텍스트 조정.
        // 영상 미리보기 위에 자막 overlay 가 즉시 반영되어 사용자가 보면서 조정 가능.
        if (state.showDetailEdit && !state.isRangeSelecting && !state.localizationOpen) {
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

        // 자막/더빙 생성 인라인 패널 — 영상편집 모드에선 노출 금지.
        if (state.localizationOpen && !state.isSegmentEditMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.panelBg, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("자막/더빙 생성", style = MaterialTheme.typography.titleSmall, color = tokens.onBackgroundPrimary)
                // 모드 선택 (자막 / 더빙 — 둘 중 하나)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Text("번역 대상 언어 (다중)", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "en" to "English",
                        "ko" to "한국어",
                        "ja" to "日本語",
                        "zh" to "中文",
                        "es" to "Español",
                        "fr" to "Français",
                        "de" to "Deutsch"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = code in state.localizationLangs,
                            onClick = { viewModel.onToggleLocalizationLang(code) },
                            label = { Text(label) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = state.localizationLangs.isNotEmpty(),
                        onClick = { viewModel.onStartLocalization() }
                    ) { Text("생성 시작") }
                    OutlinedButton(onClick = { viewModel.onDismissLocalization() }) {
                        Text("취소")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 저장 상태 메시지 (실패 시) — 헤더 저장 버튼이 진행률을 자체 표시하므로
        // running/done 은 별도 표시 안 함. 실패 메시지만 사용자에게 알림.
        when (val s = state.saveStatus) {
            is SaveStatus.FAILED -> Text(
                "저장 실패: ${s.message}",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Unit
        }
    }

    // 채팅 어시스턴트 FAB — 메인 타임라인 뷰 전용. range/segment edit/패널/시트 활성 시 숨김.
    val chatFabVisible = !state.isSegmentEditMode &&
        !state.isRangeSelecting &&
        !state.localizationOpen &&
        !state.showDetailEdit &&
        !state.showAudioSeparationSheet &&
        !state.showDubbingSheet &&
        !state.showSubtitleSheet &&
        !state.showSubtitleEditSheet &&
        !state.showRegenerateSubtitleSheet &&
        !state.showScriptReviewSheet &&
        !state.showAppendSheet &&
        !state.showFrameSheet &&
        !state.showTextOverlaySheet &&
        !chatSheetVisible
    if (chatFabVisible) {
        FloatingActionButton(
            onClick = { chatSheetVisible = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
            containerColor = tokens.accent,
            contentColor = tokens.onBackgroundPrimary,
        ) {
            Text("💬", fontSize = 22.sp)
        }
    }
    } // close Box wrapper

    // 더빙 sheet — 영상편집 모드에선 노출 금지.
    if (state.showDubbingSheet && !state.isSegmentEditMode) {
        InsertDubbingSheet(
            voices = state.voices,
            isVoicesLoading = state.isVoicesLoading,
            isSynthesizing = state.isSynthesizing,
            previewAvailable = state.previewClip != null,
            synthError = state.synthError,
            onSynthesize = { text, voiceId, voiceName ->
                viewModel.onSynthesize(text, voiceId, voiceName)
            },
            onInsert = { viewModel.onInsertPreviewClip() },
            onDismiss = { viewModel.onDismissDubbingSheet() }
        )
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "STT 결과를 확인하고 잘못 인식된 부분을 수정하세요. 확인 후 ${state.pendingReviewTargetLangs.joinToString(", ") { it.uppercase() }} 자막이 생성됩니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = sourceClips, key = { it.id }) { clip ->
                            val draft = scriptReviewEdits[clip.id] ?: clip.text
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${clip.startMs / 1000}s\n${clip.endMs / 1000}s",
                                    style = MaterialTheme.typography.labelSmall,
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


    // 자막 sheet — 영상편집 모드에선 노출 금지.
    if (state.showSubtitleSheet && !state.isSegmentEditMode) {
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

    // 음성분리 sheet — 영상편집 모드에선 노출 금지.
    state.audioSeparation
        ?.takeIf { state.showAudioSeparationSheet && !state.isSegmentEditMode }
        ?.let { sepState ->
        AudioSeparationSheet(
            state = sepState,
            onUpdateSpeakers = { viewModel.onUpdateSeparationSpeakers(it) },
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

    // 채팅 어시스턴트 sheet — 영상편집 모드와 무관하게 띄울 수 있지만 위 FAB 가 모드 중엔 숨김.
    if (chatSheetVisible) {
        ChatPanel(
            timelineVm = viewModel,
            timelineState = state,
            onDismiss = { chatSheetVisible = false },
        )
    }
}

/**
 * 한 클립을 트랙 위에 그릴 데이터.
 */
data class ClipBar(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val selected: Boolean
)

/**
 * 라벨 + 가로 트랙 + 클립 막대들 — 시간축에 비례해서 배치.
 *
 * [durationMs] 가 0 이면 클립을 그릴 수 없으므로 라벨만 노출.
 * 막대 탭 → [onClipClick] 호출 (id 또는 null=선택해제).
 */
@Composable
private fun ClipTrack(
    label: String,
    color: Color,
    durationMs: Long,
    clips: List<ClipBar>,
    onClipClick: (String?) -> Unit
) {
    val tokens = LocalDubCastColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        }
        // 트랙 바 (탭 시 선택 해제)
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.10f))
                .clickable { onClipClick(null) }
        ) {
            val totalWidth = maxWidth
            if (durationMs > 0L) {
                clips.forEach { clip ->
                    val startFrac = (clip.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val endFrac = (clip.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val widthFrac = (endFrac - startFrac).coerceAtLeast(0.01f)
                    Box(
                        modifier = Modifier
                            .padding(start = totalWidth * startFrac)
                            .width(totalWidth * widthFrac)
                            .height(28.dp)
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (clip.selected) color else color.copy(alpha = 0.6f))
                            .border(
                                width = if (clip.selected) 2.dp else 0.dp,
                                color = tokens.onBackgroundPrimary,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable {
                                // 토글: 같은 막대 다시 누르면 선택 해제
                                onClipClick(if (clip.selected) null else clip.id)
                            }
                    )
                }
            }
        }
    }
}

/**
 * Segment 편집(복제/삭제/볼륨/속도) inline 패널 — 영상 우상단 연필 버튼으로 진입했을 때
 * range slider 확정 후 노출. 적용 시 [TimelineViewModel.onDuplicateRange] / [onDeleteRange] /
 * [onApplyRangeVolume] / [onApplyRangeSpeed] 가 내부적으로 resetRangeMode() 를 호출해 자동 닫힘.
 */
@Composable
private fun SegmentEditActionPanel(
    volume: Float,
    speed: Float,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onApplyVolume: (Float) -> Unit,
    onApplySpeed: (Float) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = LocalDubCastColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panelBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // "구간 편집" 헤더 옆 복제/삭제 작은 버튼.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "구간 편집",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.onBackgroundPrimary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onDuplicate,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
            ) { Text("복제", fontSize = 12.sp) }
            OutlinedButton(
                onClick = onDelete,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
            ) { Text("삭제", fontSize = 12.sp) }
        }

        // 볼륨 — 0..2 (0 = 무음, 1 = 그대로, 2 = 2배). 변경된 경우에만 "적용" 버튼이 의미 있음.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("볼륨 ${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                TextButton(onClick = { onApplyVolume(volume) }) { Text("볼륨 적용") }
            }
            Slider(
                value = volume,
                valueRange = 0f..2f,
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 속도 — 0.25..4.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val pct = (speed * 100).toInt()
                Text("속도 ${pct}%", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
                TextButton(onClick = { onApplySpeed(speed) }) { Text("속도 적용") }
            }
            Slider(
                value = speed,
                valueRange = 0.25f..4f,
                onValueChange = onSpeedChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 복제/삭제 는 헤더 row 로 이동했고, 우상단 X 가 "취소(저장)" 역할이라 하단 row 제거.
    }
}

/**
 * 통합 타임라인 바 — 28dp 전체 높이, 가운데 6dp 만 segment/directive content strip (얇은 띠).
 * 양쪽 끝 핸들은 full height 의 grippable 바 형태 (8dp wide visual + 28dp hit area).
 * 색 분리: bg = 검정 strip, segment bg/edited = 중성 회색 톤, range fill = accent (주황/파랑 등 distinct).
 *
 * 제스처:
 *  - segment 탭 (영상편집) → onSegmentTap (그 segment 전체로 range 스냅)
 *  - directive 탭 (음원분리) → onDirectiveTap (편집 sheet)
 *  - 빈 영역 탭 → onScrub
 *  - range fill 드래그 → onTranslateRange (양쪽 끝 동시 이동)
 *  - 좌/우 핸들 드래그 → onRangeStartChange/EndChange
 *  - 재생 마커 = playbackPositionMs 위치 흰선 (얇은 strip 위에 떠 있음)
 */
/**
 * 통합 타임라인 바의 시각/제스처 spec — 사이즈/간격/clamp 등 매직 넘버 한 곳에 모음.
 * 색은 [DubCastColors] 의 `timelineBar*` 토큰 사용 — light/dark 자동 분기.
 */
private object TimelineBarSpec {
    val BarHeight = 56.dp
    val ContentHeight = 12.dp
    val HandleHitWidth = 32.dp
    val HandleVisualWidth = 8.dp
    val GripWidth = 3.dp
    val GripVerticalInset = 12.dp
    val ContentCornerRadius = 4.dp
    val HandleCornerRadius = 4.dp
    val SegmentSpacing = 1.dp
    /** 재생 마커 hit area — drag 으로 scrub. 마커 visual 자체는 GripWidth, hit zone 은 더 넓게. */
    val PlaybackHitWidth = 32.dp
    /** 재생 마커 visual line 높이 — 바 높이보다 짧게 (bar 위/아래로 marker 가 튀어나오지 않도록). */
    val PlaybackMarkerVerticalInset = 16.dp
    /** 구간 선택 영역 상/하단 accent border 두께 — Android 초기 트림 핸들 스타일. */
    val RangeBorderThickness = 2.dp
    /** range 핸들 사이 최소 간격 — VM 의 MIN_RANGE_MS 와 동일 의미. */
    const val MinRangeGapMs = 100L
}

@Composable
private fun UnifiedTimelineBar(
    segments: List<com.dubcast.shared.domain.model.Segment>,
    directives: List<com.dubcast.shared.domain.model.SeparationDirective>,
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
) {
    val density = LocalDensity.current
    val currentRangeStart by rememberUpdatedState(rangeStartMs)
    val currentRangeEnd by rememberUpdatedState(rangeEndMs)

    val barHeight = TimelineBarSpec.BarHeight
    val contentHeight = TimelineBarSpec.ContentHeight
    val handleHitWidth = TimelineBarSpec.HandleHitWidth
    val handleVisualWidth = TimelineBarSpec.HandleVisualWidth

    // range 모드 (영상편집 + 음원분리) 양쪽 다 parent tap detector 단일화. segment 자체에 clickable
    // 두지 않고 ms 좌표로 segment id 를 역검색 → onSegmentTap 호출. 같은 segment 재탭 시 VM 의
    // onSelectSegmentInEdit 토글 로직이 처리.
    val currentStartForTap by rememberUpdatedState(rangeStartMs)
    val currentEndForTap by rememberUpdatedState(rangeEndMs)
    val rangeTapModifier = if (showRange && totalMs > 0L) {
        Modifier.pointerInput(showSegments, segments, directives, totalMs) {
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
                            if (seg.type == com.dubcast.shared.domain.model.SegmentType.VIDEO) {
                                onSegmentTap(seg.id)
                            }
                            return@detectTapGestures
                        }
                        acc = nextAcc
                    }
                    return@detectTapGestures
                }

                // 음원분리: directive 위 ignore, 선택 영역 재탭 → toggle, 그 외 free interval → snap.
                val sortedDir = directives.sortedBy { it.rangeStartMs }
                val onDirective = sortedDir.any { ms in it.rangeStartMs..it.rangeEndMs }
                if (onDirective) return@detectTapGestures
                val hasSelection = currentEndForTap > currentStartForTap
                if (hasSelection && ms in currentStartForTap..currentEndForTap) {
                    onRangeTapToggle()
                    return@detectTapGestures
                }
                var freeStart = 0L
                var freeEnd = totalMs
                for (d in sortedDir) {
                    if (ms < d.rangeStartMs) {
                        freeEnd = d.rangeStartMs
                        break
                    }
                    freeStart = maxOf(freeStart, d.rangeEndMs)
                }
                onFreeIntervalTap(freeStart, freeEnd)
            })
        }
    } else Modifier

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .then(rangeTapModifier)
    ) {
        val totalWidthDp = maxWidth
        val totalWidthPx = with(density) { totalWidthDp.toPx() }

        // Layer 1 — 가운데 얇은 배경 strip + segment/directive content.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(contentHeight)
                .clip(RoundedCornerShape(TimelineBarSpec.ContentCornerRadius))
                .background(trackColor)
        )
        if (showSegments) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(contentHeight),
                horizontalArrangement = Arrangement.spacedBy(TimelineBarSpec.SegmentSpacing),
            ) {
                segments.forEach { seg ->
                    // 편집됨 = 사용자가 의도적으로 변경한 속성. trim 만으로는 split 결과 (자동 발생)
                    // 와 구분 안 되므로 색 표시에서 제외 — 100% 볼륨/속도, duplicatedFromId 미사용 도
                    // 미편집 취급.
                    val edited = (seg.volumeScale != 1.0f) ||
                        (seg.speedScale != 1.0f) ||
                        (seg.duplicatedFromId != null)
                    // tap 은 parent rangeTapModifier 가 ms → segment id 역검색으로 처리.
                    Box(
                        modifier = Modifier
                            .weight(seg.effectiveDurationMs.toFloat().coerceAtLeast(1f))
                            .fillMaxHeight()
                            .background(if (edited) segmentEditedColor else segmentColor),
                    )
                }
            }
        } else if (showDirectives && directives.isNotEmpty() && totalMs > 0L) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(contentHeight),
            ) {
                var prevEnd = 0L
                directives.forEach { directive ->
                    val gap = (directive.rangeStartMs - prevEnd).coerceAtLeast(0L)
                    if (gap > 0L) Spacer(Modifier.weight(gap.toFloat()))
                    val w = (directive.rangeEndMs - directive.rangeStartMs).coerceAtLeast(1L)
                    // range 모드에서는 directive 탭 비활성 — 음원분리 sheet 안 열리도록.
                    // 색은 edited segment 와 구별되는 짙은 grey — 사용자 가이드.
                    val directiveModifier = Modifier
                        .weight(w.toFloat())
                        .fillMaxHeight()
                        .background(directiveColor)
                        .let { if (!showRange) it.clickable { onDirectiveTap(directive.id) } else it }
                    Box(modifier = directiveModifier)
                    prevEnd = directive.rangeEndMs
                }
                val tail = (totalMs - prevEnd).coerceAtLeast(0L)
                if (tail > 0L) Spacer(Modifier.weight(tail.toFloat()))
            }
        }

        // Layer 2 — 회색 타임라인 strip(=contentHeight 12dp)에 정렬. 트림 핸들도 동일 높이.
        // tap absorber 없음 → 자식 segment.clickable / parent free-interval tap 모두 살림.
        // rangeEndMs <= rangeStartMs (zero-width) = "선택 없음" 상태 → range 시각 모두 숨김 (mode 유지).
        if (showRange && totalMs > 0L && rangeEndMs > rangeStartMs) {
            val startFrac = (rangeStartMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val endFrac = (rangeEndMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val rangeStartDp = totalWidthDp * startFrac
            val rangeWidthDp = totalWidthDp * (endFrac - startFrac).coerceAtLeast(0f)
            // 회색 strip 높이 = contentHeight. 위/아래 inset = (barHeight - contentHeight) / 2.
            val rangeStripInsetY = (barHeight - contentHeight) / 2

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

            // 좌/우 bracket 핸들 — 회색 strip 높이만큼 grip visual.
            val minGap = TimelineBarSpec.MinRangeGapMs
            RangeHandle(
                offsetX = rangeStartDp - handleHitWidth / 2,
                hitWidth = handleHitWidth,
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

        // Layer 3 — 재생 헤드 마커. hit zone 32dp wide drag → scrub.
        // 클램프 제거: hit zone 은 frac 위치 정확히 중심에 두고 좌우 끝에서 hit zone 일부가 바 밖으로
        // overflow 해도 OK (시각 marker line 은 항상 frac 위치 = 영상 길이와 정확히 일치).
        // marker visual 길이는 바 높이 - 16dp (위/아래 inset) — 사용자 요구 "재생바 길이 짧게".
        if (totalMs > 0L) {
            val frac = (playbackPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val hitWidth = TimelineBarSpec.PlaybackHitWidth
            val visualX = totalWidthDp * frac - hitWidth / 2
            val markerHeight = barHeight - TimelineBarSpec.PlaybackMarkerVerticalInset
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
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(TimelineBarSpec.GripWidth)
                        .height(markerHeight)
                        .background(markerColor)
                )
            }
        }
    }
}

/**
 * 추가된 음원(BgmClip) 목록 패널 — 클립별 시작 위치(ms 입력) + 볼륨 + 속도 슬라이더 + 음원분리/삭제 버튼.
 * BGM 은 segment 와 독립이라 별도 panel 로 노출 — Audio separation, 위치/볼륨/속도 모두 inline 조절 가능.
 */
@Composable
private fun BgmClipsPanel(
    clips: List<com.dubcast.shared.domain.model.BgmClip>,
    onUpdateStart: (clipId: String, ms: Long) -> Unit,
    onUpdateVolume: (clipId: String, volume: Float) -> Unit,
    onUpdateSpeed: (clipId: String, speed: Float) -> Unit,
    onSeparate: (clipId: String) -> Unit,
    onDelete: (clipId: String) -> Unit,
) {
    val tokens = LocalDubCastColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panelBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "추가한 음원",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.onBackgroundPrimary,
        )
        clips.forEach { clip ->
            BgmClipRow(
                clip = clip,
                onUpdateStart = onUpdateStart,
                onUpdateVolume = onUpdateVolume,
                onUpdateSpeed = onUpdateSpeed,
                onSeparate = onSeparate,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun BgmClipRow(
    clip: com.dubcast.shared.domain.model.BgmClip,
    onUpdateStart: (String, Long) -> Unit,
    onUpdateVolume: (String, Float) -> Unit,
    onUpdateSpeed: (String, Float) -> Unit,
    onSeparate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val tokens = LocalDubCastColors.current
    val displayName = clip.sourceUri.substringAfterLast('/').substringBeforeLast('.').take(28)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.chipBg, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🎵 $displayName",
                color = tokens.onBackgroundPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSeparate(clip.id) }) { Text("음원분리", fontSize = 12.sp) }
            TextButton(onClick = { onDelete(clip.id) }) { Text("삭제", fontSize = 12.sp) }
        }
        // 시작 위치 — 1초 단위 슬라이더 (영상 길이 modulo 한계는 VM 에서 clamp 안 함, 사용자 자유롭게).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("위치", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
            Slider(
                value = clip.startMs.toFloat(),
                valueRange = 0f..(clip.startMs + 60_000L).toFloat().coerceAtLeast(60_000f),
                onValueChange = { onUpdateStart(clip.id, it.toLong().coerceAtLeast(0L)) },
                modifier = Modifier.weight(1f).scale(scaleX = 1f, scaleY = 0.7f),
            )
            Text("${clip.startMs / 1000}s", style = MaterialTheme.typography.labelSmall, color = tokens.onBackgroundPrimary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("볼륨", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
            Slider(
                value = clip.volumeScale,
                valueRange = com.dubcast.shared.domain.model.BgmClip.MIN_VOLUME..com.dubcast.shared.domain.model.BgmClip.MAX_VOLUME,
                onValueChange = { onUpdateVolume(clip.id, it) },
                modifier = Modifier.weight(1f).scale(scaleX = 1f, scaleY = 0.7f),
            )
            Text("${(clip.volumeScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = tokens.onBackgroundPrimary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("속도", style = MaterialTheme.typography.labelSmall, color = tokens.mutedText)
            Slider(
                value = clip.speedScale,
                valueRange = com.dubcast.shared.domain.model.BgmClip.MIN_SPEED..com.dubcast.shared.domain.model.BgmClip.MAX_SPEED,
                onValueChange = { onUpdateSpeed(clip.id, it) },
                modifier = Modifier.weight(1f).scale(scaleX = 1f, scaleY = 0.7f),
            )
            Text("${(clip.speedScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = tokens.onBackgroundPrimary)
        }
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
    visualWidth: androidx.compose.ui.unit.Dp,
    handleColor: Color,
    gripColor: Color,
    gripHeight: androidx.compose.ui.unit.Dp,
    totalWidthPx: Float,
    totalMs: Long,
    baseMsProvider: () -> Long,
    clamp: (Long) -> Long,
    onChange: (Long) -> Unit,
) {
    var baseMs by remember { mutableStateOf(0L) }
    var accumPx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .width(hitWidth)
            .fillMaxHeight()
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
