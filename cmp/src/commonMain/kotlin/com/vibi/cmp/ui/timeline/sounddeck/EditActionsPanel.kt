package com.vibi.cmp.ui.timeline.sounddeck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing

/**
 * 다듬기(볼륨/속도/secondary/삭제) 액션 패널 — 영상 다듬기 모드(전역) 와 BGM 카드 양쪽에서 동일
 * 레이아웃으로 재사용. 볼륨/속도는 토글(슬라이더 펼침), secondary/삭제는 즉시 액션. 4 개 모두
 * 아이콘 버튼 — 라벨 문자열은 접근성용 contentDescription 으로만 남음 (글리프/짧은 텍스트가 좁은
 * 버튼 폭에서 잘리지 않게).
 *
 * - [title] 빈 문자열이면 헤더의 제목 영역 생략.
 * - [secondaryActionIcon] / [secondaryActionContentDescription] / [onSecondaryAction] — 컨텍스트별 3번째 액션.
 *   영상·BGM 모두 "복제" 라 ContentCopy 아이콘. 추후 다른 액션으로 교체해도 같은 슬롯에서 처리.
 * - [tertiaryActionLabel] — 옵셔널 5번째 슬롯. BGM 의 "배경음 제거 ↔ 원래대로" 토글용. 텍스트로
 *   유지 — 상태가 사용자에게 즉시 분간되어야 함 (같은 아이콘으로는 토글 방향 식별 불가).
 * - [onCancel] null 이면 닫기(X) 버튼 생략 — BGM in-card 의 경우 부모 카드 collapse 가 닫기 역할.
 */
