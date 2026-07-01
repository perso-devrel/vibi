package com.vibi.cmp.platform

import androidx.compose.runtime.Composable

/**
 * 갤러리에서 영상 1개를 선택하는 플랫폼 picker 를 여는 launcher.
 *
 * Android: `ActivityResultContracts.PickVisualMedia`  ·  iOS: `PHPickerViewController` (UIKit 통합).
 * 반환된 lambda 를 호출하면 picker 를 표시하고, 선택된 비디오의 영속 file 경로를 [onPicked] 로 전달한다.
 * 화면 버튼/카드/이미지는 호출 측이 자체 디자인으로 구성해 onClick 에 바인딩한다.
 */
@Composable
expect fun rememberMediaPickerLauncher(
    onPicked: (uri: String) -> Unit
): () -> Unit
