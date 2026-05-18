package com.vibi.cmp.platform

import com.vibi.shared.domain.model.IapPlatform
import com.vibi.shared.platform.currentTimeMillis
import kotlinx.coroutines.delay

/**
 * Android actual — v1 mock. 실제 결제는 Play Console productId 등록 +
 * BillingClient.launchBillingFlow + 영수증 검증 BFF 라우트 추가 후 교체.
 */
actual class PurchaseLauncher actual constructor() {
    actual suspend fun purchase(productId: String): PurchaseResult {
        delay(450)
        return PurchaseResult.Success(
            productId = productId,
            transactionId = "mock-android-${currentTimeMillis()}",
            receipt = "mock-receipt-$productId",
            platform = IapPlatform.GOOGLE,
        )
    }

    actual suspend fun restorePurchases(): RestoreResult {
        delay(250)
        return RestoreResult.Failed("복원할 구매 내역이 없습니다.")
    }
}
