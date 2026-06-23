package com.vibi.shared.domain.model

/**
 * BFF `/credits/cost` 응답의 도메인 매핑. AudioSeparationSheet 가 "이 구간 X 크레딧 사용,
 * 잔액 Y. 진행할까요?" 표시 + Start 버튼 분기에 사용.
 *
 * 비용 공식은 BFF 가 단일 source — 시작된 5분당 1 크레딧, 올림, 최소 1. 모바일은 표시만 담당.
 */
data class SeparationCost(
    val durationMs: Long,
    val credits: Int,
    val balance: Int,
    val sufficient: Boolean,
)
