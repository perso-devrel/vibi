package com.vibi.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) {
        block()
        engine {
            // NSURLSession 기본 waitsForConnectivity=true 는 오프라인 시 요청을 즉시 실패시키지 않고
            // 연결 복귀를 requestTimeout(5분) 까지 기다린다 → 음원분리 시작이 "0% Preparing" 에서
            // 멈춰 보임. false 로 두면 오프라인/서버 unreachable 시 곧바로 실패 → ViewModel 이
            // handleSeparationFailure 로 사용자에게 재시도 안내. (HttpTimeout.connectTimeoutMillis 는
            // Darwin 엔진이 적용하지 않아 이 설정이 실질적 fail-fast 스위치.)
            configureSession {
                setWaitsForConnectivity(false)
                // Darwin 은 HttpTimeout.connectTimeoutMillis 를 적용하지 않아, '서버 도달하나
                // 무응답'(과부하·LAN 전환) 시 requestTimeout(5분)까지 hang. 요청 비활동 타임아웃을
                // 60s 로 박아 fail-fast — 활성 전송(대용량 업/다운로드)은 데이터 수신마다 리셋되어
                // 끊기지 않고, 무응답만 60s 안에 깬다.
                setTimeoutIntervalForRequest(60.0)
            }
        }
    }
