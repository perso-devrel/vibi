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

/**
 * MediaPicker 의 trigger 만 분리한 launcher — UI 는 호출 측이 자체 구성.
 *
 * 반환된 lambda 호출 시 picker 표시. [MediaPicker] 와 동일한 selection 처리, 다만
 * 호출 측이 자체 디자인의 버튼/카드/이미지에 onClick 으로 바인딩.
 */
@Composable
expect fun rememberMediaPickerLauncher(
    onPicked: (uri: String) -> Unit
): () -> Unit
