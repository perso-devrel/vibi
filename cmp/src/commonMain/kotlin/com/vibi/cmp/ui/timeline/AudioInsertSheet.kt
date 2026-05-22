package com.vibi.cmp.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.cmp.platform.extractAudioPeaks
import com.vibi.cmp.platform.rememberAudioPicker
import com.vibi.cmp.platform.rememberAudioPreviewer
import com.vibi.cmp.platform.rememberAudioRecorder
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import org.koin.compose.koinInject
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/**
 * 음원 삽입 / 즉시 녹음 통합 peek bottom-sheet. iOS 보이스메모처럼 화면 하단에서 슬라이드 UP.
 *
 * - 진입 모드 [AudioInsertMode] 별로 자동 시작 (Recording: 마이크 / Picker: 파일 picker).
 * - 내부 phase: Idle → Recording / 파일선택 대기 → Preview → 사용자가 "삽입"/"폐기" 결정.
 * - 위쪽 ~52% 화면은 타임라인이 그대로 보이고, 사용자가 playhead scrub 으로 삽입 위치 조정 가능.
 * - drag-to-resize / drag-to-dismiss 는 미구현 — 명시적 X 또는 footer 버튼으로만 닫힘.
 *
 * Modifier 는 BoxScope 내에서 `Modifier.align(Alignment.BottomCenter)` 를 받아 위치 결정.
 */
enum class AudioInsertMode { Recording, Picker }

