package com.vibi.cmp.platform

import com.vibi.shared.domain.model.IapPlatform
import com.vibi.shared.platform.IosIapClient
import com.vibi.shared.platform.IosPurchaseOutcome
import com.vibi.shared.platform.IosRestoreOutcome
import org.koin.mp.KoinPlatform

/**
 * iOS actual — Swift `IapBridgeImpl` (StoreKit2) 에 위임. AppleSignIn 과 동일한 bridge 패턴.
 * 결제 확인 popup 은 OS 가 그린다 (가이드라인 3.1.1 / 4.1 / 4.5).
 */
actual class PurchaseLauncher actual constructor() {
    private val client: IosIapClient by lazy { KoinPlatform.getKoin().get() }

    actual suspend fun purchase(productId: String): PurchaseResult =
        client.purchase(productId).toResult()

    actual suspend fun restorePurchases(): RestoreResult =
        client.restorePurchases().toResult()
}

private fun IosPurchaseOutcome.toResult(): PurchaseResult = when (this) {
    is IosPurchaseOutcome.Success -> PurchaseResult.Success(
        productId = productId,
        transactionId = transactionId,
        receipt = receipt,
        platform = IapPlatform.APPLE,
    )
    IosPurchaseOutcome.Cancelled -> PurchaseResult.UserCancelled
    is IosPurchaseOutcome.Failed -> PurchaseResult.Failed(message)
}

private fun IosRestoreOutcome.toResult(): RestoreResult = when (this) {
    IosRestoreOutcome.Completed -> RestoreResult.Completed
    IosRestoreOutcome.Cancelled -> RestoreResult.UserCancelled
    is IosRestoreOutcome.Failed -> RestoreResult.Failed(message)
}
