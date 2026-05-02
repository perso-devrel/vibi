package com.dubcast.cmp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 화면 전반에서 쓰는 색 토큰. Material3 colorScheme 으로 표현 못 하는 (semi-transparent chip bg,
 * 자막 overlay bg 등) 앱 고유 색상을 한 곳에 모음.
 *
 * 기본값은 다크 (현 디자인 그대로). [isSystemInDarkTheme] false 면 light 변종 사용.
 */
data class DubCastColors(
    val backgroundPrimary: Color,
    val onBackgroundPrimary: Color,
    val chipBg: Color,
    val chipBgDisabled: Color,
    val chipContentDisabled: Color,
    val panelBg: Color,
    val subtitleOverlayBg: Color,
    val mutedText: Color,
    val accent: Color,
    /** 통합 타임라인 바 — 가운데 얇은 배경 strip (segment/directive 가 그 위에 그려짐). */
    val timelineBarTrack: Color,
    /** 통합 타임라인 바 — 기본 segment / directive 블록 색. 중성 회색 톤. */
    val timelineBarSegment: Color,
    /** 통합 타임라인 바 — 편집 적용된 segment 색. 기본보다 한 단계 밝은 회색. */
    val timelineBarSegmentEdited: Color,
)

val DarkDubCastColors = DubCastColors(
    backgroundPrimary = Color.Black,
    onBackgroundPrimary = Color.White,
    chipBg = Color(0x33FFFFFF),
    chipBgDisabled = Color(0x14FFFFFF),
    chipContentDisabled = Color(0x66FFFFFF),
    panelBg = Color(0xFF1C1C1E),
    subtitleOverlayBg = Color(0xCC000000),
    mutedText = Color(0x99EBEBF5),
    accent = Color(0xFF0A84FF),
    timelineBarTrack = Color(0xFF1F1F22),
    timelineBarSegment = Color(0xFF3A3A3C),
    timelineBarSegmentEdited = Color(0xFF6B6B6E),
)

val LightDubCastColors = DubCastColors(
    backgroundPrimary = Color.White,
    onBackgroundPrimary = Color.Black,
    chipBg = Color(0x14000000),
    chipBgDisabled = Color(0x08000000),
    chipContentDisabled = Color(0x66000000),
    panelBg = Color(0xFFF2F2F7),
    subtitleOverlayBg = Color(0xCCFFFFFF),
    mutedText = Color(0x993C3C43),
    accent = Color(0xFF007AFF),
    timelineBarTrack = Color(0xFFE5E5EA),
    timelineBarSegment = Color(0xFFC7C7CC),
    timelineBarSegmentEdited = Color(0xFF8E8E93),
)

val LocalDubCastColors = staticCompositionLocalOf { DarkDubCastColors }

@Composable
fun DubCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkDubCastColors else LightDubCastColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = tokens.backgroundPrimary,
            surface = tokens.panelBg,
            primary = tokens.onBackgroundPrimary,
            onPrimary = tokens.backgroundPrimary,
            onBackground = tokens.onBackgroundPrimary,
            onSurface = tokens.onBackgroundPrimary
        )
    } else {
        lightColorScheme(
            background = tokens.backgroundPrimary,
            surface = tokens.panelBg,
            primary = tokens.accent,
            onPrimary = Color.White,
            onBackground = tokens.onBackgroundPrimary,
            onSurface = tokens.onBackgroundPrimary
        )
    }
    CompositionLocalProvider(LocalDubCastColors provides tokens) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
