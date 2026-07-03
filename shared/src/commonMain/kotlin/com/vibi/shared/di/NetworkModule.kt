package com.vibi.shared.di

import com.vibi.shared.data.local.AuthEventBus
import com.vibi.shared.data.local.AuthTokenStore
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.createBffHttpClient
import com.vibi.shared.data.remote.createR2HttpClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

val BffBaseUrlKey = named("bffBaseUrl")
private val R2ClientKey = named("r2Client")

val networkModule = module {
    // 세션 만료 이벤트 버스 — HTTP 레이어(생산) ↔ 내비게이션(소비) 공유.
    single { AuthEventBus() }
    single<HttpClient> {
        val baseUrl = getProperty<String>("bffBaseUrl")
        val tokenStore = get<AuthTokenStore>()
        val authEventBus = get<AuthEventBus>()
        createBffHttpClient(
            baseUrl = baseUrl,
            tokenProvider = { tokenStore.getValidToken() },
            // 401(만료) → 로컬 토큰 폐기 후 만료 신호. 인메모리 캐시까지 clear 되어 이후 요청은
            // 무토큰 → hasValidSession=false. UI 가 로그인으로 라우팅한다.
            onUnauthorized = {
                tokenStore.clear()
                authEventBus.notifySessionExpired()
            },
        )
    }
    // R2 presigned PUT 전용 — auth/baseUrl default 없음. BFF client 와 graph 상 분리.
    single<HttpClient>(R2ClientKey) { createR2HttpClient() }
    single { BffApi(client = get(), r2Client = get(R2ClientKey)) }
}
