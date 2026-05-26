package com.vibi.shared.domain.error

/**
 * BFF `/separate` 가 402 (Payment Required) + `insufficient_credits` 로 응답했을 때
 * 리포지토리가 던지는 typed exception. ViewModel 이 `Result.failure(cause)` 의 cause 타입으로
 * 판별해 "잔액 부족 → 충전 화면" UI 로 분기한다.
 *
 * 일반 네트워크/서버 에러와 분리한 이유: 사용자에겐 "이상한 에러" 가 아니라 정확한 결제 안내가
 * 가야 하고, 모바일은 재시도 대신 충전 흐름을 띄워야 한다 (retry-able 에러와 구분).
 *
 * @param required 이번 분리 잡에 필요한 크레딧 수.
 * @param balance  402 발생 시점의 BFF 측 권위 잔액. UI 가 즉시 표시 + `CreditStore` 동기화에 사용.
 */
class InsufficientCreditsException(
    val required: Int,
    val balance: Int,
) : RuntimeException("insufficient_credits: required=$required balance=$balance")
