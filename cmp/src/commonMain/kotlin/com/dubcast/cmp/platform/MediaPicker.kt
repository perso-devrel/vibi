package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable

/**
 * 갤러리에서 영상 1개를 선택하는 플랫폼 picker UI.
 *
 * Android: `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia)`
 * iOS: `PHPickerViewController` (UIKit 통합)
 *
 * 선택된 비디오의 URI/path 를 [onPicked] 로 전달.
 */
@Composable
expect fun MediaPicker(
    label: String,
    onPicked: (uri: String) -> Unit
)
