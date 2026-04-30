package com.dubcast.shared.domain.usecase.export

import com.dubcast.shared.domain.model.Anchor
import com.dubcast.shared.domain.model.SubtitleClip
import com.dubcast.shared.domain.model.TextOverlay

class AssGenerator constructor() {

    fun generateFromClips(
        clips: List<SubtitleClip>,
        videoWidth: Int,
        videoHeight: Int,
        textOverlays: List<TextOverlay> = emptyList()
    ): String {
        val sb = StringBuilder()
        appendHeader(sb, videoWidth, videoHeight)

        sb.appendLine("[Events]")
        sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

        for (clip in clips) {
            val start = formatTimestamp(clip.startMs)
            val end = formatTimestamp(clip.endMs)
            val text = clip.text.replace("\n", "\\N")
            // 사용자 스타일: 폰트/크기/색 ASS 태그로 변환. 박스 배경(backgroundColorHex) 은 ASS 직접
            // 표현 어려움 — in-app preview 만 적용, burn-in 은 텍스트 스타일까지.
            val fontName = mapFontFamilyToAssName(clip.fontFamily)
            val fontSizeOverride = ((clip.fontSizeSp / 1080f) * videoHeight).toInt().coerceAtLeast(8)
            val primaryColor = hexToAssColor(clip.colorHex)
            val styleTags = "\\fn$fontName\\fs$fontSizeOverride\\c$primaryColor"
            if (clip.isSticker &&
                clip.xPct != null && clip.yPct != null && clip.heightPct != null
            ) {
                // Auto/sticker subtitle — center-anchored absolute position with scaled font
                val posX = (clip.xPct!! / 100f * videoWidth).toInt()
                val posY = (clip.yPct!! / 100f * videoHeight).toInt()
                // sticker 는 heightPct 기반 자동 폰트 크기를 우선시 (사용자 스타일 fontSizeSp 가 0/기본이면).
                val stickerFontSize = (clip.heightPct!! / 100f * videoHeight).toInt().coerceAtLeast(12)
                sb.appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,{\\an5\\pos($posX,$posY)\\fs$stickerFontSize\\fn$fontName\\c$primaryColor}$text")
            } else {
                // Manual subtitle — anchor-based position
                val alignment = when (clip.position.anchor) {
                    Anchor.TOP -> "\\an8"
                    Anchor.MIDDLE -> "\\an5"
                    Anchor.BOTTOM -> "\\an2"
                }
                val posX = videoWidth / 2
                val posY = (clip.position.yOffsetPct / 100f * videoHeight).toInt()
                sb.appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,{$alignment\\pos($posX,$posY)$styleTags}$text")
            }
        }

        for (overlay in textOverlays) {
            val start = formatTimestamp(overlay.startMs)
            val end = formatTimestamp(overlay.endMs)
            val text = overlay.text.replace("\n", "\\N")
            val posX = (overlay.xPct / 100f * videoWidth).toInt()
            val posY = (overlay.yPct / 100f * videoHeight).toInt()
            // Map sp → ASS pixel size relative to PlayResY (a sp ≈ a px on the
            // baseline 1080-tall reference height, so scale linearly).
            val fontSize = ((overlay.fontSizeSp / 1080f) * videoHeight).toInt().coerceAtLeast(8)
            val fontName = mapFontFamilyToAssName(overlay.fontFamily)
            val primaryColor = hexToAssColor(overlay.colorHex)
            sb.appendLine(
                "Dialogue: 0,$start,$end,Default,,0,0,0,," +
                    "{\\an5\\pos($posX,$posY)\\fs$fontSize\\fn$fontName\\c$primaryColor}$text"
            )
        }

        return sb.toString()
    }

    private fun appendHeader(sb: StringBuilder, videoWidth: Int, videoHeight: Int) {
        sb.appendLine("[Script Info]")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("PlayResX: $videoWidth")
        sb.appendLine("PlayResY: $videoHeight")
        sb.appendLine("WrapStyle: 0")
        sb.appendLine()

        sb.appendLine("[V4+ Styles]")
        sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        sb.appendLine("Style: Default,Noto Sans KR,48,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2,1,2,20,20,20,1")
        sb.appendLine()
    }

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val centiseconds = (ms % 1000) / 10
        return "$hours:${minutes.pad2()}:${seconds.pad2()}.${centiseconds.pad2()}"
    }

    private fun Long.pad2(): String = this.toString().padStart(2, '0')

    private fun mapFontFamilyToAssName(family: String): String = when (family) {
        "noto_sans_kr", "Noto Sans KR" -> "Noto Sans KR"
        "noto_serif_kr", "Noto Serif KR" -> "Noto Serif KR"
        else -> family.ifBlank { "Noto Sans KR" }
    }

    /**
     * ASS uses BGR ordering with optional leading alpha: `&HAABBGGRR`.
     * Accepts `#RRGGBB` or `#AARRGGBB` and returns the ASS-encoded string
     * including the `&H...` prefix and trailing `&` that ffmpeg expects.
     */
    private fun hexToAssColor(hex: String): String {
        val cleaned = hex.removePrefix("#")
        val (a, r, g, b) = when (cleaned.length) {
            6 -> Quad("00", cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6))
            8 -> Quad(cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6), cleaned.substring(6, 8))
            else -> Quad("00", "FF", "FF", "FF")
        }
        return "&H${a.uppercase()}${b.uppercase()}${g.uppercase()}${r.uppercase()}&"
    }

    private data class Quad(val a: String, val r: String, val g: String, val b: String)
}