@Composable
fun AudioInsertSheet(
    mode: AudioInsertMode?,
    modifier: Modifier = Modifier,
    onInsert: (uri: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // 종료 애니메이션 동안 child 가 composed 상태로 남아 있어야 하므로 마지막 non-null mode 를 snapshot.
    var stickyMode by remember { mutableStateOf<AudioInsertMode?>(null) }
    LaunchedEffect(mode) { if (mode != null) stickyMode = mode }

    AnimatedVisibility(
        visible = mode != null,
        modifier = modifier,
        enter = slideInVertically(animationSpec = tween(durationMillis = 220)) { it },
        exit = slideOutVertically(animationSpec = tween(durationMillis = 180)) { it },
    ) {
        stickyMode?.let { m ->
            AudioInsertSheetBody(
                mode = m,
                onInsert = onInsert,
                onDismiss = onDismiss,
            )
        }
    }
}

private sealed interface Phase {
    data object Idle : Phase
    data object Recording : Phase
    /**
     * @param recordedDurationMs recorder 의 wall-clock 측정값 (Picker 케이스는 0L).
     *   previewer 가 play() 호출 전까지 durationMs=0 이라 fallback 으로 시간 표시.
     */
    data class Preview(val uri: String, val recordedDurationMs: Long) : Phase
}

/** iPhone Voice Memos 의 record-stop 색. light/dark 무관 항상 동일. */
private val VoiceMemoRed = Color(0xFFFF453A)

/** Voice Memos 식 다크 surface — light/dark theme 무관 sheet 안쪽만 어둡게. */
private val SheetSurface = Color(0xFF1C1C1E)
private val SheetTextPrimary = Color(0xFFF2F2F7)
private val SheetTextSecondary = Color(0xFFAEAEB2)
private val SheetSurfaceElevated = Color(0xFF2C2C2E)

@Composable
private fun AudioInsertSheetBody(
    mode: AudioInsertMode,
    onInsert: (uri: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current

    var phase by remember(mode) { mutableStateOf<Phase>(Phase.Idle) }
    var errorMessage by remember(mode) { mutableStateOf<String?>(null) }

    val recorder = rememberAudioRecorder(
        onRecorded = { uri, durationMs ->
            when {
                // onError 가 먼저 발사됐을 수 있음 (iOS: file size < 1024B 시 권한 안내).
                // 그 경우 빈 파일이라 Preview 의미 없음 → Idle 유지.
                errorMessage != null -> phase = Phase.Idle
                // 너무 짧은 녹음 — 사용자 미스클릭 또는 권한 거부 후 즉시 stop 케이스.
                durationMs < 500L -> {
                    errorMessage = "녹음이 너무 짧습니다 (${durationMs}ms). 다시 시도해주세요."
                    phase = Phase.Idle
                }
                else -> phase = Phase.Preview(uri, recordedDurationMs = durationMs)
            }
        },
        onError = { msg ->
            errorMessage = msg
            phase = Phase.Idle
        },
    )
    // Picker 모드에선 Preview 단계 스킵 — 파일이 영상보다 길면 호출부의 BgmTrimSheet 가 파형 +
    // 미리듣기 + 트림 핸들 + 삽입을 모두 보유한 단일 화면으로 뜨고, 짧으면 즉시 삽입. 사용자가 같은
    // 파형을 두 번 보지 않게. Recording 은 녹음 결과 확인이 의미 있어 Preview 유지.
    val audioPicker = rememberAudioPicker { uri ->
        if (mode == AudioInsertMode.Picker) {
            onInsert(uri)
        } else {
            phase = Phase.Preview(uri, recordedDurationMs = 0L)
        }
    }

    // 자동 모드 진입 — Recording 은 마이크 시작, Picker 는 파일 picker 띄움.
    LaunchedEffect(mode) {
        when (mode) {
            AudioInsertMode.Recording -> {
                phase = Phase.Recording
                recorder.start()
            }
            AudioInsertMode.Picker -> {
                audioPicker.launch()
            }
        }
    }

    // Recording 단계 — currentLevel rolling buffer + 경과 시간.
    var levelSnapshot by remember { mutableStateOf<List<Float>>(emptyList()) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phase) {
        if (phase !is Phase.Recording) {
            levelSnapshot = emptyList()
            elapsedMs = 0L
            return@LaunchedEffect
        }
        val start = TimeSource.Monotonic.markNow()
        val buf = ArrayDeque<Float>()
        while (true) {
            buf.addLast(recorder.currentLevel)
            if (buf.size > LIVE_BUFFER_CAP) buf.removeFirst()
            levelSnapshot = buf.toList()
            elapsedMs = start.elapsedNow().inWholeMilliseconds
            delay(50)
        }
    }

    // Preview 진입 시 (a) metadata 로 실제 파일 길이 검증 (b) peaks 추출.
    // (a) 가 빈 파일 (마이크 권한 거부 등) 가드 — previewer.play() 가 silent fail 하는 케이스 잡음.
    val audioMetadataExtractor = koinInject<AudioMetadataExtractor>()
    val previewUri = (phase as? Phase.Preview)?.uri
    var peaks by remember(previewUri) { mutableStateOf<List<Float>>(emptyList()) }
    var actualDurationMs by remember(previewUri) { mutableLongStateOf(0L) }
    LaunchedEffect(previewUri) {
        val uri = previewUri ?: return@LaunchedEffect
        val info = audioMetadataExtractor.extract(uri)
        if (info == null || info.durationMs < 200L) {
            errorMessage = "녹음 파일이 비어있습니다 (${info?.durationMs ?: 0}ms). " +
                "마이크 권한을 확인해주세요 (iOS 시뮬레이터는 macOS 호스트 설정)."
            phase = Phase.Idle
            return@LaunchedEffect
        }
        actualDurationMs = info.durationMs
        val extracted = extractAudioPeaks(uri, samples = 240)
        if (extracted.isNotEmpty()) peaks = extracted
    }

    // Preview playback.
    val previewer = rememberAudioPreviewer()
    var isPlaying by remember(previewUri) { mutableStateOf(false) }

    // sheet 닫힐 때 진행 중인 recorder/previewer 정리.
    val phaseSnapshot = rememberUpdatedState(phase)
    DisposableEffect(Unit) {
        onDispose {
            when (phaseSnapshot.value) {
                Phase.Recording -> recorder.stop()
                is Phase.Preview -> previewer.stop()
                else -> Unit
            }
        }
    }

    // background() 만으로는 hit test 흡수가 안 됨 — sheet 빈 영역 탭이 뒤쪽 TimelineScreen
    // (SoundDeck 카드, 분리/삽입 버튼) 로 pass-through 되어 "안 보이는 버튼" 이 눌리는 결함 방지.
    val swallowInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(SHEET_HEIGHT_FRACTION)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(SheetSurface)
            .clickable(
                interactionSource = swallowInteraction,
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VibiSpacing.lg, vertical = VibiSpacing.base),
            verticalArrangement = Arrangement.spacedBy(VibiSpacing.lg),
        ) {
            // Header — grabber 가운데 + X 우측. 보이스메모 식.
            SheetHeader(onDismiss = onDismiss)

            errorMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoiceMemoRed.copy(alpha = 0.18f))
                        .padding(horizontal = VibiSpacing.sm, vertical = VibiSpacing.xs),
                ) {
                    Text(msg, color = VoiceMemoRed, style = typo.bodySm)
                }
            }

            // Phase-specific body — 시간 + 파형 (Recording 라이브, Preview 정적+playhead).
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.7f),
                contentAlignment = Alignment.Center,
            ) {
                when (val p = phase) {
                    Phase.Idle -> IdleBody(
                        mode = mode,
                        onPick = { audioPicker.launch() },
                        onRecord = {
                            phase = Phase.Recording
                            recorder.start()
                        },
                    )
                    Phase.Recording -> RecordingBody(
                        levels = levelSnapshot,
                        elapsedMs = elapsedMs,
                    )
                    is Phase.Preview -> PreviewBody(
                        peaks = peaks,
                        progressMs = previewer.progressMs.value,
                        durationMs = previewer.durationMs.value,
                        // metadata extract 결과 (정확) 와 recorder wall-clock (추정) 중 큰 값 — 보통 같지만 metadata 가 신뢰도 높음.
                        recordedDurationMs = maxOf(p.recordedDurationMs, actualDurationMs),
                        isPlaying = isPlaying,
                        onSeek = if (isPlaying) { ms -> previewer.seekTo(ms) } else null,
                    )
                }
            }

            // Footer — Recording 은 큰 정지 버튼만, Preview 는 재생 토글 + 삽입.
            FooterControls(
                phase = phase,
                isPlaying = isPlaying,
                onStop = { recorder.stop() },
                onTogglePlay = {
                    (phase as? Phase.Preview)?.let { p ->
                        if (isPlaying) {
                            previewer.stop()
                            isPlaying = false
                        } else {
                            previewer.play(p.uri, onComplete = { isPlaying = false })
                            isPlaying = true
                        }
                    }
                },
                onInsert = {
                    (phase as? Phase.Preview)?.let { p ->
                        previewer.stop()
                        isPlaying = false
                        onInsert(p.uri)
                    }
                },
            )
        }
    }
}

