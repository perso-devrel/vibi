import UIKit
import Cmp

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let bffBaseUrl = "http://localhost:8080/"
        let composeVC = MainViewControllerKt.MainViewController(bffBaseUrl: bffBaseUrl)

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
}
