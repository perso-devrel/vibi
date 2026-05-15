package com.vibi.cmp.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import vibi.cmp.generated.resources.EBGaramond_VariableFont_wght
import vibi.cmp.generated.resources.Inter_Medium
import vibi.cmp.generated.resources.Inter_Regular
import vibi.cmp.generated.resources.Inter_SemiBold
import vibi.cmp.generated.resources.JetBrainsMono_Medium
import vibi.cmp.generated.resources.Res

/**
 * DESIGN.md typography 토큰 15종. Material3 의 [androidx.compose.material3.Typography] 와
 * 별개 — Material slot(displayLarge/headlineMedium/...) 로는 표현 못 하는 EB Garamond Light(300) +
 * Inter mixed family / mono-time 같은 도메인 전용 스타일을 보존.
 *
 * letterSpacing 단위: DESIGN.md 는 px (e.g. -0.8px @ 40px = -0.02em). Compose 의 `.em` 은 fontSize
 * 비례라 모든 크기에서 비율 유지 — px → em 변환 (px / fontSize) 후 .em 로 표현.
 */
@Immutable
data class VibiTypography(
    /** 40/300/-0.02em — 메인 hero (모바일 캡). */
    val displayHero: TextStyle,
    /** 32/300/-0.01em — 서브 hero. */
    val displayXl: TextStyle,
    /** 28/300/-0.01em — 섹션 헤드. */
    val displayLg: TextStyle,
    /** 24/300/-0.01em — CTA section / 그룹 타이틀. */
    val displayMd: TextStyle,
    /** 20/300/0 — bottom-sheet 헤더. */
    val displaySm: TextStyle,
    /** 18/500 — 카드 타이틀 (Inter). */
    val titleLg: TextStyle,
    /** 17/500 — top-bar / list section header. */
    val titleMd: TextStyle,
    /** 15/500/+0.01em — row 라벨 / 강조 캡션. */
    val titleSm: TextStyle,
    /** 16/400/+0.01em — 기본 body. */
    val bodyMd: TextStyle,
    /** 16/500/+0.01em — 강조 body. */
    val bodyStrong: TextStyle,
    /** 14/400/+0.01em — 보조 body / chip. */
    val bodySm: TextStyle,
    /** 13/400 — 사진 캡션 / 시간. */
    val caption: TextStyle,
    /** 11/600/+0.08em UPPERCASE — 섹션 라벨 / badge. textTransform 미지원 → 호출처 .uppercase(). */
    val captionUppercase: TextStyle,
    /** 16/500 — CTA pill. */
    val button: TextStyle,
    /** 11/500/+0.01em — 탭바 라벨. */
    val tabLabel: TextStyle,
    /** 13/500 mono — 타임코드 / 재생 위치. */
    val monoTime: TextStyle,
)

/**
 * fontFamily 만 매개변수로 받는 토큰 빌더. fontSize/weight/lineHeight/letterSpacing 은 DESIGN.md
 * 그대로 고정. system fallback 용 [DefaultVibiTypography] 와 번들 폰트용 [rememberVibiTypography]
 * 가 같은 빌더를 공유.
 */
private fun buildVibiTypography(
    displayFamily: FontFamily,
    bodyFamily: FontFamily,
    monoFamily: FontFamily,
): VibiTypography = VibiTypography(
    displayHero = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        lineHeight = 43.sp,        // 1.08
        letterSpacing = (-0.02).em,
    ),
    displayXl = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 36.sp,        // 1.13
        letterSpacing = (-0.01).em,
    ),
    displayLg = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 33.sp,        // 1.18
        letterSpacing = (-0.01).em,
    ),
    displayMd = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 24.sp,
        lineHeight = 29.sp,        // 1.2
        letterSpacing = (-0.01).em,
    ),
    displaySm = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        lineHeight = 25.sp,        // 1.25
    ),
    titleLg = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 25.sp,        // 1.4
    ),
    titleMd = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,        // 1.4
    ),
    titleSm = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,        // 1.4
        letterSpacing = 0.01.em,
    ),
    bodyMd = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,        // 1.5
        letterSpacing = 0.01.em,
    ),
    bodyStrong = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em,
    ),
    bodySm = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,        // 1.5
        letterSpacing = 0.01.em,
    ),
    caption = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,        // 1.4
    ),
    captionUppercase = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,        // 1.4
        letterSpacing = 0.08.em,
    ),
    button = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 16.sp,        // 1.0
    ),
    tabLabel = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,        // 1.2
        letterSpacing = 0.01.em,
    ),
    monoTime = TextStyle(
        fontFamily = monoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 13.sp,        // 1.0
    ),
)

/** system fallback — VibiTheme 밖 (test / preview) 에서만 의미. 정상 경로는 [rememberVibiTypography]. */
private val DefaultVibiTypography: VibiTypography = buildVibiTypography(
    displayFamily = FontFamily.Serif,
    bodyFamily = FontFamily.SansSerif,
    monoFamily = FontFamily.Monospace,
)

/**
 * 번들된 ttf 로 폰트 패밀리를 구성. Font(Res.font.X) 가 @Composable 이라 본 함수도 @Composable.
 *  - Display: EB Garamond variable font (weight axis) — Light(300) 추출.
 *  - Body:    Inter Regular(400) / Medium(500) / SemiBold(600) static 3 weight.
 *  - Mono:    JetBrains Mono Medium(500).
 *
 * Why no-key `remember`: Font(...) 는 매 호출마다 새 instance 라 FontFamily(...) 도 매번 새 reference.
 * `remember(font, ...)` key 로는 cache miss 가 누적돼 매 recomposition 마다 16개 TextStyle 재할당 +
 * static CompositionLocal 의 모든 consumer (5개 화면 + 자식들) 를 강제 recompose. no-key remember
 * 로 첫 호출 시만 빌드해 동일 인스턴스를 흘려보냄.
 */
@Composable
fun rememberVibiTypography(): VibiTypography {
    val ebGaramond = Font(Res.font.EBGaramond_VariableFont_wght, weight = FontWeight.Light)
    val interRegular = Font(Res.font.Inter_Regular, weight = FontWeight.Normal)
    val interMedium = Font(Res.font.Inter_Medium, weight = FontWeight.Medium)
    val interSemiBold = Font(Res.font.Inter_SemiBold, weight = FontWeight.SemiBold)
    val jetBrainsMonoMedium = Font(Res.font.JetBrainsMono_Medium, weight = FontWeight.Medium)
    return remember {
        buildVibiTypography(
            displayFamily = FontFamily(ebGaramond),
            bodyFamily = FontFamily(interRegular, interMedium, interSemiBold),
            monoFamily = FontFamily(jetBrainsMonoMedium),
        )
    }
}

val LocalVibiTypography = staticCompositionLocalOf { DefaultVibiTypography }
