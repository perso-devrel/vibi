package com.vibi.cmp.ui.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vibi.cmp.theme.LocalVibiColors
import com.vibi.cmp.theme.LocalVibiTypography
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * IAP 미오픈 기간, 잔액 부족 화면에서 "Buy credits" 자리를 대체하는 고지 문구.
 *
 * 문구는 **중립적 피드백 수집**으로 유지한다 — "paid / coming soon / 곧 출시" 같이 아직 없는 유료
 * 기능을 광고하는 표현은 App Store 가이드라인 2.3.1(placeholder/미작동 기능 노출) 리스크라 의도적으로
 * 배제. "무료 크레딧을 다 썼고, 더 원하면 알려달라" 수준만 말한다. 실제 환호(이모지 컨페티)는
 * [ConfettiOverlay] 의 탭 보상으로만 터뜨린다 — 평상태의 절제와 대비가 보상감을 만든다.
 */
@Composable
fun PaidCreditsComingSoonNote(modifier: Modifier = Modifier) {
    val typo = LocalVibiTypography.current
    val tokens = LocalVibiColors.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "You've used your free credits.",
            style = typo.bodyMd.copy(fontWeight = FontWeight.SemiBold),
            color = tokens.onBackgroundPrimary,
        )
        Text(
            text = "Want more? Tap below to let us know.",
            style = typo.bodySm,
            color = tokens.mutedText,
        )
    }
}

/** "Buy credits" 대체 CTA 라벨 — 결제 유도가 아니라 중립적 피드백(더 원함) 수집. 2.3.1 회피. */
const val PAID_CREDITS_CTA_LABEL = "Let us know"

private class Confetto(
    val emoji: String,
    val startXFrac: Float,
    val startYFrac: Float,
    val fallEndFrac: Float,
    val sizeSp: Float,
    val swayAmpFrac: Float,
    val swayCycles: Float,
    val phase: Float,
    val rot0: Float,
    val rotSpeed: Float,
    val delay: Float,
)

private val CONFETTI_EMOJIS = listOf("❤️", "🎉", "🎊", "✨", "💖", "🥳", "💛", "💙")

/**
 * 전체화면 이모지 컨페티 1회 버스트. [Popup] 으로 그려 ModalBottomSheet / AlertDialog 위에도 덮인다.
 *
 * 싸구려로 보이는 함정(수직 낙하)을 피하려고 입자마다 sway(좌우 흔들림) + 회전 + 속도/시차 편차를
 * 준다. 약 1.6s 후 [onFinished] 를 호출해 호출자가 제거 — 재탭 시엔 호출자가 key 를 바꿔 재생성.
 */
@Composable
fun ConfettiOverlay(onFinished: () -> Unit) {
    val particles = remember {
        // 인스턴스마다 새 seed — 호출자가 key(nonce) 로 재생성하면 매번 다른 분포.
        val rnd = Random(Random.nextInt())
        List(40) {
            Confetto(
                emoji = CONFETTI_EMOJIS[rnd.nextInt(CONFETTI_EMOJIS.size)],
                startXFrac = rnd.nextFloat(),
                startYFrac = -0.15f + rnd.nextFloat() * 0.2f,
                fallEndFrac = 1.05f + rnd.nextFloat() * 0.2f,
                sizeSp = 20f + rnd.nextFloat() * 16f,
                swayAmpFrac = 0.02f + rnd.nextFloat() * 0.05f,
                swayCycles = 1.5f + rnd.nextFloat() * 2.5f,
                phase = rnd.nextFloat() * (2f * PI.toFloat()),
                rot0 = rnd.nextFloat() * 360f,
                rotSpeed = (rnd.nextFloat() - 0.5f) * 720f,
                delay = rnd.nextFloat() * 0.22f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1600, easing = LinearEasing))
        onFinished()
    }

    Popup(properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            val p = progress.value
            particles.forEach { c ->
                val t = ((p - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
                val yFrac = c.startYFrac + (c.fallEndFrac - c.startYFrac) * t
                val x = c.startXFrac * w + sin(t * c.swayCycles * 2f * PI.toFloat() + c.phase) * c.swayAmpFrac * w
                val y = yFrac * h
                val alpha = when {
                    t <= 0f -> 0f
                    t > 0.82f -> (1f - (t - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    else -> 1f
                }
                Text(
                    text = c.emoji,
                    style = TextStyle(fontSize = c.sizeSp.sp, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .graphicsLayer {
                            this.alpha = alpha
                            rotationZ = c.rot0 + c.rotSpeed * t
                        },
                )
            }
        }
    }
}
