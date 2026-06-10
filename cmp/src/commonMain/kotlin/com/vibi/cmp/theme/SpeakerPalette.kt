package com.vibi.cmp.theme

import androidx.compose.ui.graphics.Color
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.model.StemKind

/**
 * 음원분리 stem 의 시각 색. SoundDeck 카드 chip + 타임라인 파형 highlight 가 같은 매핑을 공유해
 * 같은 화자가 두 군데서 같은 색으로 인지된다.
 *
 * speaker 는 **고채도** — BGM (muted pastel) 과 채도로 구분되도록 vivid 톤 4색. 5+ 화자는 wrap.
 */
object SpeakerPalette {
    private val palette: List<Color> = listOf(
        Color(0xFF1E88E5),  // speaker 1 — blue
        Color(0xFFE65100),  // speaker 2 — deep orange
        Color(0xFF2E7D32),  // speaker 3 — green
        Color(0xFFC2185B),  // speaker 4 — pink
    )

    @Suppress("UNUSED_PARAMETER")
    fun colorFor(speakerIndex: Int?, tokens: VibiColors): Color {
        // speakerIndex 는 BFF stemId "speaker_<N>" 의 N — 0-based. (BFF SeparationService 참고.)
        // 1-based 가정으로 -1 하면 speaker_0 과 speaker_1 이 둘 다 palette[0] 으로 collapse 됨.
        val i = (speakerIndex ?: 0).coerceAtLeast(0) % palette.size
        return palette[i]
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

/**
 * BGM 클립 (파일 삽입 + 즉시 녹음 통합) 의 시각 색. 클립 id 해시 기준 4색 슬롯 — 한 번 정해진 색이
 * 다른 클립의 삽입/삭제·드래그(재정렬)에 흔들리지 않는다. 5+ 클립은 색이 겹칠 수 있으나(슬롯 4개)
 * 안정성을 우선. (이전엔 createdAt dense rank 라 중간 클립 삭제 시 뒤 클립들의 색이 한 칸씩 당겨졌음.)
 */
object BgmPalette {
    /** palette() 색 수와 일치해야 함 — [stableIndexForClipId] 가 토큰 없이 슬롯을 계산해야 해 상수로 둠. */
    private const val COLOR_COUNT = 4

    private fun palette(tokens: VibiColors): List<Color> = listOf(
        tokens.gradientMint,
        tokens.gradientLavender,
        tokens.gradientRose,
        tokens.gradientPeach,
    )

    /**
     * 클립 id → 안정 색 슬롯(1-based). id 는 생성 시 1회 부여 후 불변이라 색이 영구 고정된다.
     * [colorFor] 와 짝 — 반환값을 그대로 넘기면 같은 색.
     */
    fun stableIndexForClipId(clipId: String): Int = clipId.hashCode().mod(COLOR_COUNT) + 1

    fun colorFor(bgmIndex: Int?, tokens: VibiColors): Color {
        val p = palette(tokens)
        val i = ((bgmIndex ?: 1) - 1).coerceAtLeast(0) % p.size
        return p[i]
    }
}
