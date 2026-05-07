package com.dubcast.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/**
 * @param tokenProvider 매 요청 시 호출되어 Authorization 헤더에 박을 JWT 를 반환. null 이면 헤더 생략.
 *   v1 은 보호된 라우트가 없어 effectively no-op 이지만 향후 확장 대비 hook 을 미리 박아둠.
 */
fun createBffHttpClient(
    baseUrl: String,
    enableLogging: Boolean = true,
    tokenProvider: () -> String? = { null },
): HttpClient =
    createPlatformHttpClient {
        expectSuccess = true

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
            connectTimeoutMillis = 15_000L
            socketTimeoutMillis = 300_000L
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
