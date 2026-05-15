package com.vibi.cmp.theme

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
 * 자막 overlay bg, atmosphere gradient orb 등) 앱 고유 색상을 한 곳에 모음.
 *
 * 팔레트는 ElevenLabs editorial — off-white canvas + warm near-black ink + 파스텔 atmospheric
 * gradient orb 5종. 자세한 토큰 정의는 `cmp/DESIGN.md` 참조. 13개 코어 필드는 backward-compat
 * 위해 이름 보존 (LocalVibiColors 호출처 영향 없음).
 */
data class VibiColors(
    val backgroundPrimary: Color,
    val onBackgroundPrimary: Color,
    val chipBg: Color,
    val chipBgDisabled: Color,
    val chipContentDisabled: Color,
    val panelBg: Color,
    /** DESIGN.md `canvas-soft` — panel-card 바탕. backgroundPrimary(canvas) 보다 살짝 밝음. */
    val panelBgSoft: Color,
    val subtitleOverlayBg: Color,
    val mutedText: Color,
    val accent: Color,
    /** 통합 타임라인 바 — 가운데 얇은 배경 strip. */
    val timelineBarTrack: Color,
    /** 통합 타임라인 바 — 기본 segment / directive 블록. 중성 회색. */
    val timelineBarSegment: Color,
    /**
     * 통합 타임라인 바 — 편집 적용된 segment. atmosphere lavender 차용 — accent (ink) 와도
     * 기본 segment (gray) 와도 명확히 구별. volumeScale != 1 / speedScale != 1 / trim /
     * duplicatedFromId 중 하나라도 있으면 사용.
     */
    val timelineBarSegmentEdited: Color,
    /**
     * 통합 타임라인 바 — 음성분리 directive 블록. atmosphere peach — edited (lavender) 와
     * hue 분리. 사용자가 분리 적용한 구간 표시.
     */
    val timelineBarDirective: Color,
    /** 1px hairline 디바이더. ElevenLabs 의 카드 outline 패턴. */
    val hairline: Color,
    /** Atmospheric gradient orb — mint. hero 배경 / gradient-orb-card 한정. */
    val gradientMint: Color,
    /** Atmospheric gradient orb — peach. */
    val gradientPeach: Color,
    /** Atmospheric gradient orb — lavender. */
    val gradientLavender: Color,
    /** Atmospheric gradient orb — sky. */
    val gradientSky: Color,
    /** Atmospheric gradient orb — rose. */
    val gradientRose: Color,
)

// Light palette — ElevenLabs editorial. Off-white canvas (#f5f5f5) + warm near-black ink (#0c0a09).
// CTA = ink pill (#292524). 채도 있는 액션 컬러 없음. atmosphere orb 5종이 유일한 컬러 모먼트.
val LightVibiColors = VibiColors(
    backgroundPrimary = Color(0xFFF5F5F5),  // canvas — off-white
    onBackgroundPrimary = Color(0xFF0C0A09),  // ink — warm near-black
    chipBg = Color(0xFFF0EFED),  // surface-strong
    chipBgDisabled = Color(0xFFFAFAFA),  // canvas-soft
    chipContentDisabled = Color(0xFFA8A29E),  // muted-soft
    panelBg = Color(0xFFFFFFFF),  // surface-card — pure white card on off-white canvas
    panelBgSoft = Color(0xFFFAFAFA),  // canvas-soft — panel-card 바탕
    subtitleOverlayBg = Color(0xCC0C0A09),  // ink with 80% alpha — overlay on video
    mutedText = Color(0xFF777169),  // muted
    accent = Color(0xFF292524),  // primary — ink pill (유일한 CTA 색)
    timelineBarTrack = Color(0xFFE7E5E4),  // hairline
    timelineBarSegment = Color(0xFFA8A29E),  // muted-soft — 중성, accent (ink) 와 분리
    timelineBarSegmentEdited = Color(0xFFC8B8E0),  // gradient-lavender — 편집 강조
    timelineBarDirective = Color(0xFFF4C5A8),  // gradient-peach — directive 와 hue 분리
    hairline = Color(0xFFE7E5E4),  // 1px 디바이더
    gradientMint = Color(0xFFA7E5D3),
    gradientPeach = Color(0xFFF4C5A8),
    gradientLavender = Color(0xFFC8B8E0),
    gradientSky = Color(0xFFA8C8E8),
    gradientRose = Color(0xFFE8B8C4),
)

// Dark palette — ElevenLabs 의 dark hero (Agents page) / featured pricing 패턴 확장.
// 다크에선 accent 를 white 로 invert (잉크 pill 의 dark 등가). orb 색은 동일하지만 호출 측에서
// alpha 60% → 30% 으로 낮춰 사용 (DESIGN.md 참조).
val DarkVibiColors = VibiColors(
    backgroundPrimary = Color(0xFF0C0A09),  // canvas-deep — ink as canvas
    onBackgroundPrimary = Color(0xFFFAFAFA),  // dark-ink
    chipBg = Color(0xFF1C1917),  // surface-dark-elevated
    chipBgDisabled = Color(0xFF292524),  // primary (ink) — disabled chip plate
    chipContentDisabled = Color(0xFFA8A29E),  // muted-soft
    panelBg = Color(0xFF1C1917),  // dark-surface-card
    panelBgSoft = Color(0xFF1C1917),  // dark 모드는 canvas-soft 분리 안함 — surface-card 와 동일 plate
    subtitleOverlayBg = Color(0xCCFAFAFA),  // light overlay on dark video
    mutedText = Color(0xFFA8A29E),  // dark-body
    accent = Color(0xFFFAFAFA),  // primary inverted — white pill on dark canvas
    timelineBarTrack = Color(0xFF292524),  // dark-hairline
    timelineBarSegment = Color(0xFF777169),  // muted — dark bg 위 중성
    timelineBarSegmentEdited = Color(0xFFC8B8E0),  // gradient-lavender — atmosphere 그대로
    timelineBarDirective = Color(0xFFF4C5A8),  // gradient-peach
    hairline = Color(0xFF292524),  // dark-hairline
    gradientMint = Color(0xFFA7E5D3),
    gradientPeach = Color(0xFFF4C5A8),
    gradientLavender = Color(0xFFC8B8E0),
    gradientSky = Color(0xFFA8C8E8),
    gradientRose = Color(0xFFE8B8C4),
)

// ElevenLabs 는 light-first. 시스템 dark 모드일 때만 dark 변종.
val LocalVibiColors = staticCompositionLocalOf { LightVibiColors }

@Composable
fun VibiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkVibiColors else LightVibiColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = tokens.backgroundPrimary,
            surface = tokens.panelBg,
            primary = tokens.accent,
            onPrimary = tokens.backgroundPrimary,  // dark: white pill 위 잉크 텍스트
            onBackground = tokens.onBackgroundPrimary,
            onSurface = tokens.onBackgroundPrimary
        )
    } else {
        lightColorScheme(
            background = tokens.backgroundPrimary,
            surface = tokens.panelBg,
            primary = tokens.accent,
            onPrimary = Color.White,  // light: 잉크 pill 위 흰 텍스트
            onBackground = tokens.onBackgroundPrimary,
            onSurface = tokens.onBackgroundPrimary
        )
    }
    val typography = rememberVibiTypography()
    CompositionLocalProvider(
        LocalVibiColors provides tokens,
        LocalVibiTypography provides typography,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
