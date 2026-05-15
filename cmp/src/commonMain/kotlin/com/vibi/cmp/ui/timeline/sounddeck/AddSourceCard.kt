package com.vibi.cmp.ui.timeline.sounddeck

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography

@Composable
fun AddSourceCard(
    label: String,
    description: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    IconLabelCard(
        label = label,
        description = description,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        Text("+", color = tokens.accent, style = typo.displaySm)
    }
}
