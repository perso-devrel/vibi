package com.vibi.cmp.ui.timeline.sounddeck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.shared.ui.timeline.PreviewMode

/**
 * 하단 고정 A/B 미리듣기 바 — "원본" / "내 믹스" 두 segment 토글.
 *
 * "결과 예측 어려움" 페르소나 고통의 직접 해결책. directive 유무와 무관하게 항상 노출 — 사용자가
 * 분리 전에도 토글 affordance 를 인지하도록. 토글은 mixer 의 stem 볼륨 + video segment volume
 * 둘 다 영향 — 상위 TimelineScreen 의 stemSyncKey LaunchedEffect 가 previewMode 를 보고 일괄 처리.
 *
 * pill geometry — DESIGN.md 의 chip 패턴 차용 (rounded.pill, surface-card bg, hairline border).
 */
@Composable
fun ABPreviewBar(
    mode: PreviewMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVibiColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VibiSpacing.sm, vertical = VibiSpacing.xxs)
            .clip(VibiShape.pill)
            .background(tokens.panelBg)
            .border(width = 1.dp, color = tokens.hairline, shape = VibiShape.pill)
            .padding(VibiSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VibiSpacing.xxs),
    ) {
        SegmentChip(
            label = "Original",
            selected = mode == PreviewMode.ORIGINAL,
            onClick = { if (mode != PreviewMode.ORIGINAL) onToggle() },
            modifier = Modifier.weight(1f),
        )
        SegmentChip(
            label = "My mix",
            selected = mode == PreviewMode.MIX,
            onClick = { if (mode != PreviewMode.MIX) onToggle() },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    Box(
        modifier = modifier
            .height(VibiSpacing.xxl)
            .clip(VibiShape.pill)
            .background(if (selected) tokens.accent else tokens.panelBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) tokens.backgroundPrimary else tokens.onBackgroundPrimary,
            style = typo.bodySm,
        )
    }
}
