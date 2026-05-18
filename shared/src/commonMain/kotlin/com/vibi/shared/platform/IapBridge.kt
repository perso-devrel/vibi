package com.vibi.shared.platform

/**
 * Swift 가 구현해 K/N 으로 주입하는 IAP bridge. iOS 만 사용 — Android 는 별도 `BillingClient`
 * 통합 시점에 같은 패턴으로 확장 가능하지만 v1 은 stub.
 *
 * StoreKit2 (`Product.purchase()` 등) 는 Swift-only API 라 Kotlin/Native cinterop 으로 직접
 * 호출할 수 없다. Apple Sign-In 과 동일한 callback bridge 방식으로 Swift 측이 결제 흐름을
 * 처리하고 결과만 Kotlin 으로 전달.
 *
 * suspend 함수를 Swift 가 구현하기 어려워 callback 으로 단순화. `IosIapClient` 가 wrapper 로
 * `suspendCancellableCoroutine` 변환 + 반환된 [IapCancellable] 로 코루틴 취소 시 Swift Task
 * 도 취소.
 *
 * outcome 문자열 단일 source — Swift 와 Kotlin 양쪽이 같은 값 사용. 변경 시 두 쪽 모두 갱신.
 * (`"success"` | `"cancelled"` | `"failed"`)
 *
 * **출시 전 TODO**: `Transaction.updates` 장기 listener 추가 — Ask-to-Buy 승인 / Family
 * Sharing / crash 직후 복구 / 환불 revocation 처럼 `Product.purchase()` 호출 밖에서
 * 도착하는 transaction 도 처리해야 사용자가 결제 후 credit 누락되지 않는다.
 */
interface IapBridge {
    /**
     * StoreKit2 `Product.purchase()` 트리거. callback 은 정확히 1회 호출.
     *
     * @return 코루틴이 취소되면 [IapCancellable.cancel] 로 Swift Task 까지 전파. 이미 결제 popup
     *   이 떠 있으면 Swift 측이 무시 (Apple 가이드).
     * @param outcome `"success"` | `"cancelled"` | `"failed"`
     * @param transactionId `Transaction.id` 문자열 — success 일 때만 non-null, BFF idempotency key.
     * @param receipt `Transaction.jsonRepresentation` 의 base64 — success 일 때만 non-null.
     * @param errorMessage failed 일 때 사유.
     */
    fun purchase(
        productId: String,
        callback: (
            outcome: String,
            transactionId: String?,
            receipt: String?,
            errorMessage: String?,
        ) -> Unit,
    ): IapCancellable

    /**
     * `AppStore.sync()` 호출. consumable (크레딧) 은 보통 복원 대상 아니지만 App Store
     * 가이드라인 권장사항 충족 + 향후 비소비성/구독 도입 시 즉시 동작하도록 노출.
     *
     * @param outcome `"success"` | `"cancelled"` | `"failed"`
     */
    fun restorePurchases(
        callback: (outcome: String, errorMessage: String?) -> Unit,
    ): IapCancellable
}

/**
 * 진행 중인 StoreKit Task 의 취소 핸들. `suspendCancellableCoroutine.invokeOnCancellation`
 * 에서 호출되어 sheet dismiss / 화면 이탈 시 Swift 측 Task 도 같이 끊는다.
 */
interface IapCancellable {
    fun cancel()
}
