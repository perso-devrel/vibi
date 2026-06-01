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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.vibi.cmp.theme.BgmPalette
import com.vibi.cmp.theme.SpeakerPalette
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
    /** Slider 드래그 종료 시 1회. null 이면 미사용 (예: stem 분리 결과 — 별도 undo policy). */
    onUpdateVolumeFinished: (() -> Unit)? = null,
    onTogglePreview: () -> Unit,
    onDelete: (() -> Unit)?,
    onApplySpeed: ((Float) -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    /** 이름 옆 연필 — null 이면 미표시 (stem 카드). BGM·녹음 카드만 주입해 이름 편집 진입. */
    onRename: (() -> Unit)? = null,
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

    // BGM 은 timeline 클립 탭 → 하단바로 편집 — 카드 long-press expand 폐기.
    // stem 카드는 inline 볼륨 슬라이더가 long-press 로 펼쳐지는 패턴 유지.
    val canLongPressExpand = model.kind != SoundCardKind.BGM
    val gestureModifier = modifier.combinedClickable(
        enabled = !disabled,
        onClick = onToggle,
        onLongClick = if (canLongPressExpand) ({ expanded = !expanded }) else null,
    )

    VibiPanelCard(modifier = gestureModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xs)) {
            Row(
                modifier = Modifier.alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chipColor = when (model.kind) {
                    SoundCardKind.SPEAKER -> SpeakerPalette.colorFor(model.speakerIndex, tokens)
                    SoundCardKind.VOICE_ALL -> tokens.accent
                    SoundCardKind.BACKGROUND -> tokens.mutedText
                    SoundCardKind.BGM -> BgmPalette.colorFor(model.bgmIndex, tokens)
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
                        // 펼친(=expanded) 상태에선 이름 줄임 없이 전체 노출 — 긴 BGM 파일명을 다듬기 패널 진입 시 확인.
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
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
                // 이름 옆 연필 — BGM·녹음 카드만 (onRename != null). 이름 편집 다이얼로그 진입.
                // 카드 row 의 combinedClickable 보다 IconButton 자체 클릭이 우선 소비.
                if (onRename != null) {
                    IconButton(
                        onClick = onRename,
                        enabled = !disabled,
                        modifier = Modifier.size(VibiSpacing.touchMin),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = tokens.onBackgroundPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(VibiSpacing.base),
                        )
                    }
                }
                // Long-press 발견성 hint — 점 3개 affordance 로 "추가 옵션 있음" 암시. BGM 은
                // long-press 비활성이라 미표시. 펼친 상태에서도 미표시 (이미 발견됨). clickable 없음
                // — 시각 전용, 손가락은 카드 row 의 combinedClickable 이 받음.
                if (canLongPressExpand && !expanded) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = tokens.onBackgroundPrimary.copy(alpha = 0.35f),
                        modifier = Modifier.size(VibiSpacing.base),
                    )
                }
                // 재생/정지 버튼 — stem · BGM 공통. IconButton 의 자체 클릭이 카드의 combinedClickable
                // (onClick=onToggle) 보다 우선 소비. 다듬기/볼륨 진입은 long-press (expanded) 로 분리.
                IconButton(
                    onClick = onTogglePreview,
                    enabled = !disabled && !model.audioUrl.isNullOrBlank(),
                    modifier = Modifier.size(VibiSpacing.touchMin),
                ) {
                    Icon(
                        imageVector = if (isPreviewing) Icons.Filled.Pause
                                      else Icons.Filled.PlayArrow,
                        contentDescription = if (isPreviewing) "Stop" else "Play",
                        tint = tokens.onBackgroundPrimary,
                    )
                }
            }

            // BGM 은 timeline 클립 탭 → 하단바로 편집 진입. SoundCard 의 expand 영역은 stem 전용
            // 볼륨 슬라이더만 — BGM 카드는 long-press 가 없어 expanded 가 영영 false 라 본 영역 미렌더.
            AnimatedVisibility(visible = expanded && canLongPressExpand) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (disabled) 0.4f else 1f),
                ) {
                    Text(
                        "Volume",
                        modifier = Modifier.width(VibiSpacing.xxl),
                        style = typo.bodySm,
                        color = tokens.mutedText,
                    )
                    // stem 분리 결과 슬라이더 — accent (잉크 near-black) 와 M3 기본 surfaceContainerHighest
                    // (라벤더 0xFFE6E0E9, 우리 colorScheme 미override) 두 색을 모두 muted gray 로 통일.
                    // 카드 자체 색이 화자/배경 팔레트라 슬라이더에 컬러 더하면 시각 과부하.
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = model.volume.coerceIn(0f, 2f),
                        onValueChange = { if (!disabled) onUpdateVolume(it) },
                        onValueChangeFinished = onUpdateVolumeFinished,
                        valueRange = 0f..2f,
                        enabled = !disabled && model.selected,
                        colors = mutedSliderColors(tokens.mutedText),
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

private fun formatRange(startMs: Long?, endMs: Long?): String? {
    if (startMs == null || endMs == null) return null
    if (endMs <= startMs) return null
    val s = startMs / 1000
    val e = endMs / 1000
    return "${s}s ~ ${e}s"
}