@Composable
private fun IdleBody(
    mode: AudioInsertMode,
    onPick: () -> Unit,
    onRecord: () -> Unit,
) {
    val typo = LocalVibiTypography.current
    Column(
        verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (mode == AudioInsertMode.Recording) "마이크 준비 중…" else "파일 선택을 기다리는 중…",
            color = SheetTextSecondary,
            style = typo.bodySm,
        )
        OutlinedButton(
            shape = VibiShape.lg,
            onClick = if (mode == AudioInsertMode.Recording) onRecord else onPick,
        ) {
            Text(
                text = if (mode == AudioInsertMode.Recording) "녹음 시작" else "파일 다시 선택",
                color = SheetTextPrimary,
                style = typo.bodySm,
            )
        }
    }
}

@Composable
private fun RecordingBody(
    levels: List<Float>,
    elapsedMs: Long,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(VibiSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = formatElapsed(elapsedMs),
            color = SheetTextPrimary,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
        )
        // 녹음 끝난 후 PreviewBody 의 파형과 완전히 동일한 시각 — 같은 컴포저블, 같은 색상 override.
        // peaks = 라이브 amplitude 버퍼. duration/progress 모두 0 이라 playhead 안 그림.
        // levels 가 비어있을 때 (buffer 가 아직 비어있을 때) WaveformPlayBar 가 fallback progress bar
        // 를 그리는 걸 막기 위해 최소 1 sample 의 0f 를 보장.
        val safeLevels = if (levels.isEmpty()) listOf(0f) else levels
        WaveformPlayBar(
            peaks = safeLevels,
            progressMs = 0L,
            durationMs = 0L,
            isPlaying = false,
            barColorOverride = SheetTextPrimary.copy(alpha = 0.5f),
            playedColorOverride = SheetTextPrimary,
            playheadColorOverride = VoiceMemoRed,
            trackBgOverride = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
    }
}

