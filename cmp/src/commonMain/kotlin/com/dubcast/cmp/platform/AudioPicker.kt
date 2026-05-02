package com.dubcast.cmp.platform

import androidx.compose.runtime.Composable

/**
 * 오디오 파일 1개를 디바이스 storage 에서 선택하는 플랫폼 picker.
 *
 * Android: ActivityResultContracts.GetContent (audio mime wildcard) 또는 OpenDocument 로 mp3/m4a/wav 선택.
 * iOS: UIDocumentPickerViewController (kUTTypeAudio) — 파일 앱 / iCloud / 다른 앱 공유 폴더 접근.
 *
 * 콜백은 영구 저장된 절대 경로(또는 file 스키마 URI) — picker 가 임시 URL 반환하면 호출자가 복사하도록.
 *
 * UI 구현체는 [trigger] 람다를 받아 사용자 액션(버튼 클릭)에 연결한다 — 자체 UI 없음, host 가
 * 자유롭게 버튼/메뉴 항목 등으로 노출.
 */
@Composable
expect fun rememberAudioPicker(
    onPicked: (uri: String) -> Unit,
): AudioPickerLauncher

interface AudioPickerLauncher {
    /** 시스템 audio picker UI 띄움. */
    fun launch()
}
