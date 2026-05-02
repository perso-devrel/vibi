package com.dubcast.shared.ui.timeline

import com.dubcast.shared.domain.model.Stem

enum class AudioSeparationStep {
    SETUP,
    PROCESSING,
    PICK_STEMS,
    DONE,
    FAILED
}

data class StemSelectionUi(
    val stemId: String,
    val selected: Boolean,
    val volume: Float
)

data class AudioSeparationUiState(
    val segmentId: String,
    val step: AudioSeparationStep = AudioSeparationStep.SETUP,
    val numberOfSpeakers: Int = 2,
    val jobId: String? = null,
    val progress: Int = 0,
    val progressReason: String? = null,
    val stems: List<Stem> = emptyList(),
    val selections: Map<String, StemSelectionUi> = emptyMap(),
    val muteOriginalSegmentAudio: Boolean = true,
    val errorMessage: String? = null,
    /** 사용자 지정 부분 구간 (range mode 진입). null = 영상 전체. */
    val rangeStartMs: Long? = null,
    val rangeEndMs: Long? = null,
) {
    val canStart: Boolean
        get() = step == AudioSeparationStep.SETUP && numberOfSpeakers in 1..10

    val canMix: Boolean
        get() = step == AudioSeparationStep.PICK_STEMS &&
            selections.values.any { it.selected }
}

/**
 * BFF progressReason values come in English (Enqueue Pending / Transcribing / ...).
 * Map them to Korean labels for the progress UI, falling back to the raw string
 * when the BFF adds a reason we haven't seen yet.
 */
fun localizeProgressReason(reason: String?): String = when (reason) {
    null, "" -> "준비 중"
    "Enqueue Pending" -> "대기열 대기 중"
    "Slow Mode Pending" -> "느린 모드 대기 중"
    "Uploading" -> "업로드 중"
    "Transcribing" -> "음성 전사 중"
    "Translating" -> "번역 중"
    "Generating Voice" -> "음원 분리 중"
    "Completed" -> "완료"
    "Failed" -> "실패"
    else -> reason
}

/**
 * Korean display label for a stem. `background` includes reactions/effects
 * (Perso does not expose a "pure background" variant), so we flag that in
 * the label. Per-speaker indices are 0-based on the wire; show them as 1-based
 * to match the user's mental model.
 */
fun stemDisplayLabel(stem: Stem): String = when {
    stem.stemId == "background" -> "배경음"
    stem.stemId == "voice_all" -> "모든 화자"
    stem.speakerIndex != null -> "화자 ${stem.speakerIndex + 1}"
    else -> stem.label.ifBlank { stem.stemId }
}
