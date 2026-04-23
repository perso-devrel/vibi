package com.example.dubcast.domain.model

data class TargetLanguage(val code: String, val label: String) {
    companion object {
        const val CODE_ORIGINAL = "original"

        val ORIGINAL = TargetLanguage(CODE_ORIGINAL, "원본 그대로")

        val AVAILABLE: List<TargetLanguage> = listOf(
            ORIGINAL,
            TargetLanguage("ko", "한국어"),
            TargetLanguage("en", "English"),
            TargetLanguage("ja", "日本語"),
            TargetLanguage("zh", "中文"),
            TargetLanguage("es", "Español"),
            TargetLanguage("fr", "Français"),
            TargetLanguage("de", "Deutsch")
        )

        fun fromCode(code: String): TargetLanguage =
            AVAILABLE.firstOrNull { it.code == code } ?: ORIGINAL
    }
}
