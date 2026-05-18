import Cmp
import Foundation
import StoreKit

/// Swift implementation of the Kotlin `IapBridge` interface (defined in `:shared` commonMain).
/// 호출자(`IosIapClient`)가 callback 을 suspend 함수로 변환한다.
///
/// StoreKit2 (iOS 15+) — `Product.purchase()` 의 시스템 결제 popup 은 Apple 이 그린다.
/// 본 클래스는 trigger + 결과 매핑만; UI 모방 없음 (App Store 가이드라인 3.1.1 / 4.1 / 4.5).
///
/// 세션 동안 `Product` 객체는 캐시 — 매 구매마다 카탈로그 RTT (~100~500ms) 방지.
///
/// **출시 전 필수**:
///   - App Store Connect 에 productId 등록 (consumable IAP).
///   - Xcode > Capabilities > In-App Purchase 추가.
///   - Sandbox tester 계정으로 결제 흐름 verify.
///   - `Transaction.updates` 장기 listener 추가 — Ask-to-Buy 승인 / Family Sharing / crash
///     직후 복구 / 환불 revocation 처럼 `Product.purchase()` 호출 밖에서 도착하는 transaction
///     도 처리해야 결제 후 credit 누락 안 됨. (현 v1 은 명시적 구매 흐름만 커버)
final class IapBridgeImpl: NSObject, IapBridge {

    // outcome 문자열은 Kotlin `IapBridge` 의 expected 값과 동기. 변경 시 양쪽 모두 갱신.
    private enum Outcome {
        static let success = "success"
        static let cancelled = "cancelled"
        static let failed = "failed"
    }

    private var productCache: [String: Product] = [:]
    private let cacheQueue = DispatchQueue(label: "vibi.iap.cache")

    func purchase(
        productId: String,
        callback: @escaping (String, String?, String?, String?) -> Void
    ) -> IapCancellable {
        let task = Task {
            do {
                let product = try await cachedProduct(productId: productId)
                guard let product = product else {
                    callback(Outcome.failed, nil, nil, "product_not_found")
                    return
                }
                try Task.checkCancellation()
                let result = try await product.purchase()
                try Task.checkCancellation()

                switch result {
                case .success(let verification):
                    switch verification {
                    case .verified(let tx):
                        // 영수증 — Transaction.jsonRepresentation 은 Apple 서명된 JWS.
                        // BFF 가 App Store Server API 로 재검증하므로 그대로 base64 인코딩 전달.
                        let receiptB64 = tx.jsonRepresentation.base64EncodedString()
                        // 소비성 (consumable) — finish 호출해야 다음 구매 가능. BFF 검증 후 호출이
                        // 정석이지만 v1 stub 영수증 검증 단계라 여기서 즉시 finish.
                        await tx.finish()
                        callback(Outcome.success, "\(tx.id)", receiptB64, nil)
                    case .unverified(_, let error):
                        callback(Outcome.failed, nil, nil, "verification_failed: \(error.localizedDescription)")
                    }
                case .userCancelled:
                    callback(Outcome.cancelled, nil, nil, nil)
                case .pending:
                    // Ask-to-Buy / SCA 보류. 결과는 Transaction.updates 로 별도 도착 (출시 전 TODO).
                    callback(Outcome.failed, nil, nil, "purchase_pending")
                @unknown default:
                    callback(Outcome.failed, nil, nil, "unknown_result")
                }
            } catch is CancellationError {
                // 호출자가 취소 — callback 호출 안 함 (Kotlin 측 continuation 도 이미 cancel 상태).
                return
            } catch {
                callback(Outcome.failed, nil, nil, error.localizedDescription)
            }
        }
        return CancellableTask(task: task)
    }

    func restorePurchases(callback: @escaping (String, String?) -> Void) -> IapCancellable {
        let task = Task {
            do {
                // consumable 은 보통 복원 대상 아님. 권장사항 충족 + 향후 비소비성/구독 도입 시
                // 즉시 동작하도록 AppStore.sync() 호출.
                try await AppStore.sync()
                try Task.checkCancellation()
                callback(Outcome.success, nil)
            } catch is CancellationError {
                return
            } catch {
                callback(Outcome.failed, error.localizedDescription)
            }
        }
        return CancellableTask(task: task)
    }

    private func cachedProduct(productId: String) async throws -> Product? {
        if let cached = cacheQueue.sync(execute: { productCache[productId] }) {
            return cached
        }
        let products = try await Product.products(for: [productId])
        guard let product = products.first else { return nil }
        cacheQueue.sync { productCache[productId] = product }
        return product
    }
}

/// Kotlin `IapCancellable` 의 Swift 구현. `IosIapClient.suspendCancellableCoroutine.invokeOnCancellation`
/// 에서 호출되어 Swift Task 까지 취소 — 결제 popup 중 sheet dismiss 시 receipt 누수 방지.
private final class CancellableTask: IapCancellable {
    private let task: Task<Void, Never>
    init(task: Task<Void, Never>) { self.task = task }
    func cancel() { task.cancel() }
}
