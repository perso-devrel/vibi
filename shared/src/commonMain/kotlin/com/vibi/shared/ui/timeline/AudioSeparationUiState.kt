package com.vibi.shared.ui.timeline

import com.vibi.shared.domain.model.Stem

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
    /**
     * 사용자가 진행 중 sheet 를 명시적으로 닫았는지 — 이후 FAILED 가 도착해도 sheet 자동
     * 재오픈 안 함. directive 막대만으로 알림. 새 분리 시작(onShowAudioSeparationSheet 통한
     * 재진입) 시 자연스럽게 false 로 리셋됨.
     */
    val userDismissed: Boolean = false,
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
    null, "" -> "Preparing"
    "Enqueue Pending" -> "Queued"
    "Slow Mode Pending" -> "Queued (slow mode)"
    "Uploading" -> "Uploading"
    "Transcribing" -> "Transcribing"
    "Translating" -> "Translating"
    "Generating Voice" -> "Separating audio"
    "Completed" -> "Done"
    "Failed" -> "Failed"
    else -> reason
}

/**
 * Korean display label for a stem. `background` includes reactions/effects
 * (Perso does not expose a "pure background" variant), so we flag that in
 * the label. Per-speaker indices are 0-based on the wire; show them as 1-based
 * to match the user's mental model.
 */
fun stemDisplayLabel(stem: Stem): String = when {
    stem.stemId == "background" -> "Background"
    stem.stemId == "voice_all" -> "All speakers"
    stem.speakerIndex != null -> "Speaker ${stem.speakerIndex + 1}"
    else -> stem.label.ifBlank { stem.stemId }
}

/**
 * [stemDisplayLabel] 의 stemId-only 변종 — directive 의 [com.vibi.shared.domain.repository.StemSelection]
 * 처럼 Stem 객체가 없는 경로에서 사용. label 폴백이 stemId 본인.
 */
fun stemDisplayLabelFromId(stemId: String): String = when {
    stemId == Stem.STEM_ID_BACKGROUND -> "Background"
    stemId == Stem.STEM_ID_VOICE_ALL -> "All speakers"
    else -> Stem.speakerIndexFromId(stemId)?.let { "Speaker ${it + 1}" } ?: stemId
}
