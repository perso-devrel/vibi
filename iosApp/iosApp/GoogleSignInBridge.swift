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

    func signIn(callback: @escaping (String?, String?) -> Void) {
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
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        let root = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
        var top = root
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
