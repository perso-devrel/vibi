import Cmp
import Foundation
import GoogleMobileAds
import UIKit

/// Swift implementation of the Kotlin `RewardedAdBridge` interface (defined in `:shared` commonMain).
/// 호출자(`IosRewardedAdController`)가 callback 을 suspend 함수로 변환한다.
///
/// Google Mobile Ads SDK v11.x (GAD-prefixed API) 기준. 보상형 광고를 매 호출마다 1회 load → present.
/// 보상은 클라가 지급하지 않는다 — `GADServerSideVerificationOptions.userIdentifier` 에 userId 를
/// 실어두면 시청 완료 시 Google 이 BFF `/credits/admob-ssv` 로 서명 콜백을 보내 +1 크레딧을 지급한다.
/// 본 클래스는 "끝까지 봤는가"만 outcome 으로 돌려준다.
///
/// outcome 문자열은 Kotlin `RewardedAdBridge` 의 expected 값과 동기 (변경 시 양쪽 모두 갱신):
/// `"earned"` | `"dismissed"` | `"unavailable"`.
final class RewardedAdBridgeImpl: NSObject, RewardedAdBridge, GADFullScreenContentDelegate {

    private enum Outcome {
        static let earned = "earned"
        static let dismissed = "dismissed"
        static let unavailable = "unavailable"
    }

    // AdMob iOS 보상형 광고 단위 ID (프로덕션).
    private let adUnitID = "ca-app-pub-4825847811436125/3906980637"

    private var rewardedAd: GADRewardedAd?
    private var callback: ((String) -> Void)?
    private var earned = false

    func showRewardedAd(userId: String, callback: @escaping (String) -> Void) {
        self.callback = callback
        self.earned = false

        // 비맞춤(non-personalized) 광고 요청 — npa=1. 앱 간 추적 없이 맥락 기반으로만 노출
        // → ATT 프롬프트/추적 매니페스트 불필요. 맞춤 전환 시 이 extras 제거 + 동의(UMP) 추가.
        let request = GADRequest()
        let extras = GADExtras()
        extras.additionalParameters = ["npa": "1"]
        request.register(extras)
        GADRewardedAd.load(withAdUnitID: adUnitID, request: request) { [weak self] ad, error in
            guard let self = self else { return }
            guard let ad = ad, error == nil else {
                self.finish(Outcome.unavailable)
                return
            }
            let options = GADServerSideVerificationOptions()
            options.userIdentifier = userId
            ad.serverSideVerificationOptions = options
            ad.fullScreenContentDelegate = self
            self.rewardedAd = ad

            guard let root = Self.topViewController() else {
                self.finish(Outcome.unavailable)
                return
            }
            ad.present(fromRootViewController: root) { [weak self] in
                // 보상 조건 충족(끝까지 시청). 실제 적립은 SSV 콜백이 처리.
                self?.earned = true
            }
        }
    }

    // MARK: - GADFullScreenContentDelegate

    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        finish(earned ? Outcome.earned : Outcome.dismissed)
    }

    func ad(
        _ ad: GADFullScreenPresentingAd,
        didFailToPresentFullScreenContentWithError error: Error
    ) {
        finish(Outcome.unavailable)
    }

    /// callback 은 정확히 1회. 중복 호출 방어 위해 nil 로 비운 뒤 호출.
    private func finish(_ outcome: String) {
        let cb = callback
        callback = nil
        rewardedAd = nil
        cb?(outcome)
    }

    /// 현재 표시 중인 최상단 view controller — 광고 present 대상.
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
            ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
