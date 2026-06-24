package com.vibi.shared.platform

/**
 * 음원분리 완료/실패 시 디바이스(로컬) 알림 게시. 갤러리에서 영상을 고르면 분리가 백그라운드로
 * 돌기 때문에, 사용자가 앱을 벗어난 사이 끝나면 디바이스 알림으로 알린다.
 *
 * - iOS = `UNUserNotificationCenter`. [IosSeparationNotifier] 가 willPresent delegate 를 설정해
 *   앱이 **포그라운드**일 때도 배너+사운드를 표시한다(미설정 시 OS 가 보류 → 전혀 안 울림).
 *   분리 완료가 포그라운드이거나 백그라운드 완료 후 재진입 시 post() 가 포그라운드에서 도는
 *   흔한 경로를 모두 커버. (완전 백그라운드 장기 잡은 폴링 suspend 로 post() 미실행 → APNs 필요.)
 * - Android = no-op — 분리 자동실행이 iOS 한정 ([AudioExtractor.isSupported]=false) 이라 현재
 *   Android 엔 트리거 지점이 없다.
 */
interface SeparationNotifier {
    /** 분리 시작 시 호출 — 알림 권한을 미리 요청해 완료 시점엔 허용 상태가 되도록. 멱등. */
    fun requestPermission()

    /** 로컬 알림 즉시 게시. 동일 [id] 면 OS 가 갱신 → 중복 누적 방지. */
    fun post(id: String, title: String, body: String)
}

/** 분리 알림 문구·식별자 단일 출처. 제목 한 줄만 노출(본문 없음). */
object SeparationNotice {
    const val COMPLETE_ID = "separation_complete"
    const val COMPLETE_TITLE = "Video ready"
    const val COMPLETE_BODY = ""

    const val FAILED_ID = "separation_failed"
    const val FAILED_TITLE = "Video preparation failed"
    const val FAILED_BODY = ""
}
