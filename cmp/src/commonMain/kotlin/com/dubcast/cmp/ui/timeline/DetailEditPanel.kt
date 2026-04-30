package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dubcast.cmp.theme.LocalDubCastColors
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.ui.timeline.TimelineUiState

/**
 * 상세 편집 panel — lang chip 으로 lang 필터 + cue list + 선택된 cue 의 텍스트·스타일 inline 조정.
 *
 * 사용자가 cue 클릭 → 그 자막의 시간으로 영상 seek + 선택 마킹. 텍스트/슬라이더/색 chip 변경 시
 * 즉시 ViewModel 호출 → preview overlay 자동 반영 (편집 화면 보면서 조정).
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
    val selectedClip = state.subtitleClips.firstOrNull { it.id == state.selectedSubtitleClipId }

    // draft state — 사용자가 텍스트/스타일 변경해도 즉시 ViewModel 반영 X. "적용" 버튼 클릭 시만 commit.
    val selectedClipId = selectedClip?.id
    var draftText by remember(selectedClipId, selectedClip?.text) {
        mutableStateOf(selectedClip?.text ?: "")
    }
    var draftFontSize by remember(selectedClipId, selectedClip?.fontSizeSp) {
        mutableStateOf(selectedClip?.fontSizeSp ?: 16f)
    }
    var draftColorHex by remember(selectedClipId, selectedClip?.colorHex) {
        mutableStateOf(selectedClip?.colorHex ?: "")
    }
    var draftBgHex by remember(selectedClipId, selectedClip?.backgroundColorHex) {
        mutableStateOf(selectedClip?.backgroundColorHex ?: "")
    }
    val isDirty = selectedClip != null && (
        draftText != selectedClip.text ||
            draftFontSize != selectedClip.fontSizeSp ||
            draftColorHex != selectedClip.colorHex ||
            draftBgHex != selectedClip.backgroundColorHex
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.panelBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "자막 상세 편집",
                style = MaterialTheme.typography.titleSmall,
                color = tokens.onBackgroundPrimary,
                modifier = Modifier.weight(1f),
            )
            // 우상단 "적용" 버튼 — selectedClip 있고 draft 가 dirty 일 때만 활성.
            androidx.compose.material3.Button(
                enabled = isDirty,
                onClick = {
                    val clip = selectedClip ?: return@Button
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
            ) { Text("적용", fontSize = 13.sp) }
        }
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

        // cue list — 시간순. 선택 시 selectedSubtitleClipId 설정 + 영상 seek.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items = cues, key = { it.id }) { clip ->
                val selected = state.selectedSubtitleClipId == clip.id
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
            }
        }

        // 선택된 cue 의 텍스트 + 스타일 panel — draft 만 update, "적용" 버튼 클릭 시 commit.
        selectedClip?.let { _ ->
            Spacer(Modifier.height(4.dp))
            Text("텍스트", style = MaterialTheme.typography.labelMedium, color = tokens.mutedText)
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
                onSelect = { draftBgHex = it },
            )
        }
    }
}
