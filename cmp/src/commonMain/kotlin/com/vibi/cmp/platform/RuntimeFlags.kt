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
     * Timeline 의 3단계 stepper UI 와 단계별 분기 숨김 — AudioSources + SubtitleDub 을
     * 한 스크롤로 노출. Edit 단계 자동 진입도 비활성. true 가 새 디자인 기본.
     */
    const val stepperHidden: Boolean = true

    /**
     * 인앱 크레딧 구매 (IAP) 진입점 노출.
     *
     * **출시 빌드 주의**: `PurchaseLauncher` 가 아직 StoreKit / Play Billing 미연동 mock 이라
     * 실제 결제창 안 뜬 채로 App Store 제출하면 가이드라인 2.1 (incomplete) / 3.1.1
     * (외부 결제 의심) 으로 reject. 데모/내부 테스트에선 true, 심사 빌드 직전에 false 로 토글.
     */
    const val iapEnabled: Boolean = true
}
