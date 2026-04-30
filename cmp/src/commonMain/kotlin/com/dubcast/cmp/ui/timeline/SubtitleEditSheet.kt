package com.dubcast.cmp.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dubcast.shared.domain.model.SubtitleClip

/**
 * 자막 텍스트 + 스타일 편집 sheet — 언어 chip 으로 lang 전환, 상단 스타일 컨트롤 +
 * 하단 cue 별 텍스트 편집.
 *
 * 스타일 변경은 현재 선택된 언어의 모든 cue 에 일괄 적용 (사용자 단순화). slider 는
 * onValueChangeFinished 시점에만 commit 하여 DB write 빈도 제한.
 */
@Composable
fun SubtitleEditSheet(
    selectedLang: String?,
    availableLanguages: List<String>,
    clipsByLang: Map<String, List<SubtitleClip>>,
    onSelectLang: (String) -> Unit,
    onSaveText: (clipId: String, newText: String) -> Unit,
    onUpdateLangStyle: (lang: String, fontSizeSp: Float?, colorHex: String?, backgroundColorHex: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("자막 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (availableLanguages.isEmpty()) {
                    Text("편집할 자막이 없습니다.", style = MaterialTheme.typography.bodyMedium)
                    return@Column
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableLanguages.forEach { lang ->
                        FilterChip(
                            selected = lang == selectedLang,
                            onClick = { onSelectLang(lang) },
                            label = { Text(lang.uppercase()) }
                        )
                    }
                }
                val activeLang = selectedLang ?: availableLanguages.first()
                val cues = clipsByLang[activeLang]?.sortedBy { it.startMs } ?: emptyList()

                StyleSection(
                    sample = cues.firstOrNull(),
                    onSize = { onUpdateLangStyle(activeLang, it, null, null) },
                    onColor = { onUpdateLangStyle(activeLang, null, it, null) },
                    onBackground = { onUpdateLangStyle(activeLang, null, null, it) },
                )

                Spacer(Modifier.height(4.dp))
                Text("자막 텍스트", style = MaterialTheme.typography.titleSmall)

                if (cues.isEmpty()) {
                    Text("$activeLang 자막이 없습니다.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = cues, key = { it.id }) { clip ->
                            CueEditRow(clip = clip, onSave = onSaveText)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("완료") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun StyleSection(
    sample: SubtitleClip?,
    onSize: (Float) -> Unit,
    onColor: (String) -> Unit,
    onBackground: (String) -> Unit,
) {
    val initialSize = sample?.fontSizeSp ?: SubtitleClip.DEFAULT_FONT_SIZE_SP
    val initialColor = sample?.colorHex ?: SubtitleClip.DEFAULT_COLOR_HEX
    val initialBg = sample?.backgroundColorHex ?: SubtitleClip.DEFAULT_BACKGROUND_COLOR_HEX
    var sizeDraft by remember(sample?.id) { mutableStateOf(initialSize) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("스타일 (현재 언어 전체 자막에 일괄 적용)", style = MaterialTheme.typography.titleSmall)
        Text("글자 크기: ${sizeDraft.toInt()}sp", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = sizeDraft,
            valueRange = 12f..32f,
            onValueChange = { sizeDraft = it },
            // drag 종료 시점에 한 번만 DB commit — 매 frame DB write 회피.
            onValueChangeFinished = { onSize(sizeDraft) },
        )
        Text("글자 색", style = MaterialTheme.typography.bodySmall)
        ColorPaletteRow(
            selectedHex = initialColor,
            palette = TEXT_COLOR_PALETTE,
            onSelect = onColor,
        )
        Text("배경", style = MaterialTheme.typography.bodySmall)
        ColorPaletteRow(
            selectedHex = initialBg,
            palette = BG_COLOR_PALETTE,
            onSelect = onBackground,
        )
    }
}

@Composable
private fun CueEditRow(clip: SubtitleClip, onSave: (clipId: String, newText: String) -> Unit) {
    var draft by remember(clip.id, clip.text) { mutableStateOf(clip.text) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${clip.startMs / 1000}s\n${clip.endMs / 1000}s",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.heightIn(min = 56.dp)
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(clip.languageCode.uppercase()) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    DisposableEffect(clip.id) {
        onDispose {
            if (draft != clip.text) onSave(clip.id, draft)
        }
    }
}
