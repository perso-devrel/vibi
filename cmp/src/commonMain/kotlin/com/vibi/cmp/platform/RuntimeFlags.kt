package com.vibi.cmp.platform

/**
 * 런타임 토글 — 디자인 변경처럼 점진 이행이 필요한 기능을 코드에 둔 채로 끄고 켤 수 있게.
 * BuildConfig / xcconfig 와 달리 컴파일 없이 한 곳만 바꾸면 됨.
 *
 * 본 객체 자체가 SSOT — 향후 디버그 메뉴(긴 탭 등)에서 동적 변경하려면 mutable 로 바꾸고
 * remember/snapshotFlow 로 관찰. 지금은 컴파일타임 상수만 필요.
 */
object RuntimeFlags {
    /** Timeline 의 SoundDeck (카드 스택) 노출. 기존 AudioSeparationSheet 과 병행. */
    const val soundDeckEnabled: Boolean = true

    /**
     * Timeline stepper UI 숨김. 자막/더빙 제거 후 단계가 하나뿐이라 항상 true.
     */
    const val stepperHidden: Boolean = true

    /**
     * 인앱 크레딧 구매 (IAP) 진입점 노출.
     *
     * **현재 false — 무료 선출시 모드.** IAP 진입점(InputScreen 크레딧 칩, UserMenu 크레딧/구매,
     * 잔액부족 "Buy credits")이 숨겨지고, 잔액 소진 화면은 "I want this" 수요표현 탭
     * (→ 컨페티 + `BffApi.recordPaidCreditIntent`)으로 대체된다.
     *
     * true 로 켜면 결제 오픈 — 모든 IAP 진입점이 노출되고 `PurchaseLauncher` 가
     * StoreKit2(iOS) / Play Billing 7.x(Android) 실연동으로 동작한다.
     */
    const val iapEnabled: Boolean = false
}
