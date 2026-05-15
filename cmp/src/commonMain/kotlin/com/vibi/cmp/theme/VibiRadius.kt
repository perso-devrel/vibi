package com.vibi.cmp.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * DESIGN.md rounded 토큰 — 모바일 튜닝 스케일.
 *
 * 새 코드는 raw `12.dp` (스케일에 없음) 대신 [VibiRadius.lg] 같은 토큰 사용.
 * RoundedCornerShape 미리 인스턴스화한 [VibiShape] 도 동시 노출.
 */
object VibiRadius {
    /** timeline segment block. */
    val xs = 4.dp
    /** subtitle overlay. */
    val sm = 6.dp
    /** panel-card. */
    val lg = 14.dp
    /** feature-card. */
    val xl = 18.dp
    /** CTA button / badge / chip / avatar / icon-button. */
    val pill = 9999.dp
}

object VibiShape {
    val xs = RoundedCornerShape(VibiRadius.xs)
    val sm = RoundedCornerShape(VibiRadius.sm)
    val lg = RoundedCornerShape(VibiRadius.lg)
    val xl = RoundedCornerShape(VibiRadius.xl)
    val pill = RoundedCornerShape(VibiRadius.pill)
}
