package com.vibi.cmp.platform

import com.vibi.shared.domain.model.IapPlatform

/**
 * 인앱 결제 trigger — 플랫폼 결제 시스템 (StoreKit / Play Billing) 을 호출하고,
 * 시스템 UI 가 띄우는 결제 확인 popup 결과를 콜백으로 돌려준다.
 *
 * **중요**: 결제 확인 dialog 는 반드시 OS 가 그린다 (Apple App Store 가이드라인 3.1.1 /
 * Google Play 정책). 앱 자체가 Apple/Google 시스템 UI 를 모방하는 popup 을 띄우면 reject.
 *
 * v1 은 mock — 실제 StoreKit / Play Billing 연동은 다음 마일스톤.
 */
expect class PurchaseLauncher() {
    suspend fun purchase(productId: String): PurchaseResult
    /**
     * App Store / Play Store 가 보관 중인 이전 비소비성 / 구독 구매 복원.
     *
     * 본 호출은 단순히 OS 캐시를 새로고침할 뿐 — 복원된 transaction 자체는 별도 listener
     * (`Transaction.updates`, 출시 전 TODO) 가 enumerate. 따라서 [RestoreResult] 는 단순 완료/취소/실패만.
     */
    suspend fun restorePurchases(): RestoreResult
}

sealed interface PurchaseResult {
    /**
     * 시스템 결제 성공.
     *
     * @param transactionId 결제 영수증의 unique ID. BFF idempotency key 로 사용.
     * @param receipt StoreKit2 `Transaction.jsonRepresentation` (base64) 또는 Play Billing
     *   `Purchase.purchaseToken`. BFF 가 영수증 검증에 그대로 사용.
     */
    data class Success(
        val productId: String,
        val transactionId: String,
        val receipt: String,
        val platform: IapPlatform,
    ) : PurchaseResult
    data object UserCancelled : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

sealed interface RestoreResult {
    data object Completed : RestoreResult
    data object UserCancelled : RestoreResult
    data class Failed(val message: String) : RestoreResult
}
