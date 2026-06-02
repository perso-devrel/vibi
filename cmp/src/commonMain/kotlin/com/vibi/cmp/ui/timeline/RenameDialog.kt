package com.vibi.cmp.ui.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography

/** 이름 입력 길이 상한 — ViewModel 의 MAX_DISPLAY_NAME_LEN 과 동일. UI 단 1차 차단. */
private const val RENAME_MAX_LEN = 80

/**
 * 표시 이름(프로젝트 제목 / 음원·녹음 이름) 편집 다이얼로그. 순수 표시용 — 확정 시 [onConfirm] 으로
 * 새 문자열을 넘기기만 하며, 저장/렌더 동작에는 관여하지 않는다. 비워서 확정하면 빈 문자열을 넘겨
 * 호출부가 자동 라벨(파일명/타임스탬프)로 되돌리도록 한다.
 *
 * 앱 디자인 유지: 기존 [AlertDialog] 패턴(예: BGM 비용 confirm) + 토큰 색/타이포 그대로 사용.
 */
@Composable
fun RenameDialog(
    title: String,
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
) {
    val tokens = LocalVibiColors.current
    val typo = LocalVibiTypography.current
    var text by remember { mutableStateOf(currentName) }

    fun commit() = onConfirm(text.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.panelBg,
        titleContentColor = tokens.onBackgroundPrimary,
        title = { Text(title, style = typo.titleSm) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= RENAME_MAX_LEN) text = it },
                    singleLine = true,
                    placeholder = if (placeholder.isNotBlank()) {
                        { Text(placeholder, style = typo.bodySm, color = tokens.mutedText) }
                    } else null,
                    // 우측 끝 X — 입력된 이름 한번에 비우기. 텍스트 있을 때만 노출.
                    trailingIcon = if (text.isNotEmpty()) {
                        {
                            IconButton(onClick = { text = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    tint = tokens.mutedText,
                                )
                            }
                        }
                    } else null,
                    textStyle = typo.bodyStrong,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = tokens.onBackgroundPrimary,
                        unfocusedTextColor = tokens.onBackgroundPrimary,
                        cursorColor = tokens.accent,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.chipBg,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { commit() }) {
                Text("Save", color = tokens.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.mutedText)
            }
        },
    )
}
