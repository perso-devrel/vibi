package com.vibi.cmp.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vibi.cmp.platform.rememberMediaPickerLauncher
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.ui.account.UserAvatarButton
import com.vibi.cmp.ui.account.UserMenuSheet
import com.vibi.cmp.ui.cupertino.BodyText
import com.vibi.cmp.ui.cupertino.PageScaffold
import com.vibi.cmp.ui.cupertino.SecondaryText
import com.vibi.cmp.ui.cupertino.Section
import com.vibi.cmp.ui.cupertino.SectionRow
import com.vibi.shared.domain.model.ValidationError
import com.vibi.shared.domain.model.ValidationResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vibi.shared.ui.account.UserMenuViewModel
import com.vibi.shared.platform.currentTimeMillis
import com.vibi.shared.platform.formatRelative
import com.vibi.shared.platform.formatTimestamp
import com.vibi.shared.ui.input.DraftSummary
import com.vibi.shared.ui.input.InputViewModel
import com.vibi.shared.ui.input.PreparingSummary
import com.vibi.shared.ui.timeline.localizeProgressReason
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InputScreen(
    onNavigateToTimeline: (projectId: String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: InputViewModel = koinViewModel(),
    userMenuViewModel: UserMenuViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val userMenuState by userMenuViewModel.uiState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    // "준비중" 카드 X 취소 경고 대상 projectId (null = 닫힘). "다시 보지 않기" 영속 시엔 경고 없이 바로 취소.
    var cancelWarnProjectId by remember { mutableStateOf<String?>(null) }

    // 화면 재진입 시 이전 비디오/검증/언어 선택 리셋. drafts ("이어서 작업") 카드는
    // EditProjectRepository.observeAllProjects() 가 영속 상태에서 직접 읽어 노출.
    LaunchedEffect(Unit) { viewModel.onResetSelection() }

    // 홈 진입 시마다 BFF 권위 잔액 1회 refresh — 분리(차감)·구매·다른 기기 변경을 프로필 sheet 를
    // 열지 않아도 크레딧 배지에 반영. Timeline 등에서 돌아올 때도 재진입으로 다시 동기화된다.
    // refreshBalance 는 viewModelScope fire-and-forget 이라 화면 표시를 막지 않는다.
    LaunchedEffect(userMenuViewModel) { userMenuViewModel.refreshBalance() }

    LaunchedEffect(viewModel) {
        viewModel.navigateToTimeline.collect { projectId ->
            onNavigateToTimeline(projectId)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToLogin.collect { onSignedOut() }
    }

    val pickLauncher = rememberMediaPickerLauncher { uri -> viewModel.onVideoPicked(uri) }

    PageScaffold(
        title = "VIBI",
        trailing = {
            UserAvatarButton(
                user = userMenuState.user,
                onClick = { menuOpen = true },
            )
        }
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            GreetingRow(
                name = userMenuState.user?.name,
                credits = userMenuState.credits,
            )

            // Hero CTA — 화면에 들어오자마자 가장 큰 element 가 "갤러리에서 영상 선택".
            // 카드 전체가 클릭 가능. selectedVideo 없으면 강조 그라디언트 + 큰 갤러리 글리프 + CTA 텍스트,
            // 있으면 같은 카드가 "다른 영상 선택" 으로 변환되며 작은 메타 표시.
            Spacer(Modifier.height(16.dp))
            val isFirstPick = state.selectedVideo == null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFirstPick) 240.dp else 180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isFirstPick) listOf(
                                Color(0xFF6E45E2),
                                Color(0xFF88D3CE)
                            ) else listOf(
                                Color(0xFF1F2024),
                                Color(0xFF2A2C32)
                            )
                        )
                    )
                    .clickable { pickLauncher() }
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 큰 갤러리 글리프 — 사진 아이콘 자리. UTF 글리프로 무료, 추후 vector asset 교체 가능.
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "▶",
                            style = TextStyle(
                                fontSize = 30.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        )
                    }
                    Spacer(Modifier.height(if (isFirstPick) 18.dp else 12.dp))
                    Text(
                        text = if (isFirstPick) "Choose a video"
                               else "Pick another video",
                        style = TextStyle(
                            fontSize = if (isFirstPick) 22.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    )
                    if (isFirstPick) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Keep the video. Remove the noise.",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = Color(0xCCFFFFFF),
                            )
                        )
                    }
                }
            }

            // 선택된 영상 메타·검증 결과 — hero 아래 평범한 Section.
            if (state.selectedVideo != null || state.isExtracting || state.validationResult != null) {
                Section(header = "Selected video") {
                    state.selectedVideo?.let { info ->
                        SectionRow {
                            Column {
                                BodyText("${info.durationMs / 1000}s")
                                Spacer(Modifier.height(2.dp))
                                SecondaryText("${info.width}×${info.height}")
                            }
                        }
                    }
                    if (state.isExtracting) {
                        SectionRow { SecondaryText("Analyzing…") }
                    }
                    when (val v = state.validationResult) {
                        ValidationResult.Valid -> SectionRow {
                            Text(
                                "Ready to import",
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    color = Color(0xFF30D158),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        is ValidationResult.Invalid -> SectionRow {
                            Text(
                                v.reason.userMessage(),
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    color = Color(0xFFFF453A),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        null -> Unit
                    }
                }
            }

            // "작업 준비중" — 영상 선택 직후 백그라운드로 전체 음원분리가 도는 프로젝트들.
            // drafts 위에 위치. 분리 완료되면 카드가 여기서 빠지고 아래 "이어서 작업"으로 내려간다.
            if (state.preparing.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Preparing",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Separating audio. We'll have it ready soon.",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items = state.preparing, key = { it.projectId }) { item ->
                        PreparingCard(
                            item = item,
                            onRetry = { viewModel.onRetryPreparing(item.projectId) },
                            onDelete = {
                                if (viewModel.preparingCancelNeedsWarning(item.failed)) {
                                    cancelWarnProjectId = item.projectId
                                } else {
                                    viewModel.onDeleteDraft(item.projectId)
                                }
                            },
                        )
                    }
                }
            }

            // "이어서 작업" — drafts 가 비어있으면 섹션 자체 숨김.
            // (갤러리에서 영상 선택 섹션 아래에 위치 — 신규 영상 선택 흐름이 우선.)
            if (state.drafts.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Drafts",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Drafts are kept for 7 days.",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items = state.drafts, key = { it.projectId }) { draft ->
                        DraftCard(
                            draft = draft,
                            onClick = { viewModel.onContinueDraft(draft.projectId) },
                            onDelete = { viewModel.onDeleteDraft(draft.projectId) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
      }
    }

    if (menuOpen) {
        UserMenuSheet(
            onDismiss = { menuOpen = false },
            onSignedOut = {
                menuOpen = false
                onSignedOut()
            },
            viewModel = userMenuViewModel,
        )
    }

    // 분리 시작 확인 — 영상 선택 직후 바로 분리하지 않고, 영상 길이만큼의 크레딧 소모를 고지한 뒤 시작.
    // 차감량(분당 1크레딧, 올림)은 BFF 가 단일 source 로 state.separationCreditCost 에 담긴다.
    // 취소하면 선택만 해제(크레딧 미소모). 잔액 부족 시엔 시작 후 "준비중" 카드가 실패로 안내.
    if (state.awaitingSeparationConfirm) {
        val credits = state.separationCreditCost ?: 1
        AlertDialog(
            onDismissRequest = { viewModel.onCancelStartSeparation() },
            title = { Text("Start audio separation?") },
            text = {
                Text(
                    if (credits == 1) {
                        "This will use 1 credit to separate the audio from your video."
                    } else {
                        "This will use $credits credits to separate the audio from your video."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmStartSeparation() }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelStartSeparation() }) { Text("Cancel") }
            },
        )
    }

    // "준비중" 카드 X 취소 경고 — 진행 중 분리는 어느 단계든 취소 시 크레딧 환불이 안 되므로 항상 고지.
    // "다시 보지 않기" 체크는 계정별로 영속(타임라인 취소 경로와 공유)돼 다음부터는 바로 취소된다.
    cancelWarnProjectId?.let { projectId ->
        var dontShowAgain by remember(projectId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { cancelWarnProjectId = null },
            title = { Text("Stop audio separation?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Separation will stop and this project will be removed. " +
                            "Credits already used won't be refunded.",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(checkedColor = LocalVibiColors.current.accent),
                        )
                        Text("Don't show this again")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontShowAgain) viewModel.setSkipSeparationCancelWarning(true)
                    viewModel.onDeleteDraft(projectId)
                    cancelWarnProjectId = null
                }) { Text("Stop separation") }
            },
            dismissButton = {
                TextButton(onClick = { cancelWarnProjectId = null }) { Text("Keep processing") }
            },
        )
    }
}

