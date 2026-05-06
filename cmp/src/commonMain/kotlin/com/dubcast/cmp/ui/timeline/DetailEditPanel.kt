package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dubcast.cmp.theme.LocalDubCastColors
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.ui.timeline.TimelineUiState

/**
 * 상세 편집 panel — lang chip 으로 lang 필터 + cue list + 선택된 cue 의 텍스트·스타일 inline 조정.
 *
 * 사용자가 cue 클릭 → 그 자막의 시간으로 영상 seek + 선택 마킹. cue list 안에서 선택된 cue 바로 아래
 * 텍스트/슬라이더/색 chip 이 inline 으로 펼쳐져 스크롤 없이 즉시 편집 가능. "적용" 버튼은 텍스트 라벨
 * 우측에 inline 배치 — draft 가 dirty 일 때만 활성, 클릭 시 ViewModel 호출 → preview overlay 반영.
 */
@Composable
fun DetailEditPanel(
    state: TimelineUiState,
    onSelectLang: (String) -> Unit,
    onSelectClip: (String?) -> Unit,
    onUpdateText: (clipId: String, newText: String) -> Unit,
    onUpdateStyle: (clipId: String, fontSizeSp: Float?, colorHex: String?, backgroundColorHex: String?) -> Unit,
    onSeekToClip: (positionMs: Long) -> Unit,
) {
    val tokens = LocalDubCastColors.current
    val langs = remember(state.subtitleClips) {
        state.subtitleClips.map { it.languageCode }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    if (langs.isEmpty()) {
        Text("자막이 없어 상세 편집 불가.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val activeLang = state.detailEditLang ?: langs.first()
    val cues = remember(state.subtitleClips, activeLang) {
        state.subtitleClips.filter { it.languageCode == activeLang }.sortedBy { it.startMs }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panelBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 헤더 — 타이틀만. "적용" 버튼은 inline edit area 의 텍스트 라벨 우측으로 이동.
        Text(
            "자막 상세 편집",
            style = MaterialTheme.typography.titleSmall,
            color = tokens.onBackgroundPrimary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            langs.forEach { lang ->
                FilterChip(
                    selected = lang == activeLang,
                    onClick = { onSelectLang(lang) },
                    label = { Text(lang.uppercase()) }
                )
            }
        }

        // cue list — 시간순. 선택된 cue 의 row 아래 inline edit area 가 함께 펼쳐짐.
        // 다른 cue 클릭 시 selectedSubtitleClipId 가 바뀌므로 이전 펼침은 자동으로 닫힘 (단일 selection).
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items = cues, key = { it.id }) { clip ->
                val selected = state.selectedSubtitleClipId == clip.id
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) tokens.chipBg else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                onSelectClip(if (selected) null else clip.id)
                                if (!selected) onSeekToClip(clip.startMs)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${clip.startMs / 1000}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.mutedText,
                        )
                        Text(
                            text = clip.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.onBackgroundPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                        )
                    }
                    if (selected) {
                        InlineCueEditArea(
                            clip = clip,
                            onUpdateText = onUpdateText,
                            onUpdateStyle = onUpdateStyle,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 선택된 cue 의 row 아래에 inline 으로 펼쳐지는 편집 영역.
 *
 * draft state 는 clip.id 로 keyed — cue 가 바뀌면 새로 초기화. "적용" 버튼은 텍스트 라벨 우측에
 * 배치, draft 가 dirty 일 때만 활성. 클릭 시 변경된 필드만 ViewModel 에 반영.
 */
@Composable
private fun InlineCueEditArea(
    clip: SubtitleClip,
    onUpdateText: (clipId: String, newText: String) -> Unit,
    onUpdateStyle: (clipId: String, fontSizeSp: Float?, colorHex: String?, backgroundColorHex: String?) -> Unit,
) {
    val tokens = LocalDubCastColors.current
    var draftText by remember(clip.id, clip.text) { mutableStateOf(clip.text) }
    var draftFontSize by remember(clip.id, clip.fontSizeSp) { mutableStateOf(clip.fontSizeSp) }
    var draftColorHex by remember(clip.id, clip.colorHex) { mutableStateOf(clip.colorHex) }
    var draftBgHex by remember(clip.id, clip.backgroundColorHex) { mutableStateOf(clip.backgroundColorHex) }
    val isDirty = draftText != clip.text ||
        draftFontSize != clip.fontSizeSp ||
        draftColorHex != clip.colorHex ||
        draftBgHex != clip.backgroundColorHex

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "텍스트",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.mutedText,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = isDirty,
                onClick = {
                    if (draftText != clip.text) onUpdateText(clip.id, draftText)
                    val styleChanged = draftFontSize != clip.fontSizeSp ||
                        draftColorHex != clip.colorHex ||
                        draftBgHex != clip.backgroundColorHex
                    if (styleChanged) {
                        onUpdateStyle(
                            clip.id,
                            if (draftFontSize != clip.fontSizeSp) draftFontSize else null,
                            if (draftColorHex != clip.colorHex) draftColorHex else null,
                            if (draftBgHex != clip.backgroundColorHex) draftBgHex else null,
                        )
                    }
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
            ) { Text("적용", fontSize = 13.sp) }
        }
        OutlinedTextField(
            value = draftText,
            onValueChange = { draftText = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "글자 크기: ${draftFontSize.toInt()}sp",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.mutedText,
        )
        Slider(
            value = draftFontSize,
            valueRange = 12f..32f,
            onValueChange = { draftFontSize = it },
        )
        Text("글자 색", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
        ColorPaletteRow(
            selectedHex = draftColorHex,
            palette = TEXT_COLOR_PALETTE,
            onSelect = { draftColorHex = it },
        )
        Text("배경", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
        ColorPaletteRow(
            selectedHex = draftBgHex,
            palette = BG_COLOR_PALETTE,
            showAlpha = true,
            onSelect = { draftBgHex = it },
        )
        Spacer(Modifier.height(2.dp))
    }
}
