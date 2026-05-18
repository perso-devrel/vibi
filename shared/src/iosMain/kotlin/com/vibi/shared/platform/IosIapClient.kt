package com.vibi.shared.platform

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [IapBridge] callback 을 suspend API 로 wrap.
 *
 * 코루틴 취소 시 bridge 가 반환한 [IapCancellable] 을 통해 Swift Task 도 취소 — sheet dismiss
 * 직후 결제 popup 이 무한히 살아 receipt 가 사라지는 race 방지.
 */
class IosIapClient(
    private val bridge: IapBridge,
) {
    suspend fun purchase(productId: String): IosPurchaseOutcome = suspendCancellableCoroutine { cont ->
        val handle = bridge.purchase(productId) { outcome, txId, receipt, errorMessage ->
            val result = when (outcome) {
                "success" -> IosPurchaseOutcome.Success(
                    productId = productId,
                    transactionId = txId.orEmpty(),
                    receipt = receipt.orEmpty(),
                )
                "cancelled" -> IosPurchaseOutcome.Cancelled
                else -> IosPurchaseOutcome.Failed(errorMessage ?: "iap_failed")
            }
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { handle.cancel() }
    }

    suspend fun restorePurchases(): IosRestoreOutcome = suspendCancellableCoroutine { cont ->
        val handle = bridge.restorePurchases { outcome, errorMessage ->
            val result = when (outcome) {
                "success" -> IosRestoreOutcome.Completed
                "cancelled" -> IosRestoreOutcome.Cancelled
                else -> IosRestoreOutcome.Failed(errorMessage ?: "restore_failed")
            }
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { handle.cancel() }
    }
}

sealed interface IosPurchaseOutcome {
    data class Success(val productId: String, val transactionId: String, val receipt: String) : IosPurchaseOutcome
    data object Cancelled : IosPurchaseOutcome
    data class Failed(val message: String) : IosPurchaseOutcome
}

/**
 * Restore 는 결과 payload (productId/transactionId/receipt) 가 없다 — `AppStore.sync()` 는
 * 단순히 캐시 새로고침이고 실제 복원된 transaction 은 `Transaction.currentEntitlements` 로
 * 따로 enumerate 해야 한다 (별도 작업). 따라서 [IosPurchaseOutcome] 의 빈-문자열 placeholder
 * 를 피하려 별도 sealed 로 분리.
 */
sealed interface IosRestoreOutcome {
    data object Completed : IosRestoreOutcome
    data object Cancelled : IosRestoreOutcome
    data class Failed(val message: String) : IosRestoreOutcome
}
