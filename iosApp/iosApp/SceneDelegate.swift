import Cmp
import GoogleSignIn
import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }

        // BFF base URL 은 Auth.xcconfig 의 BFF_BASE_URL → Info.plist BFFBaseURL 로 expand.
        // Swift 코드에는 어떤 URL 도 직접 적지 않는다.
        guard let bffBaseUrl = Bundle.main.object(forInfoDictionaryKey: "BFFBaseURL") as? String,
              !bffBaseUrl.isEmpty else {
            preconditionFailure("BFFBaseURL missing in Info.plist — check Configs/Auth.xcconfig")
        }

        let googleBridge = GoogleSignInBridgeImpl()
        let appleBridge = AppleSignInBridgeImpl()
        let iapBridge = IapBridgeImpl()
        let onDeviceExportBridge = OnDeviceVideoExportBridgeImpl()
        let composeVC = MainViewControllerKt.MainViewController(
            bffBaseUrl: bffBaseUrl,
            googleSignInBridge: googleBridge,
            appleSignInBridge: appleBridge,
            iapBridge: iapBridge,
            onDeviceVideoExportBridge: onDeviceExportBridge
        )

        // safe-area inset 무시하고 화면 전체 차지
        composeVC.edgesForExtendedLayout = .all
        composeVC.extendedLayoutIncludesOpaqueBars = true
        composeVC.additionalSafeAreaInsets = .zero

        let win = UIWindow(windowScene: windowScene)
        // 라이트/다크 시스템 설정 따라감 — 동적 systemBackground 가 자동 반전.
        win.backgroundColor = UIColor.systemBackground
        win.rootViewController = composeVC
        win.makeKeyAndVisible()
        composeVC.view.frame = win.bounds
        composeVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        composeVC.view.backgroundColor = UIColor.systemBackground
        self.window = win
    }

    /// Google Sign-In OAuth callback URL 처리. Info.plist 의 reversed client id URL
    /// scheme 으로 돌아온 redirect 를 GoogleSignIn SDK 에 전달.
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url else { return }
        _ = GIDSignIn.sharedInstance.handle(url)
    }
}
