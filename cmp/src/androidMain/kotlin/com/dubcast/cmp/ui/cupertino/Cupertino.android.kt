package com.dubcast.cmp.ui.cupertino

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Android actual — Material3 그대로 위임 (현행 디자인 유지).
 * iOS 디자인 정착되면 Android 도 별도로 디자인 작업 예정.
 */

@Composable
actual fun PageScaffold(
    title: String,
    modifier: Modifier,
    step: Int,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepHero(step = step, title = title)
        content()
    }
}

@Composable
actual fun StepHero(step: Int, title: String, modifier: Modifier, compact: Boolean) {
    Column(modifier = modifier) {
        Text(text = "STEP $step", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
actual fun Section(
    header: String?,
    footer: String?,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(text = header, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
        }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
        if (footer != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = footer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
actual fun SectionRow(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val rowMod = if (onClick != null) modifier.padding(12.dp) else modifier.padding(12.dp)
    Row(modifier = rowMod.fillMaxWidth()) {
        content()
    }
}

@Composable
actual fun BodyText(text: String, modifier: Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

@Composable
actual fun SecondaryText(text: String, modifier: Modifier) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

@Composable
actual fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text)
    }
}

@Composable
actual fun TextButton(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean,
    modifier: Modifier
) {
    M3TextButton(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

@Composable
actual fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
}

@Composable
actual fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
    )
}

@Composable
actual fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(placeholder) },
        modifier = modifier
    )
}
