package com.vibi.cmp.ui.timeline.sounddeck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import com.vibi.cmp.theme.VibiSpacing
import com.vibi.cmp.ui.components.VibiPanelCard

/**
 * SoundDeck 의 진입 카드 — leading 시각 단서 + 라벨 + 부연 설명 + 탭 액션 한 묶음.
 * "+ 음원 분리" 같은 placeholder 와 "영상 다듬기" 같은 명시 액션을 같은 형태로 노출해
 * 사용자가 화면 어디서든 일관된 카드 metaphor 로 다음 행동을 인지하게 함.
 *
 * DESIGN.md `panel-card` 베이스 — [VibiPanelCard].
 */
@Composable
fun IconLabelCard(
    label: String,
    description: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    VibiPanelCard(
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(modifier = Modifier.width(VibiSpacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VibiSpacing.xxs),
            ) {
                Text(
                    label,
                    style = typo.bodyStrong,
                    color = tokens.onBackgroundPrimary,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = typo.bodySm,
                        color = tokens.mutedText,
                    )
                }
            }
        }
    }
}