/**
 * "이어서 작업" 한 카드. 클릭 → onContinueDraft, 우상단 X → onDeleteDraft.
 *
 * title 이 null 이면 createdAt 의 fallback 표시. 본 phase 에선 타이틀 편집 UI / 썸네일 추출
 * 없음 — minimal text-only 카드.
 */
@Composable
private fun DraftCard(
    draft: DraftSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val titleText = draft.title?.takeIf { it.isNotBlank() }
        ?: formatTimestamp(draft.createdAt)
    val now = currentTimeMillis()
    val relative = "Last edited " + formatRelative(draft.updatedAt, now)

    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2C2C2E),
                        Color(0xFF1C1C1E),
                    )
                )
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 비디오 한 장면 썸네일 — InputViewModel 이 사전 추출한 JPEG path 를 Coil AsyncImage 로 표시.
            // ExoPlayer/AVPlayer 인스턴스 없이 정적 이미지 한 장만 메모리 → drafts N개에서도 비용 일정.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                val thumb = draft.thumbnailPath
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = titleText,
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    ),
                    modifier = Modifier.padding(end = 24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = relative,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xCCFFFFFF),
                    )
                )
                draft.jobsRunningSummary?.let { summary ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF30D158))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = summary,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = Color(0xCCFFFFFF),
                            )
                        )
                    }
                }
            }
        }
        // 우상단 작은 X 버튼 — 카드 전체 클릭 영역과 분리하기 위해 별도 clickable Box.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            )
        }
    }
}

