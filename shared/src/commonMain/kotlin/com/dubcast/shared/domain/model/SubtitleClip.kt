package com.dubcast.shared.domain.model

data class SubtitleClip(
    val id: String,
    val projectId: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val position: SubtitlePosition,
    val sourceDubClipId: String? = null,
    val xPct: Float? = null,
    val yPct: Float? = null,
    val widthPct: Float? = null,
    val heightPct: Float? = null,
    val source: SubtitleSource = SubtitleSource.MANUAL,
    /** 언어 코드 ("" = 원본/언어 미지정, "en"/"ko"/... = 특정 언어용 자막). 미리보기 swap 키. */
    val languageCode: String = "",
    /** 폰트 패밀리 — TextOverlay 와 동일 어휘 (Noto Sans KR / System / Serif 등). */
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    /** 폰트 크기 sp 단위. ASS burn-in 시 px 로 환산. */
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    /** ARGB hex (#AARRGGBB). 텍스트 색. */
    val colorHex: String = DEFAULT_COLOR_HEX,
    /** ARGB hex (#AARRGGBB). in-app preview 박스 배경색. ASS burn-in 은 미반영. */
    val backgroundColorHex: String = DEFAULT_BACKGROUND_COLOR_HEX,
) {
    val isAuto: Boolean get() = sourceDubClipId != null
    val isSticker: Boolean get() = xPct != null

    companion object {
        const val DEFAULT_FONT_FAMILY = "Noto Sans KR"
        const val DEFAULT_FONT_SIZE_SP = 16f
        const val DEFAULT_COLOR_HEX = "#FFFFFFFF"
        const val DEFAULT_BACKGROUND_COLOR_HEX = "#80000000"
    }
}

enum class SubtitleSource { MANUAL, AUTO }
