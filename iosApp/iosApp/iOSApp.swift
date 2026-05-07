import Cmp
import GoogleSignIn
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // BFF base URL 은 Auth.xcconfig 의 BFF_BASE_URL → Info.plist BFFBaseURL 로 expand.
        // Swift 코드에는 어떤 URL 도 직접 적지 않는다.
        guard let bffBaseUrl = Bundle.main.object(forInfoDictionaryKey: "BFFBaseURL") as? String,
              !bffBaseUrl.isEmpty else {
            preconditionFailure("BFFBaseURL missing in Info.plist — check Configs/Auth.xcconfig")
        }

        let bridge = GoogleSignInBridgeImpl()
        let composeVC = MainViewControllerKt.MainViewController(
            bffBaseUrl: bffBaseUrl,
            googleSignInBridge: bridge
        )

        // safe-area inset 무시하고 화면 전체 차지
        composeVC.edgesForExtendedLayout = .all
        composeVC.extendedLayoutIncludesOpaqueBars = true
        composeVC.additionalSafeAreaInsets = .zero

        let win = UIWindow(frame: UIScreen.main.bounds)
        // 라이트/다크 시스템 설정 따라감 — 동적 systemBackground 가 자동 반전.
        win.backgroundColor = UIColor.systemBackground
        win.rootViewController = composeVC
        win.makeKeyAndVisible()
        composeVC.view.frame = win.bounds
        composeVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        composeVC.view.backgroundColor = UIColor.systemBackground
        // overrideUserInterfaceStyle 제거 — 시스템 설정 (Settings → Display) 따라감.
        self.window = win
        return true
    }

    /// Google Sign-In OAuth callback URL 처리. Info.plist 의 reversed client id URL
    /// scheme 으로 돌아온 redirect 를 GoogleSignIn SDK 에 전달.
    func application(
        _ app: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey: Any] = [:]
    ) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }
}
