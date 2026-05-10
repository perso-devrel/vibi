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
    /**
     * 통합 타임라인 바 — 편집 적용된 segment 색. accent (selection 표시) 와도, 기본 segment 색과도
     * 명확히 구별되는 warm tint. volumeScale != 1 / speedScale != 1 / trim / duplicatedFromId 중
     * 하나라도 있으면 사용.
     */
    val timelineBarSegmentEdited: Color,
    /**
     * 통합 타임라인 바 — 음성분리 directive 블록 색. 사용자 가이드: "edited segment 와 명확히 구별,
     * 짙은 회색". directive 는 사용자가 분리 적용한 구간이지 segment 자체 편집과 다름.
     */
    val timelineBarDirective: Color,
)

// Linear palette — VoltAgent/awesome-design-md/design-md/linear.app/DESIGN.md 기준.
// 핵심: near-black canvas (#010102) + 단일 chromatic accent (#5e6ad2 라벤더-블루) +
// charcoal panels (#0f1011) + hairline borders. 강조는 brand mark / focus / 의도된 CTA 한정.
val DarkDubCastColors = DubCastColors(
    backgroundPrimary = Color(0xFF010102),  // canvas
    onBackgroundPrimary = Color(0xFFF7F8F8),  // ink
    chipBg = Color(0xFF18191A),  // surface-3 — opaque chip on canvas
    chipBgDisabled = Color(0xFF141516),  // surface-2
    chipContentDisabled = Color(0xFF62666D),  // ink-tertiary
    panelBg = Color(0xFF0F1011),  // surface-1 (charcoal panel)
    subtitleOverlayBg = Color(0xCC010102),  // canvas with alpha — overlay tint
    mutedText = Color(0xFF8A8F98),  // ink-subtle
    accent = Color(0xFF5E6AD2),  // primary lavender-blue
    timelineBarTrack = Color(0xFF0F1011),  // surface-1 — 얇은 트랙
    timelineBarSegment = Color(0xFF23252A),  // hairline — 중성, accent 와 분리
    timelineBarSegmentEdited = Color(0xFF828FFF),  // primary-hover (밝은 라벤더) — Linear 팔레트 내부에서 edited 강조
    timelineBarDirective = Color(0xFFE5C275),  // warm amber — segment(중성)/edited(라벤더)와 hue 분리
)

// Light variant — Linear 의 inverse-* 토큰 기반. Linear 자체는 거의 다크 전용이지만
// system light 시 일관성 유지.
val LightDubCastColors = DubCastColors(
    backgroundPrimary = Color(0xFFFFFFFF),  // inverse-canvas
    onBackgroundPrimary = Color(0xFF000000),  // inverse-ink
    chipBg = Color(0xFFF5F6F6),  // inverse-surface-1
    chipBgDisabled = Color(0xFFF6F7F7),  // inverse-surface-2
    chipContentDisabled = Color(0x66000000),
    panelBg = Color(0xFFF5F6F6),  // inverse-surface-1
    subtitleOverlayBg = Color(0xCCFFFFFF),
    mutedText = Color(0xFF62666D),  // ink-tertiary 톤
    accent = Color(0xFF5E6AD2),  // primary lavender (light/dark 공통 brand)
    timelineBarTrack = Color(0xFFF5F6F6),
    timelineBarSegment = Color(0xFFD0D6E0),  // ink-muted (light bg 위 중성 회색)
    timelineBarSegmentEdited = Color(0xFF5E6AD2),  // primary 그대로 — light bg 에서 라벤더가 충분히 띔
    timelineBarDirective = Color(0xFFB8861E),  // deep amber — light bg 위 충분한 contrast
)

val LocalDubCastColors = staticCompositionLocalOf { DarkDubCastColors }

@Composable
fun DubCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkDubCastColors else LightDubCastColors
    // Linear 패턴: primary 는 항상 라벤더 accent. 이전엔 dark 에서 primary=white 였는데
    // 라벤더 강조 일관성을 위해 dark/light 모두 accent 로 통일. on-primary 는 Linear 의 #ffffff.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = tokens.backgroundPrimary,
            surface = tokens.panelBg,
            primary = tokens.accent,
            onPrimary = Color.White,
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
