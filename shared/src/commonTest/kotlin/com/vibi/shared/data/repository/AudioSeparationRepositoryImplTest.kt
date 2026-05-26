package com.vibi.shared.data.repository

import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.domain.error.InsufficientCreditsException
import com.vibi.shared.domain.model.SeparationMediaType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * AudioSeparationRepositoryImpl 의 BFF 응답 매핑 회귀 — 특히 402 `insufficient_credits` 가
 * typed [InsufficientCreditsException] 으로 변환되는지. 모바일이 잔액 부족을 일반 네트워크
 * 에러와 구분해 충전 UI 로 분기시키는 핵심 invariant.
 */
class AudioSeparationRepositoryImplTest {

    private fun buildRepo(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
            io.ktor.client.request.HttpRequestData
        ) -> io.ktor.client.request.HttpResponseData
    ): AudioSeparationRepositoryImpl {
        val engine = MockEngine { handler(it) }
        val client = HttpClient(engine) {
            // production HttpClientFactory 와 동일 — 4xx/5xx 에서 ClientRequestException
            // 으로 throw 해야 Repository 의 catch 가 작동.
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return AudioSeparationRepositoryImpl(
            api = BffApi(client),
            bffBaseUrl = "http://test",
            // creditStore / userSession 미주입 — 매핑 자체는 의존하지 않음 (side-effect 만).
        )
    }

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `startSeparation 402 insufficient_credits maps to InsufficientCreditsException`() = runTest {
        val repo = buildRepo { _ ->
            respond(
                content = """{"error":"insufficient_credits","detail":"required=2 balance=1"}""",
                status = HttpStatusCode.PaymentRequired,
                headers = jsonHeaders(),
            )
        }

        val result = repo.startSeparation(
            sourceUri = "ignored",
            mediaType = SeparationMediaType.AUDIO,
            numberOfSpeakers = 2,
            editedRenderJobId = "render-x",  // null 이면 readFileBytes 호출 — render path 로 우회.
        )

        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertIs<InsufficientCreditsException>(cause)
        assertEquals(2, cause.required)
        assertEquals(1, cause.balance)
    }

    @Test
    fun `startSeparation 402 with malformed detail still throws InsufficientCredits with zeros`() = runTest {
        // BFF detail 형식이 미래에 바뀌어도 402+insufficient_credits 이면 일단 typed 예외로 전환 —
        // 모바일이 generic 네트워크 에러 분기로 떨어져 충전 UI 를 놓치는 회귀 방지.
        val repo = buildRepo { _ ->
            respond(
                content = """{"error":"insufficient_credits","detail":"unparseable"}""",
                status = HttpStatusCode.PaymentRequired,
                headers = jsonHeaders(),
            )
        }
        val result = repo.startSeparation(
            sourceUri = "ignored",
            mediaType = SeparationMediaType.AUDIO,
            numberOfSpeakers = 2,
            editedRenderJobId = "render-x",
        )
        val cause = result.exceptionOrNull()
        assertIs<InsufficientCreditsException>(cause)
        assertEquals(0, cause.required)
        assertEquals(0, cause.balance)
    }

    @Test
    fun `startSeparation 402 with different error code is not mapped`() = runTest {
        // 다른 402 error (가상 — 미래에 다른 결제 에러가 추가될 가능성) 는 typed 변환 안 함.
        val repo = buildRepo { _ ->
            respond(
                content = """{"error":"some_other_402"}""",
                status = HttpStatusCode.PaymentRequired,
                headers = jsonHeaders(),
            )
        }
        val result = repo.startSeparation(
            sourceUri = "ignored",
            mediaType = SeparationMediaType.AUDIO,
            numberOfSpeakers = 2,
            editedRenderJobId = "render-x",
        )
        val cause = result.exceptionOrNull()
        assertTrue(cause != null && cause !is InsufficientCreditsException)
    }

    @Test
    fun `getCost returns SeparationCost with sufficient flag`() = runTest {
        val repo = buildRepo { _ ->
            respond(
                content = """{"durationMs":60000,"credits":1,"balance":3,"sufficient":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val result = repo.getCost(60_000L)
        assertTrue(result.isSuccess)
        val cost = result.getOrNull()!!
        assertEquals(1, cost.credits)
        assertEquals(3, cost.balance)
        assertTrue(cost.sufficient)
    }
}
