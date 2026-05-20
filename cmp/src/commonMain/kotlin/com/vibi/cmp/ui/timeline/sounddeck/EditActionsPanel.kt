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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing

/**
 * 다듬기(볼륨/속도/secondary/삭제) 액션 패널 — 영상 다듬기 모드(전역) 와 BGM 카드 in-card expansion
 * 양쪽에서 동일 레이아웃으로 재사용. 볼륨/속도는 토글, secondary/삭제는 즉시 액션.
 *
 * - [title] 빈 문자열이면 헤더의 제목 영역 생략.
 * - [secondaryActionLabel] / [onSecondaryAction] — 영상은 "복제", BGM 은 "배경음 제거" 등 컨텍스트별
 *   세 번째 액션을 자유 라벨링. 색·outlined 스타일은 동일.
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
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit,
    onDelete: () -> Unit,
    onCancel: (() -> Unit)? = null,
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
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "닫기",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = { expanded = if (expanded == "volume") null else "volume" },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (expanded == "volume") tokens.accent else tokens.onBackgroundPrimary,
                ),
            ) { Text("볼륨", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { expanded = if (expanded == "speed") null else "speed" },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (expanded == "speed") tokens.accent else tokens.onBackgroundPrimary,
                ),
            ) { Text("속도", fontSize = 12.sp) }
            OutlinedButton(
                onClick = onSecondaryAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
            ) { Text(secondaryActionLabel, fontSize = 12.sp, color = tokens.accent) }
            OutlinedButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
            ) { Text("삭제", fontSize = 12.sp, color = tokens.accent) }
        }

        // 볼륨 — 0..2 (0 = 무음, 1 = 그대로, 2 = 2배).
        if (expanded == "volume") {
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("볼륨 ${(volume * 100).toInt()}%", style = typo.bodySm, color = tokens.mutedText)
                    TextButton(onClick = { onApplyVolume(volume) }) { Text("적용") }
                }
                Slider(
                    value = volume,
                    valueRange = 0f..2f,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = mutedSliderColors(tokens.mutedText),
                )
            }
        }

        // 속도 — 0.25..4.
        if (expanded == "speed") {
            Column(verticalArrangement = Arrangement.spacedBy(VibiSpacing.xxs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val pct = (speed * 100).toInt()
                    Text("속도 ${pct}%", style = typo.bodySm, color = tokens.mutedText)
                    TextButton(onClick = { onApplySpeed(speed) }) { Text("적용") }
                }
                Slider(
                    value = speed,
                    valueRange = 0.25f..4f,
                    onValueChange = onSpeedChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = mutedSliderColors(tokens.mutedText),
                )
            }
        }
    }
}

/**
 * 음원분리 결과 / BGM 편집 슬라이더 전용 muted 색상. M3 Slider 기본은 thumb·activeTrack=primary(우리
 * 테마에선 잉크 near-black) + inactiveTrack=surfaceContainerHighest(M3 미override 기본 라벤더
 * 0xFFE6E0E9). 두 색이 분리 stem 카드 (화자/배경 팔레트) 위에서 액티브 컬러로 인식돼 시각 과부하 +
 * 사용자가 "보라" 로 인지. mutedText 한 톤으로 묶고 alpha 를 단계화해 active/inactive 만 구분.
 */
@Composable
internal fun mutedSliderColors(base: androidx.compose.ui.graphics.Color): SliderColors =
    SliderDefaults.colors(
        thumbColor = base,
        activeTrackColor = base.copy(alpha = 0.40f),
        activeTickColor = base.copy(alpha = 0.40f),
        inactiveTrackColor = base.copy(alpha = 0.18f),
        inactiveTickColor = base.copy(alpha = 0.18f),
    )