/**
 * "작업 준비중" 한 카드. [DraftCard] 와 같은 썸네일/레이아웃이되, 하단에 음원분리 진행 바 + 퍼센트를
 * 보여준다. 진행 중에는 탭 비활성(분리 완료 전 에디터 진입 불가), 실패 시 "다시 시도" 노출.
 */
@Composable
private fun PreparingCard(
    item: PreparingSummary,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val titleText = item.title?.takeIf { it.isNotBlank() }
        ?: formatTimestamp(item.createdAt)

    Box(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2C2C2E),
                        Color(0xFF1C1C1E),
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                val thumb = item.thumbnailPath
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = titleText,
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    ),
                    modifier = Modifier.padding(end = 24.dp)
                )
                Spacer(Modifier.height(8.dp))
                if (item.failed) {
                    Text(
                        text = if (item.insufficientCredits) "Not enough credits" else "Separation failed",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = Color(0xFFFF453A),
                            fontWeight = FontWeight.Medium,
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Retry",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF30D158),
                        trackColor = Color(0x33FFFFFF),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${item.progress}% · ${localizeProgressReason(item.progressReason)}",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = Color(0xCCFFFFFF),
                        )
                    )
                }
            }
        }
        // 우상단 X — 준비중 취소(프로젝트 삭제). DraftCard 와 동일 패턴.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✕",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            )
        }
    }
}

@Composable
private fun GreetingRow(name: String?, credits: Int) {
    val safeName = name?.trim()?.takeIf { it.isNotBlank() }
    val title = if (safeName != null) "Hi, $safeName" else "What are we making today?"
    val sub = if (safeName != null) "Keep your audio clean" else "VIBI removes the noise for you"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
        // 잔여 크레딧 칩은 결제(iapEnabled)와 무관하게 항상 노출 — 무료 선출시에도 남은 무료
        // 분리 횟수를 보여준다. 구매/충전 UI 만 iapEnabled 로 게이팅(다른 화면에서 처리).
        val tokens = LocalVibiColors.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tokens.chipBg)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = "✦",
                style = TextStyle(fontSize = 12.sp, color = tokens.accent)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$credits",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            )
        }
    }
}

private fun ValidationError.userMessage(): String = when (this) {
    ValidationError.UNSUPPORTED_FORMAT ->
        "Unsupported video format. Please choose an MP4, MOV, or WebM file."
    ValidationError.DURATION_EXCEEDS_LIMIT ->
        "Videos must be 5 minutes or shorter."
    ValidationError.RESOLUTION_EXCEEDS_LIMIT ->
        "Video resolution can't exceed 1920 pixels on the longest side."
    ValidationError.METADATA_UNREADABLE ->
        "We couldn't read this video. Please try a different file."
}
