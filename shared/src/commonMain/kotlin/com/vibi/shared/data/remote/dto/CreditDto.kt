package com.vibi.shared.data.remote.dto

import kotlinx.serialization.Serializable

/** GET /api/v2/credits 응답 — 현재 잔액. */
@Serializable
data class CreditBalanceResponse(
    val balance: Int,
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
