package com.dubcast.shared.domain.model

/**
 * BFF `/api/v2/languages` 응답에서 도출된 지원 타깃 언어.
 *
 * [TargetLanguage] 와 다른 점: 여기는 **동적 목록** (Perso 가 추가/제거하면 자동 반영),
 * [TargetLanguage] 는 클라이언트 정적 enum 으로 fallback / UI 라벨 용도.
 */
data class SupportedLanguage(
    val code: String,
    val name: String,
    val nativeName: String? = null,
    val supportsDubbing: Boolean = true,
    val supportsSubtitles: Boolean = true,
) {
    val displayLabel: String
        get() = nativeName?.takeIf { it.isNotBlank() && it != name }?.let { "$name ($it)" } ?: name
}
