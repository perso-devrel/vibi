package com.vibi.cmp.theme

import androidx.compose.ui.graphics.Color
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.StemKind

/**
 * 음원분리 stem 의 시각 색. SoundDeck 카드 chip + 타임라인 파형 highlight 가 같은 매핑을 공유해
 * 같은 화자가 두 군데서 같은 색으로 인지된다.
 *
 * speaker palette 는 [VibiColors.gradient*] 5종에서 hue 가 잘 떨어진 순서로 선택. 6+ 화자는 wrap.
 */
object SpeakerPalette {
    private fun palette(tokens: VibiColors): List<Color> = listOf(
        tokens.gradientSky,
        tokens.gradientPeach,
        tokens.gradientMint,
        tokens.gradientRose,
        tokens.gradientLavender,
    )

    fun colorFor(speakerIndex: Int?, tokens: VibiColors): Color {
        val p = palette(tokens)
        val i = ((speakerIndex ?: 1) - 1).coerceAtLeast(0) % p.size
        return p[i]
    }

    /**
     * stemId → 시각 색. SPEAKER 는 [colorFor], BACKGROUND 는 mutedText, 그 외는 [fallback].
     * SoundCard chip 과 TimelineWaveform highlight 가 같은 진입점을 쓰도록 통합.
     */
    fun stemColor(stemId: String, tokens: VibiColors, fallback: Color): Color =
        when (Stem.kindFromId(stemId)) {
            StemKind.SPEAKER -> colorFor(Stem.speakerIndexFromId(stemId), tokens)
            StemKind.BACKGROUND -> tokens.mutedText
            else -> fallback
        }
}
