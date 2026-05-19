package com.vibi.cmp.ui.timeline.sounddeck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vibi.cmp.platform.rememberAudioPreviewer
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.cmp.ui.components.VibiPanelCard

/**
 * 분리된 stem + BGM 들을 구간(directive) 별 섹션으로 묶어 보여주는 컨테이너.
 *
 * 책임:
 *  - 그룹·카드 모델링은 [buildSoundDeckGroups] 에 위임
 *  - 카드 한 장의 인터랙션은 [SoundCard] 에 위임
 *  - 본 컴포저블은 섹션 헤더 렌더 + 미리듣기 로컬 상태 관리만
 *
 * 분리 구간은 collapsible "+" 카드 — 헤더 탭으로 펼침/접힘. BGM 은 보통 1-2개라 단순 헤더 유지.
 *
 * 진행 중 잡(분리/생성) 일 때 [disabled=true] — 카드 alpha 낮추고 클릭 ignore.
 */
@Composable
fun SoundDeck(
    groups: List<SoundDeckGroup>,
    disabled: Boolean,
    expandedSeparationIds: Set<String>,
    onToggleSeparationExpanded: (String) -> Unit,
    onToggleStem: (directiveId: String, stemId: String, selected: Boolean) -> Unit,
    onUpdateStemVolume: (directiveId: String, stemId: String, volume: Float) -> Unit,
    onUpdateBgmVolume: (clipId: String, volume: Float) -> Unit,
    onApplyBgmSpeed: (clipId: String, value: Float) -> Unit,
    onRemoveBgmBackground: (clipId: String) -> Unit,
    onDeleteBgm: (clipId: String) -> Unit,
    onAddSeparation: (() -> Unit)?,
    onAddBgm: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    val previewer = rememberAudioPreviewer()
    var previewingKey by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            previewer.stop()
        }
    }

    fun cardCallbacks(card: SoundCardModel): SoundCardCallbacks = SoundCardCallbacks(
        onToggle = {
            when (val src = card.source) {
                is SoundCardSource.SeparationStem ->
                    onToggleStem(src.directiveId, src.stemId, !card.selected)
                is SoundCardSource.Bgm -> {
                    val next = if (card.volume > 0f) 0f else 1f
                    onUpdateBgmVolume(src.clipId, next)
                }
            }
        },
        onUpdateVolume = { v ->
            when (val src = card.source) {
                is SoundCardSource.SeparationStem ->
                    onUpdateStemVolume(src.directiveId, src.stemId, v)
                is SoundCardSource.Bgm ->
                    onUpdateBgmVolume(src.clipId, v)
            }
        },
        onTogglePreview = {
            val url = card.audioUrl
            if (url.isNullOrBlank()) return@SoundCardCallbacks
            if (previewingKey == card.key) {
                previewer.stop()
                previewingKey = null
            } else {
                previewer.stop()
                previewer.play(
                    url = url,
                    volume = card.volume.coerceIn(0f, 1f),
                    onComplete = { previewingKey = null },
                )
                previewingKey = card.key
            }
        },
        onDelete = when (val src = card.source) {
            is SoundCardSource.Bgm -> ({
                if (previewingKey == card.key) {
                    previewer.stop()
                    previewingKey = null
                }
                onDeleteBgm(src.clipId)
            })
            is SoundCardSource.SeparationStem -> null
        },
        onApplySpeed = when (val src = card.source) {
            is SoundCardSource.Bgm -> ({ v -> onApplyBgmSpeed(src.clipId, v) })
            is SoundCardSource.SeparationStem -> null
        },
        onSecondaryAction = when (val src = card.source) {
            is SoundCardSource.Bgm -> ({ onRemoveBgmBackground(src.clipId) })
            is SoundCardSource.SeparationStem -> null
        },
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VibiSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "소리",
                style = typo.titleSm,
                color = tokens.onBackgroundPrimary,
            )
            Spacer(modifier = Modifier.width(VibiSpacing.xs))
            Text(
                if (groups.isEmpty()) "분리된 소리·삽입한 음원이 여기 카드로 모입니다"
                else "구간을 펼쳐 분리된 소리를 조절하세요",
                style = typo.bodySm,
                color = tokens.mutedText,
            )
        }

        groups.forEach { group ->
            key(group.key) {
                when (group) {
                    is SoundDeckGroup.Separation -> CollapsibleSeparationCard(
                        group = group,
                        disabled = disabled,
                        previewingKey = previewingKey,
                        expanded = group.directiveId in expandedSeparationIds,
                        onToggleExpanded = { onToggleSeparationExpanded(group.directiveId) },
                        callbacksFor = ::cardCallbacks,
                    )
                    is SoundDeckGroup.Bgm -> Column(
                        verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                    ) {
                        group.cards.forEach { card ->
                            key(card.key) {
                                SoundCardRow(
                                    card = card,
                                    disabled = disabled,
                                    isPreviewing = previewingKey == card.key,
                                    callbacks = cardCallbacks(card),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (onAddSeparation != null) {
            AddSourceCard(
                label = "음원 분리",
                description = "원하는 구간을 골라 화자·배경음으로 나눠요",
                enabled = !disabled,
                onClick = onAddSeparation,
            )
        }
        if (onAddBgm != null) {
            AddSourceCard(
                label = "음원 삽입",
                description = "BGM 파일 업로드 또는 즉시 녹음",
                enabled = !disabled,
                onClick = onAddBgm,
            )
        }
    }
}

private data class SoundCardCallbacks(
    val onToggle: () -> Unit,
    val onUpdateVolume: (Float) -> Unit,
    val onTogglePreview: () -> Unit,
    val onDelete: (() -> Unit)?,
    val onApplySpeed: ((Float) -> Unit)?,
    val onSecondaryAction: (() -> Unit)?,
)

@Composable
private fun SoundCardRow(
    card: SoundCardModel,
    disabled: Boolean,
    isPreviewing: Boolean,
    callbacks: SoundCardCallbacks,
) {
    SoundCard(
        model = card,
        disabled = disabled,
        isPreviewing = isPreviewing,
        onToggle = callbacks.onToggle,
        onUpdateVolume = callbacks.onUpdateVolume,
        onTogglePreview = callbacks.onTogglePreview,
        onDelete = callbacks.onDelete,
        onApplySpeed = callbacks.onApplySpeed,
        onSecondaryAction = callbacks.onSecondaryAction,
    )
}

/**
 * 분리 구간 한 개를 collapsible "+" 블럭으로. 펼침 상태는 호출부가 hoist —
 * 타임라인 파형의 화자별 색 표시와 같은 진실을 공유.
 */
@Composable
private fun CollapsibleSeparationCard(
    group: SoundDeckGroup.Separation,
    disabled: Boolean,
    previewingKey: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    callbacksFor: (SoundCardModel) -> SoundCardCallbacks,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    val rangeText = formatSectionRange(group.rangeStartMs, group.rangeEndMs)

    VibiPanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !disabled) { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(VibiSpacing.lg)
                        .clip(CircleShape)
                        .background(tokens.chipBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.Remove else Icons.Filled.Add,
                        contentDescription = if (expanded) "접기" else "펼치기",
                        tint = tokens.onBackgroundPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(VibiSpacing.sm))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(VibiSpacing.xxs),
                ) {
                    Text(
                        "구간 ${group.index}",
                        style = typo.bodyStrong,
                        color = tokens.onBackgroundPrimary,
                    )
                    if (rangeText != null) {
                        Text(
                            rangeText,
                            style = typo.bodySm,
                            color = tokens.mutedText,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
                    group.cards.forEach { card ->
                        key(card.key) {
                            SoundCardRow(
                                card = card,
                                disabled = disabled,
                                isPreviewing = previewingKey == card.key,
                                callbacks = callbacksFor(card),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatSectionRange(startMs: Long, endMs: Long): String? {
    if (endMs <= startMs) return null
    return "${formatMs(startMs)} ~ ${formatMs(endMs)}"
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (s < 10) "$m:0$s" else "$m:$s"
}
