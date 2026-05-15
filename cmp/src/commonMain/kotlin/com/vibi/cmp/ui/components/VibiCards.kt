package com.vibi.cmp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.VibiShape
import com.vibi.cmp.theme.VibiSpacing

/**
 * DESIGN.md `panel-card` — `canvas-soft` bg, `hairline` 1dp border, `rounded.lg` (14), `padding 16`.
 *
 * 카드 안의 sub-panel — 더 약한 계층. 화면 캔버스(`backgroundPrimary`) 위에 살짝 떠 있는 느낌.
 * SoundDeck 의 IconLabelCard / SoundCard 처럼 deck 안에 여러 장 쌓이는 카드는 이걸 사용.
 */
@Composable
fun VibiPanelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tokens = LocalVibiColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.panelBgSoft, VibiShape.lg)
            .border(width = 1.dp, color = tokens.hairline, shape = VibiShape.lg)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled) { onClick() } else Modifier)
            .padding(VibiSpacing.base),
    ) {
        content()
    }
}
