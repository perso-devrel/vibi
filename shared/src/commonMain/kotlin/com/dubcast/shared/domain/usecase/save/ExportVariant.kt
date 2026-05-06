package com.dubcast.shared.domain.usecase.save

/**
 * 사용자가 저장/공유 picker sheet 에서 선택할 한 변종의 메타데이터.
 *
 * `key` 는 [SaveAllVariantsUseCase] 가 동작할 때 쓰는 internal sentinel:
 *  - `"original"` — 원본 영상 (자막·더빙 X). 항상 존재.
 *  - `""` (empty string) — 원본 자막 burn-in (review 완료된 lang="" 클립).
 *  - 기타 — translation lang 코드 (예: `"ko"`, `"en"`).
 *
 * [displayLabel] 은 사용자에게 노출할 한국어 라벨.
 */
data class ExportVariant(
    val key: String,
    val kind: ExportVariantKind,
    val displayLabel: String,
) {
    companion object {
        /** [ExportVariantKind.ORIGINAL] 의 sentinel key — 원본 영상 (자막·더빙 X). */
        const val KEY_ORIGINAL: String = "original"

        /**
         * [ExportVariantKind.ORIGINAL_SUBTITLE] 의 sentinel key — lang="" 클립을 burn 한 원본 자막 영상.
         * SubtitleClip.languageCode 가 빈 문자열인 클립이 곧 "원본 자막" 이므로 동일하게 빈 문자열을 키로.
         */
        const val KEY_ORIGINAL_SUBTITLE: String = ""
    }
}

enum class ExportVariantKind {
    /** 원본 영상 — 자막·더빙 burn 안 함. */
    ORIGINAL,
    /** lang="" 클립을 burn — 원본 언어 자막 (review 완료). */
    ORIGINAL_SUBTITLE,
    /** translation lang 의 자막을 burn (해당 lang 의 더빙은 없음). */
    TRANSLATION_SUBTITLE,
    /** translation lang 의 더빙 mux + (있으면) 자막 burn. */
    TRANSLATION_DUB,
}