@Composable
fun EditActionsPanel(
    title: String,
    volume: Float,
    speed: Float,
    onVolumeChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onApplyVolume: (Float) -> Unit,
    onApplySpeed: (Float) -> Unit,
    secondaryActionIcon: ImageVector,
    secondaryActionContentDescription: String,
    onSecondaryAction: () -> Unit,
    onDelete: () -> Unit,
    onCancel: (() -> Unit)? = null,
    // 5번째(맨 오른쪽) 액션 — 옵셔널. BGM 의 "배경음 제거 ↔ 원래대로" 같은 컨텍스트 액션용.
    // null 이면 행은 4버튼 그대로 (영상 패널은 사용 안 함).
    tertiaryActionLabel: String? = null,
    onTertiaryAction: (() -> Unit)? = null,
    tertiaryActionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    // 볼륨/속도 슬라이더는 기본 숨김 — 액션 버튼(볼륨/속도)을 누르면 해당 bar 만 펼침.
    // 한 번에 하나만 펼쳐지도록 enum 상태: null = 아무도 안 열림.
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.panelBg, VibiShape.lg)
            .padding(VibiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 헤더(닫기/제목)는 액션 버튼 행과 분리 — 4 액션 버튼이 행 폭을 가로질러 양 끝 flush 로
        // 분포되도록 SpaceBetween. 헤더가 같은 Row 에 있으면 weight(1f) 가 빈 공간을 흡수해 액션 4개가
        // 우측에 쏠림. 헤더가 없는 호출 (현행 영상/BGM 양쪽 모두) 에서는 헤더 Row 자체 미렌더.
        if (onCancel != null || title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xs),
            ) {
                if (onCancel != null) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(VibiSpacing.touchMin),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = tokens.onBackgroundPrimary,
                        )
                    }
                }
                if (title.isNotEmpty()) {
                    Text(
                        title,
                        style = typo.titleSm,
                        color = tokens.onBackgroundPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // 자연 폭 + SpaceBetween 으로 분배 — 아이콘 버튼은 컨텐트 (icon + padding) 만큼, 5번째
        // 텍스트 버튼은 라벨 길이만큼 차지하고, 남는 공간이 버튼 사이로 균등 분배. 양 끝 버튼은
        // 행 양쪽 가장자리에 flush. 좁은 화면이면 자동으로 gap 이 0 까지 줄어 라벨도 안 잘림.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 아이콘 버튼 4개 — 테두리 제거 (border = null). 폭은 ButtonDefaults.MinWidth 기본값에
            // 맡겨 자연스러운 터치 영역 보장 (Material3 기본 ~58dp). 활성 색 (펼침 시 accent) 은
            // 아이콘 tint 로 유지.
            OutlinedButton(
                onClick = { expanded = if (expanded == "volume") null else "volume" },
                contentPadding = PaddingValues(horizontal = VibiSpacing.xxs, vertical = 0.dp),
                modifier = Modifier.height(VibiSpacing.touchMin),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (expanded == "volume") tokens.accent else tokens.onBackgroundPrimary,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    modifier = Modifier.size(20.dp),
                )
            }
            OutlinedButton(
                onClick = { expanded = if (expanded == "speed") null else "speed" },
                contentPadding = PaddingValues(horizontal = VibiSpacing.xxs, vertical = 0.dp),
                modifier = Modifier.height(VibiSpacing.touchMin),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (expanded == "speed") tokens.accent else tokens.onBackgroundPrimary,
                ),
            ) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = "Speed",
                    modifier = Modifier.size(20.dp),
                )
            }
            OutlinedButton(
                onClick = onSecondaryAction,
                contentPadding = PaddingValues(horizontal = VibiSpacing.xxs, vertical = 0.dp),
                modifier = Modifier.height(VibiSpacing.touchMin),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
            ) {
                Icon(
                    secondaryActionIcon,
                    contentDescription = secondaryActionContentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = tokens.accent,
                )
            }
            OutlinedButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = VibiSpacing.xxs, vertical = 0.dp),
                modifier = Modifier.height(VibiSpacing.touchMin),
                border = null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(20.dp),
                    tint = tokens.accent,
                )
            }
            // 옵셔널 5번째 — 라벨/콜백 둘 다 있으면 표시. enabled=false 면 회색·터치 비활성 (예: 처리 중).
            if (tertiaryActionLabel != null && onTertiaryAction != null) {
                OutlinedButton(
                    onClick = onTertiaryAction,
                    enabled = tertiaryActionEnabled,
                    contentPadding = PaddingValues(horizontal = VibiSpacing.xxs, vertical = 0.dp),
                    modifier = Modifier.height(VibiSpacing.touchMin),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
                ) {
                    Text(
                        tertiaryActionLabel,
                        style = typo.bodySm,
                        color = if (tertiaryActionEnabled) tokens.accent else tokens.mutedText,
                        maxLines = 1,
                    )
                }
            }
        }

        // 볼륨 — 0..2 (0 = 무음, 1 = 그대로, 2 = 2배). Local state 로 슬라이더 위치 즉시 갱신 +
        // onVolumeChange 로 부모에 live commit. parent prop (volume) 이 바뀌면 (예: 적용 / Apply
        // 외부 경로) sliderVal 도 재seed.
        if (expanded == "volume") {
            var sliderVal by remember(expanded, volume) { mutableStateOf(volume) }
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Volume ${(sliderVal * 100).toInt()}%", style = typo.bodySm, color = tokens.mutedText)
                    TextButton(onClick = { onApplyVolume(sliderVal) }) { Text("Apply") }
                }
                Slider(
                    value = sliderVal,
                    valueRange = 0f..2f,
                    onValueChange = {
                        sliderVal = it
                        onVolumeChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = mutedSliderColors(tokens.mutedText),
                )
            }
        }

        // 속도 — 0.25..4. BGM 호출 측은 onSpeedChange 가 no-op (commit 비용 큼 — applyBgmRangeSpeed
        // 가 lane re-pack + 다른 BGM 들의 startMs 조정 동반). 그렇다고 onValueChange 를 그대로
        // no-op 으로 두면 controlled Slider 라 value prop 이 안 바뀌어 슬라이더가 시각적으로
        // 움직이지 않음 → 사용자 입장에서 "조절이 안됨". local state 로 시각만 즉시 갱신, parent
        // 의 onSpeedChange 는 그대로 호출해 (live preview 원하는 호출은 그쪽에서 처리), 실제
        // commit 은 "적용" 의 onApplySpeed(sliderVal) 가 담당.
        if (expanded == "speed") {
            var sliderVal by remember(expanded, speed) { mutableStateOf(speed) }
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val pct = (sliderVal * 100).toInt()
                    Text("Speed ${pct}%", style = typo.bodySm, color = tokens.mutedText)
                    TextButton(onClick = { onApplySpeed(sliderVal) }) { Text("Apply") }
                }
                Slider(
                    value = sliderVal,
                    valueRange = 0.25f..4f,
                    onValueChange = {
                        sliderVal = it
                        onSpeedChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = mutedSliderColors(tokens.mutedText),
                )
            }
        }
    }
}

/**
 * 음원분리 결과 / BGM 편집 슬라이더 색상.
 *
 * - thumb · activeTrack: M3 기본 (= colorScheme.primary = 우리 테마 잉크 near-black). 사용자가
 *   "현재 값" 을 즉각 인지할 수 있도록 유지.
 * - inactiveTrack: M3 기본은 surfaceContainerHighest (라벤더 0xFFE6E0E9, 우리 colorScheme
 *   미override) 라 stem 카드 위에서 "보라" 로 인지됨 → mutedText 옅은 회색으로 교체.
 *
 * [base] 는 inactiveTrack tint 의 기준색 (보통 tokens.mutedText).
 */
@Composable
internal fun mutedSliderColors(base: androidx.compose.ui.graphics.Color): SliderColors =
    SliderDefaults.colors(
        inactiveTrackColor = base.copy(alpha = 0.18f),
        inactiveTickColor = base.copy(alpha = 0.18f),
    )
