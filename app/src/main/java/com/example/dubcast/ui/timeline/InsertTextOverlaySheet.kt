package com.example.dubcast.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dubcast.domain.model.TextOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertTextOverlaySheet(
    pendingText: String,
    pendingFontFamily: String,
    pendingFontSizeSp: Float,
    pendingColorHex: String,
    error: String?,
    isEditing: Boolean,
    onTextChange: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onColorChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                if (isEditing) "Edit Text" else "Insert Text",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = pendingText,
                onValueChange = onTextChange,
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Font", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row {
                TextOverlay.SUPPORTED_FONT_FAMILIES.forEach { family ->
                    AssistChip(
                        onClick = { onFontFamilyChange(family) },
                        label = { Text(family.toFontLabel(), color = chipFontColor(family, pendingFontFamily)) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Size", modifier = Modifier.width(50.dp))
                Slider(
                    value = pendingFontSizeSp,
                    onValueChange = onFontSizeChange,
                    valueRange = TextOverlay.MIN_FONT_SIZE_SP..TextOverlay.MAX_FONT_SIZE_SP,
                    modifier = Modifier.weight(1f)
                )
                Text("${pendingFontSizeSp.toInt()}", modifier = Modifier.width(40.dp))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pendingColorHex,
                onValueChange = onColorChange,
                label = { Text("Color (#AARRGGBB or #RRGGBB)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onConfirm) { Text(if (isEditing) "Save" else "Add") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun String.toFontLabel(): String = when (this) {
    "noto_sans_kr" -> "Sans"
    "noto_serif_kr" -> "Serif"
    else -> this
}

@Composable
private fun chipFontColor(family: String, selected: String) =
    if (family == selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface
