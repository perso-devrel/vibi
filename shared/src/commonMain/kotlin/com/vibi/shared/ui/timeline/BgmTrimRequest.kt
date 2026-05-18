package com.vibi.shared.ui.timeline

/**
 * 음원 선택 후 길이가 영상보다 길어 trim 입력을 받기 위한 pending 상태.
 *
 * `onPickBgmAudio` 에서 `info.durationMs > state.videoDurationMs` 일 때 생성되고
 * BgmTrimSheet UI 에 전달된다. 사용자가 시트에서 핸들 드래그로 `rangeStartMs/EndMs`
 * 를 갱신하다가 confirm 시 `AddBgmClipUseCase` 의 `sourceTrimStartMs/EndMs` 로 적용,
 * cancel 시 그대로 폐기 (BGM 미삽입).
 *
 * @param insertStartMs 사용자가 시트를 띄운 시점의 playhead — 적용 시 BgmClip.startMs.
 */
data class BgmTrimRequest(
    val sourceUri: String,
    val sourceDurationMs: Long,
    val insertStartMs: Long,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
)
