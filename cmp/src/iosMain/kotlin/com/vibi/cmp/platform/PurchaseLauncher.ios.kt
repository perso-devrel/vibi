package com.vibi.cmp.platform

import com.vibi.shared.domain.model.IapPlatform
import com.vibi.shared.platform.currentTimeMillis
import kotlinx.coroutines.delay

/**
 * iOS actual — v1 mock. 실제 결제는 App Store Connect productId 등록 +
 * `StoreKit.Product.purchase()` 호출 + Apple 서버 영수증 검증 BFF 작업 후 교체.
 *
 * **앱 심사 주의**: 결제 확인 popup 은 Apple 만 그린다. 가이드라인 3.1.1 / 4.1 / 4.5.
 */
actual class PurchaseLauncher actual constructor() {
    actual suspend fun purchase(productId: String): PurchaseResult {
        delay(450)
        return PurchaseResult.Success(
            productId = productId,
            transactionId = "mock-apple-${currentTimeMillis()}",
            receipt = "mock-receipt-$productId",
            platform = IapPlatform.APPLE,
        )
    }

    actual suspend fun restorePurchases(): PurchaseResult {
        delay(250)
        return PurchaseResult.Failed("복원할 구매 내역이 없습니다.")
    }
}
