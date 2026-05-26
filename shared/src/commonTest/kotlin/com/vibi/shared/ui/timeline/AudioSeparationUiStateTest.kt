package com.vibi.shared.ui.timeline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * canStart 가 잔액 부족 시 false 인지 — UI 의 Start 버튼 disable 핵심 invariant.
 * costPreview 가 미수신 (null) 인 startup race 에서는 Start 허용 (BFF 권위 검증이 폴백) 도 회귀 가드.
 */
class AudioSeparationUiStateTest {

    private val baseState = AudioSeparationUiState(segmentId = "seg-1")

    @Test
    fun `canStart true when SETUP and costPreview null`() {
        // sheet 막 열린 직후 — preview fetch 미완료. 일단 Start 허용, BFF 가 권위 검증.
        assertTrue(baseState.canStart)
    }

    @Test
    fun `canStart true when costPreview sufficient`() {
        val state = baseState.copy(
            costPreview = CreditCostPreview(60_000L, credits = 1, balance = 3, sufficient = true),
        )
        assertTrue(state.canStart)
    }

    @Test
    fun `canStart false when costPreview insufficient`() {
        val state = baseState.copy(
            costPreview = CreditCostPreview(60_000L, credits = 5, balance = 1, sufficient = false),
        )
        assertFalse(state.canStart)
    }

    @Test
    fun `canStart false when step is not SETUP`() {
        val state = baseState.copy(
            step = AudioSeparationStep.PROCESSING,
            costPreview = CreditCostPreview(60_000L, 1, 3, sufficient = true),
        )
        assertFalse(state.canStart)
    }

    @Test
    fun `canStart false when numberOfSpeakers out of range`() {
        val state = baseState.copy(
            numberOfSpeakers = 0,
            costPreview = CreditCostPreview(60_000L, 1, 3, sufficient = true),
        )
        assertFalse(state.canStart)
    }
}
