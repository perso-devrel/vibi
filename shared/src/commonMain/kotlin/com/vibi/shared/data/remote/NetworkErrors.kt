package com.vibi.shared.data.remote

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

/**
 * BFF 호출이 401 을 반환(액세스 토큰 만료/무효)했을 때 던진다. 일반 네트워크 실패·5xx 와
 * 구분해 호출자가 "세션 만료 → 다시 로그인" 으로 분기할 수 있게 한다. refresh 토큰이 없어
 * 재인증이 유일한 복구라, 장시간 폴링 중 만료를 무한 재시도하지 않고 즉시 종료시키는 신호.
 */
class SessionExpiredException : Exception("session_expired")

/** 401 Unauthorized 여부 — expectSuccess 가 던진 ClientRequestException 의 status 로 판정. */
fun Throwable.isUnauthorized(): Boolean =
    this is ClientRequestException && response.status == HttpStatusCode.Unauthorized

/**
 * 장시간 폴링 루프가 단발 네트워크 블립으로 전체 잡을 실패시키지 않도록, 연속 실패를 이 횟수까지
 * 백오프하며 허용한다(초과 시 비로소 throw). HTTP 레이어의 HttpRequestRetry(요청당 재시도)와
 * 합쳐 일시 끊김/서버 cold-start 5xx 를 견딘다.
 */
const val MAX_CONSECUTIVE_POLL_FAILURES = 5
