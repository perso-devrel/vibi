@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vibi.shared.platform

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS 로컬 알림. `UNUserNotificationCenter` 에 trigger=null 요청을 등록해 즉시 게시한다.
 *
 * [ForegroundPresentationDelegate] 를 `center.delegate` 로 설정해 `willPresentNotification` 에서
 * banner+sound 를 반환한다 — delegate 가 없으면 앱이 **포그라운드**일 때 iOS 가 배너/사운드를
 * 보류해 알림이 전혀 울리지 않는다. 분리 완료 시점이 포그라운드이거나(앱에서 대기), 백그라운드
 * 완료 후 앱 재진입 시 post() 가 포그라운드에서 실행되는 흔한 경로 모두 해당한다. Koin single 로
 * 등록돼 delegate(이 클래스가 강참조로 보유) 가 프로세스 수명 동안 유지된다.
 *
 * (Kotlin 인터페이스 SeparationNotifier 와 ObjC 슈퍼타입은 한 클래스에 섞을 수 없어 delegate 를
 * 별도 NSObject 로 분리한다.)
 *
 * 주의(미해결): 기기가 완전히 백그라운드인 채로 오래 걸리는 분리 잡이 끝나는 경우엔 폴링 루프가
 * suspend 되어 post() 자체가 미실행 → 이 delegate 만으로는 못 울린다. 그 경로까지 확실히 알리려면
 * BFF 의 잡 완료 APNs push 가 필요(별도 작업).
 */
class IosSeparationNotifier : SeparationNotifier {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    // 강참조 보유 필수 — delegate 는 weak 라 GC 되면 포그라운드 표시가 다시 깨진다.
    private val presentationDelegate = ForegroundPresentationDelegate()

    init {
        center.delegate = presentationDelegate
    }

    override fun requestPermission() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ -> }
    }

    override fun post(id: String, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
        }
        // trigger=null → 즉시 게시. 동일 identifier 면 OS 가 기존 알림을 갱신.
        val request = UNNotificationRequest.requestWithIdentifier(id, content, null)
        center.addNotificationRequest(request, null)
    }
}

/**
 * 포그라운드 표시 delegate — `willPresentNotification` 에서 배너+사운드를 반환해, 앱이 떠 있을 때
 * iOS 가 알림을 보류하던 기본 동작을 해제한다. ObjC 슈퍼타입이라 [IosSeparationNotifier] 본체와
 * 분리(한 클래스에 Kotlin 인터페이스 + ObjC 슈퍼타입 혼용 불가).
 */
private class ForegroundPresentationDelegate :
    NSObject(),
    UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound,
        )
    }
}
