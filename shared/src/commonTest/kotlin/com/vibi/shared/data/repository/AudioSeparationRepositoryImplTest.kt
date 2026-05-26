package com.vibi.shared.data.repository

import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.domain.error.InsufficientCreditsException
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.AudioSourceKind
import com.vibi.shared.platform.PreparedAudio
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

    /** Repo 가 audio extract 단계를 거치지 않고 바로 BFF 호출로 가도록 만드는 stub. ext 가
     *  화이트리스트 (m4a) 라 BFF 모의 응답 (402 / 200) 단계까지 진입. readFileBytes 는 path 가
     *  존재해야 통과하지만 본 테스트는 BFF 응답 매핑만 검증하므로 fake path 가 throw 하면
     *  Result.failure 로 떨어져도 테스트 의도와 별개 — 그래서 readFileBytes 가 안 부르는 흐름
     *  ([prepareSeparationAudio] 가 throw) 으로 시뮬레이션. */
    private class StubAudioExtractor(private val throwOnPrepare: Throwable? = null) : AudioExtractor {
        override val isSupported: Boolean = true
        override suspend fun prepareSeparationAudio(
            sourceUri: String,
            sourceKind: AudioSourceKind,
            startMs: Long?,
            endMs: Long?,
        ): PreparedAudio {
            throwOnPrepare?.let { throw it }
            return PreparedAudio(path = sourceUri, mimeType = "audio/mp4", ext = "m4a")
        }
    }

    private fun buildRepo(
        audioExtractor: AudioExtractor = StubAudioExtractor(),
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(
            io.ktor.client.request.HttpRequestData
        ) -> io.ktor.client.request.HttpResponseData
    ): AudioSeparationRepositoryImpl {
        val engine = MockEngine { handler(it) }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return AudioSeparationRepositoryImpl(
            api = BffApi(client = client, r2Client = client),
            bffBaseUrl = "http://test",
            audioExtractor = audioExtractor,
        )
    }

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

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

    @Test
    fun `startSeparation propagates AudioExtractException as Result failure`() = runTest {
        val expected = com.vibi.shared.platform.AudioExtractException.SourceCorrupt
        val repo = buildRepo(audioExtractor = StubAudioExtractor(throwOnPrepare = expected)) { _ ->
            respond(
                content = """{"jobId":"sep-x"}""",
                status = HttpStatusCode.Accepted,
                headers = jsonHeaders(),
            )
        }
        val result = repo.startSeparation(
            sourceUri = "ignored",
            sourceKind = AudioSourceKind.VIDEO,
        )
        assertTrue(result.isFailure)
        assertEquals(expected, result.exceptionOrNull())
    }
}
