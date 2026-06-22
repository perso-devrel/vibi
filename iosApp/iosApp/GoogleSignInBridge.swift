import Cmp
import GoogleSignIn
import UIKit

/// Swift implementation of the Kotlin `GoogleSignInBridge` interface (defined in
/// `:shared` commonMain). 호출자(`IosGoogleSignInClient`)가 callback 을 suspend
/// 함수로 변환한다.
///
/// suspend 함수를 Swift 가 직접 구현하기 까다로워 (KMP 의 Kotlin → Swift async 는
/// 자동이지만 반대 방향은 wrapper 필요) callback 패턴으로 단순화.
final class GoogleSignInBridgeImpl: NSObject, GoogleSignInBridge {

    func signIn(callback_ callback: @escaping (String?, String?) -> Void) {
        guard let presenter = topViewController() else {
            callback(nil, "no_presenting_view_controller")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error {
                callback(nil, error.localizedDescription)
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                callback(nil, "missing_id_token")
                return
            }
            callback(idToken, nil)
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }

    private func topViewController() -> UIViewController? {
        var top = UIApplication.shared.activeKeyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

/// iPad / iOS 26 호환 presenter 조회. 기존 구현은 `activationState == .foregroundActive`
/// scene 만 봐서, iPad 호환(iPhone-only 앱이 iPad 에서 도는) 모드나 scene 전환 타이밍에
/// 그 조건을 만족하는 scene 이 없으면 nil 을 반환 → OAuth 시트가 present 되지 않고
/// GoogleSignIn / AuthenticationServices 콜백이 영영 오지 않아 로그인 무한 로딩.
/// foregroundActive scene → 아무 scene 의 key window → 아무 window 순으로 단계적 폴백.
extension UIApplication {
    var activeKeyWindow: UIWindow? {
        let scenes = connectedScenes.compactMap { $0 as? UIWindowScene }
        if let w = scenes.first(where: { $0.activationState == .foregroundActive })?
            .windows.first(where: { $0.isKeyWindow }) {
            return w
        }
        if let w = scenes.flatMap({ $0.windows }).first(where: { $0.isKeyWindow }) {
            return w
        }
        return scenes.flatMap { $0.windows }.first
    }
}
