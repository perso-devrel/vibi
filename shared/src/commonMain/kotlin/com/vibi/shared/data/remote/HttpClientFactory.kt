package com.vibi.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * @param tokenProvider 매 요청 시 호출되어 Authorization 헤더에 박을 JWT 를 반환. null 이면 헤더 생략.
 * @param onUnauthorized 인증 요청(auth 엔드포인트 제외)의 401 시 1회 호출 — 세션 만료 신호(상세는 아래 구현 주석).
 */
fun createBffHttpClient(
    baseUrl: String,
    // Ktor INFO 가 매 요청마다 [Ktor] METHOD URL 한 줄 + 헤더 다수 → 시뮬레이터 콘솔 도배.
    // dev 시 트래픽 검증 필요한 호출자만 명시적으로 true.
    enableLogging: Boolean = false,
    tokenProvider: () -> String? = { null },
    onUnauthorized: () -> Unit = {},
): HttpClient =
    createPlatformHttpClient {
        expectSuccess = true

        // 인증 요청의 401 을 전역 포착 → 세션 만료 신호. auth 엔드포인트(로그인 교환·탈퇴) 자체의
        // 401 은 기존 세션 만료가 아니라 로그인 실패이므로 제외. Ktor 는 이 핸들러 실행 후 원본
        // ClientRequestException 을 그대로 rethrow 하므로 각 호출자의 에러 처리는 그대로 유지된다.
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                if (cause.isUnauthorized() && !request.url.encodedPath.contains("/auth/")) {
                    onUnauthorized()
                }
            }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 300_000L
            // connect 는 TCP handshake 라 정상 환경이면 ms 단위. dev 시 BFF_BASE_URL 이
            // stale IP 라 닿지 않는 케이스를 5초 안에 명확히 깨도록 짧게 잡는다.
            connectTimeoutMillis = 5_000L
            socketTimeoutMillis = 300_000L
        }

        // 멱등 GET(상태 폴링·잔액·견적 등)만 일시적 실패에 자동 재시도. 비멱등 POST(렌더/분리
        // 제출, auth 교환)는 중복 잡/중복 과금/중복 로그인 위험이라 제외 — GET 만 retry.
        // GCP cold-start 5xx·일시 끊김 대비. 폴링 루프의 연속실패 허용과 2중으로 견고화.
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { request, response ->
                request.method == HttpMethod.Get && response.status.value in 500..599
            }
            retryOnExceptionIf { request, cause ->
                request.method == HttpMethod.Get &&
                    cause !is CancellationException &&
                    // 응답 상태(4xx/5xx) 예외는 retryIf 가 status 로 처리 — 여기선 연결성 예외만.
                    cause !is ResponseException
            }
            exponentialDelay()
        }

        if (enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("[Ktor] $message")
                    }
                }
                level = LogLevel.INFO
            }
        }

        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { jwt ->
                header(HttpHeaders.Authorization, "Bearer $jwt")
            }
        }
    }

/**
 * R2 presigned PUT 전용 client — baseUrl/auth/contentType default 없음. presigned URL 의
 * SigV4 는 query string 기반이라 Authorization 헤더가 있으면 R2 가 401, contentType 도
 * caller 가 sign 시점 값과 정확히 매치해야 하므로 default 박지 않음.
 *
 * [createBffHttpClient] 의 `defaultRequest { url(baseUrl) }` 와 분리해서 baseUrl 이 absolute
 * R2 URL 을 override 하는 ktor 동작 위험을 회피.
 */
fun createR2HttpClient(): HttpClient =
    createPlatformHttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            // 대용량 영상 PUT — 100MB 영상 5 Mbps 업로드면 160s, margin 포함 600s 잡음.
            requestTimeoutMillis = 600_000L
            connectTimeoutMillis = 10_000L
            socketTimeoutMillis = 600_000L
        }
    }