@Composable
private fun PreviewBody(
    peaks: List<Float>,
    progressMs: Long,
    durationMs: Long,
    recordedDurationMs: Long,
    isPlaying: Boolean,
    onSeek: ((Long) -> Unit)?,
) {
    // previewer 가 play() 호출 전엔 durationMs=0 — recorder 의 wall-clock 시간으로 fallback.
    val displayMs = when {
        isPlaying && durationMs > 0 -> progressMs
        durationMs > 0 -> durationMs
        else -> recordedDurationMs
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(VibiSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = formatElapsed(displayMs),
            color = SheetTextPrimary,
            fontSize = 56.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
        )
        WaveformPlayBar(
            peaks = peaks,
            progressMs = progressMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            barColorOverride = SheetTextPrimary.copy(alpha = 0.5f),
            playedColorOverride = SheetTextPrimary,
            playheadColorOverride = VoiceMemoRed,
            trackBgOverride = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
    }
}

@Composable
private fun FooterControls(
    phase: Phase,
    isPlaying: Boolean,
    onStop: () -> Unit,
    onTogglePlay: () -> Unit,
    onInsert: () -> Unit,
) {
    val typo = LocalVibiTypography.current
    when (phase) {
        Phase.Idle -> Unit
        Phase.Recording -> {
            // 큰 정지 버튼 단독 — 하단 padding 으로 호흡감.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = VibiSpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                VoiceMemoStopButton(onClick = onStop)
            }
        }
        is Phase.Preview -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = VibiSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SheetSurfaceElevated),
                    onClick = onTogglePlay,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "일시정지" else "재생",
                        tint = SheetTextPrimary,
                    )
                }
                Button(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = VibiShape.lg,
                    onClick = onInsert,
                ) { Text("삽입", style = typo.bodySm) }
            }
        }
    }
}

@Composable
private fun VoiceMemoStopButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .border(
                width = 4.dp,
                color = SheetTextPrimary.copy(alpha = 0.4f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(VoiceMemoRed),
        )
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Drag handle (시각적 affordance — drag-to-dismiss 는 v2).
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(SheetTextSecondary.copy(alpha = 0.5f)),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "닫기",
                tint = SheetTextPrimary,
            )
        }
    }
}

/** mm:ss.t (1/10 초 단위) — iPhone Voice Memos 와 동일 자릿수. */
private fun formatElapsed(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val m = total / 60_000
    val s = (total / 1000) % 60
    val tenths = (total / 100) % 10
    val mStr = m.toString().padStart(2, '0')
    val sStr = s.toString().padStart(2, '0')
    return "$mStr:$sStr.$tenths"
}

/** ~45% 의 화면 영역은 타임라인을 그대로 노출 — peek 모드. 큰 시간 + 큰 파형 + 큰 정지 버튼 호흡감. */
private const val SHEET_HEIGHT_FRACTION = 0.55f

/** 50ms 폴링 * 240 = 12초 윈도우. WaveformPlayBar 가 이 버퍼를 canvas 폭으로 리샘플. */
private const val LIVE_BUFFER_CAP = 240
