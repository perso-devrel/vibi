package com.vibi.shared.data.remote.dto

import kotlinx.serialization.Serializable

/** GET /api/v2/credits 응답 — 현재 잔액. */
@Serializable
data class CreditBalanceResponse(
    val balance: Int,
)

/**
 * GET /api/v2/credits/cost?durationMs=N 응답.
 *
 * 음원 분리 시작 전 "이 구간 X 크레딧 사용, 잔액 Y. 진행할까요?" 팝업 표시용. BFF 공식:
 * 시작된 5분당 1 크레딧 (`ceil(durationMs / 5분)`, 최소 1, block 경계 ~1초 grace). 모바일은
 * 로컬 추정 안 하고 BFF 응답을 단일 source 로 표시만 한다 (network 무료 / 인증 잔액 동기화).
 *
 * - [durationMs] — 요청 echo back.
 * - [credits]    — 실제 차감될 크레딧 수.
 * - [balance]    — 호출 시점 잔액.
 * - [sufficient] — balance >= credits. false 면 모바일은 충전 화면 분기.
 */
@Serializable
data class CreditCostResponse(
    val durationMs: Long,
    val credits: Int,
    val balance: Int,
    val sufficient: Boolean,
)

/**
 * BFF 표준 에러 응답. `/separate` 의 402 `insufficient_credits` 등에서 detail 필드에
 * "required=N balance=M" 형식. AudioSeparationRepository 가 그 필드를 파싱해 typed
 * [com.vibi.shared.domain.error.InsufficientCreditsException] 으로 변환.
 */
@Serializable
data class BffErrorResponse(
    val error: String,
    val detail: String? = null,
)

/**
 * POST /api/v2/credits/purchase 요청.
 *
 * - [productId] — App Store / Play Store SKU. BFF `CreditCatalog` 와 1:1.
 * - [platform] — IAP 시스템 wire name ("apple" / "google"). `IapPlatform.wireName` 사용.
 * - [receipt] — StoreKit2 `Transaction.jsonRepresentation` (base64) 또는 Play Billing
 *   `Purchase.purchaseToken`. BFF 가 Apple / Google 서버 검증에 그대로 전달.
 * - [transactionId] — idempotency key. retry 시 같은 값을 보내면 BFF 가 `granted=0` 반환.
 */
@Serializable
data class CreditPurchaseRequest(
    val productId: String,
    val platform: String,
    val receipt: String,
    val transactionId: String,
)

/**
 * 결제 처리 결과.
 *
 * @param granted 이번 호출로 가산된 크레딧. duplicate transactionId 면 0.
 * @param balance 가산 후 현재 잔액.
 */
@Serializable
data class CreditPurchaseResponse(
    val granted: Int,
    val balance: Int,
    val transactionId: String,
)

/**
 * POST /api/v2/credits/admin-grant 요청. admin role 만 호출 가능. body 는 productId 만 —
 * txId 는 서버가 생성해 매 호출마다 새 grant 로 처리. 응답은 [CreditPurchaseResponse] 공유.
 */
@Serializable
data class AdminGrantRequest(
    val productId: String,
)
