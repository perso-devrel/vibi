package com.vibi.cmp.ui.timeline.sounddeck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.cmp.ui.components.VibiPanelCard

/**
 * 분리된 stem 또는 BGM 한 장. 카드 외형 자체가 켜기/끄기 상태 — 별도 토글 위젯 없음.
 *
 *  - tap = selected 토글
 *  - long-press = 펼치기 (볼륨 / 미리듣기 / 삭제 등 부가 액션)
 *
 * 진행 중인 잡(분리/생성) 일 때 [disabled] — 더 흐리고 input ignore.
 *
 * DESIGN.md `panel-card` 베이스 — [VibiPanelCard]. tap/longPress 제스처는 modifier 합성으로
 * VibiPanelCard 의 onClick 슬롯 대신 outer 에서 부착.
 *
 * `combinedClickable` 사용 이유 — `pointerInput` 의 block 은 key 가 안 바뀌면 코루틴이 유지돼
 * onTap 람다가 첫 컴포지션의 [onToggle] 캡처를 영구 보존, [model.selected] 변경이 반영 안 돼
 * 토글 켜기가 영원히 안 됨. clickable 계열은 매 컴포지션 람다 갱신.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundCard(
    model: SoundCardModel,
    disabled: Boolean,
    isPreviewing: Boolean,
    onToggle: () -> Unit,
    onUpdateVolume: (Float) -> Unit,
    onTogglePreview: () -> Unit,
    onDelete: (() -> Unit)?,
    onApplySpeed: ((Float) -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    var expanded by remember { mutableStateOf(false) }
    // 세 가지 시각 상태: 진행 중(가장 흐림) > 꺼짐(중간) > 켜짐(불투명).
    // 꺼짐은 여전히 인터랙티브하므로 alpha 만 변경, 제스처는 disabled 만 차단.
    val contentAlpha = when {
        disabled -> 0.4f
        !model.selected -> 0.45f
        else -> 1f
    }

    val gestureModifier = modifier.combinedClickable(
        enabled = !disabled,
        onClick = onToggle,
        onLongClick = { expanded = !expanded },
    )

    VibiPanelCard(modifier = gestureModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
            Row(
                modifier = Modifier.alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chipColor = when (model.kind) {
                    SoundCardKind.SPEAKER -> tokens.accent
                    SoundCardKind.VOICE_ALL -> tokens.accent
                    SoundCardKind.BACKGROUND -> tokens.mutedText
                    SoundCardKind.BGM -> tokens.accent.copy(alpha = 0.7f)
                    SoundCardKind.OTHER_STEM -> tokens.chipBg
                }
                Box(
                    modifier = Modifier.size(VibiSpacing.sm).clip(CircleShape).background(chipColor),
                )
                Spacer(Modifier.width(VibiSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.label,
                        style = typo.bodyStrong,
                        color = tokens.onBackgroundPrimary,
                        textDecoration = if (model.selected) TextDecoration.None else TextDecoration.LineThrough,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val rangeStr = formatRange(model.rangeStartMs, model.rangeEndMs)
                    if (rangeStr != null) {
                        Text(
                            rangeStr,
                            style = typo.caption,
                            color = tokens.mutedText,
                        )
                    }
                }
                // stem 카드는 별도 헤더 액션이 없어 재생 indicator 가 필요하지만, BGM 카드는
                // 우측 재생/정지 IconButton 의 Pause 아이콘이 같은 상태를 더 명확히 표현하므로 생략.
                if (isPreviewing && model.kind != SoundCardKind.BGM) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(VibiSpacing.base),
                        strokeWidth = 2.dp,
                        color = tokens.accent,
                    )
                }
                // BGM 카드 헤더의 재생/정지 버튼 — IconButton 의 자체 클릭이 카드의 combinedClickable
                // (onClick=onToggle) 보다 우선 소비. 다듬기 진입은 long-press (onEdit) 로 분리.
                if (model.kind == SoundCardKind.BGM) {
                    IconButton(
                        onClick = onTogglePreview,
                        enabled = !disabled && !model.audioUrl.isNullOrBlank(),
                        modifier = Modifier.size(VibiSpacing.xl),
                    ) {
                        Icon(
                            imageVector = if (isPreviewing) Icons.Filled.Pause
                                          else Icons.Filled.PlayArrow,
                            contentDescription = if (isPreviewing) "정지" else "재생",
                            tint = tokens.onBackgroundPrimary,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.alpha(if (disabled) 0.4f else 1f),
                    verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
                ) {
                    if (model.kind == SoundCardKind.BGM &&
                        onApplySpeed != null && onSecondaryAction != null && onDelete != null
                    ) {
                        // BGM 다듬기 — 영상 다듬기와 동일한 4-액션(볼륨/속도/배경음 제거/삭제) 패널.
                        // 볼륨은 슬라이더 드래그 = 즉시 적용(onUpdateVolume → bgm.volumeScale).
                        // 속도는 ripple 효과(뒤 BGM 클립 startMs shift), 배경음 제거는 음원분리 sheet 진입.
                        // 패널 닫기는 카드 long-press collapse 로 — onCancel 생략.
                        // model.speed 가 바뀔 때만 pendingSpeed 재초기화 — 사용자가 드래그 중인
                        // 값이 외부 재합성으로 덮어쓰이는 사고 방지.
                        var pendingSpeed by remember(model.speed) { mutableStateOf(model.speed) }
                        EditActionsPanel(
                            title = "",
                            volume = model.volume,
                            speed = pendingSpeed,
                            onVolumeChange = { if (!disabled) onUpdateVolume(it) },
                            onSpeedChange = { pendingSpeed = it },
                            onApplyVolume = { if (!disabled) onUpdateVolume(it) },
                            onApplySpeed = { if (!disabled) onApplySpeed(it) },
                            secondaryActionLabel = "배경음 제거",
                            onSecondaryAction = { if (!disabled) onSecondaryAction() },
                            onDelete = { if (!disabled) onDelete() },
                            onCancel = null,
                        )
                    } else {
                        // stem 카드 — 기존 부가 액션 row (미리듣기 + 삭제) + 볼륨 슬라이더.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onTogglePreview,
                                enabled = !disabled && !model.audioUrl.isNullOrBlank(),
                            ) {
                                Icon(
                                    imageVector = if (isPreviewing) Icons.Filled.Pause
                                                  else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPreviewing) "정지" else "미리듣기",
                                    tint = tokens.onBackgroundPrimary,
                                )
                                Spacer(Modifier.width(VibiSpacing.xxs))
                                Text(
                                    if (isPreviewing) "정지" else "이 소리만 듣기",
                                    color = tokens.onBackgroundPrimary,
                                    style = typo.bodySm,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (onDelete != null) {
                                IconButton(
                                    onClick = onDelete,
                                    enabled = !disabled,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "삭제",
                                        tint = tokens.mutedText,
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "볼륨",
                                modifier = Modifier.width(VibiSpacing.xxl),
                                style = typo.bodySm,
                                color = tokens.mutedText,
                            )
                            Slider(
                                modifier = Modifier.weight(1f),
                                value = model.volume.coerceIn(0f, 2f),
                                onValueChange = { if (!disabled) onUpdateVolume(it) },
                                valueRange = 0f..2f,
                                enabled = !disabled && model.selected,
                            )
                            Text(
                                "${(model.volume * 100).toInt()}%",
                                modifier = Modifier.width(VibiSpacing.xxl),
                                style = typo.bodySm,
                                color = tokens.mutedText,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatRange(startMs: Long?, endMs: Long?): String? {
    if (startMs == null || endMs == null) return null
    if (endMs <= startMs) return null
    val s = startMs / 1000
    val e = endMs / 1000
    return "${s}s ~ ${e}s"
}
