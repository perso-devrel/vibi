package com.dubcast.cmp.ui.input

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
import androidx.compose.material3.Text
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
import com.dubcast.cmp.platform.MediaPicker
import com.dubcast.cmp.ui.cupertino.BodyText
import com.dubcast.cmp.ui.cupertino.PageScaffold
import com.dubcast.cmp.ui.cupertino.SecondaryText
import com.dubcast.cmp.ui.cupertino.Section
import com.dubcast.cmp.ui.cupertino.SectionRow
import com.dubcast.shared.domain.model.ValidationResult
import com.dubcast.shared.platform.currentTimeMillis
import com.dubcast.shared.platform.formatRelative
import com.dubcast.shared.platform.formatTimestamp
import com.dubcast.shared.ui.input.DraftSummary
import com.dubcast.shared.ui.input.InputViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InputScreen(
    onNavigateToTimeline: (projectId: String) -> Unit,
    viewModel: InputViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 화면 재진입 시 이전 비디오/검증/언어 선택 리셋. drafts ("이어서 작업") 카드는
    // EditProjectRepository.observeAllProjects() 가 영속 상태에서 직접 읽어 노출.
    LaunchedEffect(Unit) { viewModel.onResetSelection() }

    LaunchedEffect(viewModel) {
        viewModel.navigateToTimeline.collect { projectId ->
            onNavigateToTimeline(projectId)
        }
    }

    PageScaffold(title = "영상 선택") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero placeholder — Apple TV+ 처럼 콘텐츠 hero 카드
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF6E45E2),
                                Color(0xFF88D3CE)
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        "Vibi 로 영상을 새롭게 입혀보세요",
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "음원 분리 · 더빙 · 자막 · 간단한 영상 편집까지 한 번에.",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color(0xCCFFFFFF)
                        )
                    )
                }
            }

            Section(header = "비디오") {
                SectionRow {
                    MediaPicker(
                        label = if (state.selectedVideo == null) "갤러리에서 영상 선택"
                                else "다른 영상 선택",
                        onPicked = { uri -> viewModel.onVideoPicked(uri) }
                    )
                }
                state.selectedVideo?.let { info ->
                    SectionRow {
                        Column {
                            BodyText("선택된 영상")
                            Spacer(Modifier.height(2.dp))
                            SecondaryText("${info.durationMs / 1000}초 · ${info.width}×${info.height}")
                        }
                    }
                }
                if (state.isExtracting) {
                    SectionRow { SecondaryText("메타데이터 분석 중…") }
                }
                when (val v = state.validationResult) {
                    ValidationResult.Valid -> SectionRow {
                        Text(
                            "✓  사용 가능",
                            style = TextStyle(
                                fontSize = 17.sp,
                                color = Color(0xFF30D158),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    is ValidationResult.Invalid -> SectionRow {
                        Text(
                            "✕  ${v.reason.name}",
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

            // "이어서 작업" — drafts 가 비어있으면 섹션 자체 숨김.
            // (갤러리에서 영상 선택 섹션 아래에 위치 — 신규 영상 선택 흐름이 우선.)
            if (state.drafts.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "이어서 작업",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
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
    val relative = "마지막 편집: " + formatRelative(draft.updatedAt, now)

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
