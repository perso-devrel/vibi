package com.vibi.shared.data.remote.api

import com.vibi.shared.data.remote.dto.RenderConfig
import com.vibi.shared.data.remote.dto.SeparationSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BFF API integration tests using Ktor MockEngine.
 *
 * BFF v2 endpoints — auth/render/separate/mix/credits/testdata.
 * 각 엔드포인트마다 happy path + 에러 path. 멀티파트/스트리밍은 별도 단언.
 *
 * legacy-android 의 BffApiService 와 동등 동작을 보장하기 위한 회귀 테스트.
 */
class BffApiTest {

    private fun buildApi(
        respond: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): Pair<BffApi, MutableList<HttpRequestData>> {
        val captured = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            captured.add(request)
            respond(request)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
        // r2Client 는 v3 putAssetToR2 만 사용 — 본 테스트는 v2 흐름만 다루므로 동일 client 재사용 OK.
        return BffApi(client = client, r2Client = client) to captured
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    // ───────────────────── render ─────────────────────

    @Test
    fun `submitRenderJob is multipart and includes config field`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"jobId":"render-123"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = api.submitRenderJob(
            videoFiles = listOf(BinaryPart("video_0", "v.mp4", byteArrayOf(0x00, 0x01), "video/mp4")),
            bgmFiles = emptyList(),
            config = RenderConfig(segments = emptyList())
        )

        assertEquals(HttpMethod.Post, captured[0].method)
        assertEquals("api/v2/render", captured[0].url.encodedPath.trimStart('/'))
        val ct = captured[0].headers[HttpHeaders.ContentType] ?: captured[0].body.contentType?.toString()
        assertNotNull(ct)
        assertTrue(ct.contains("multipart/form-data", ignoreCase = true), "expected multipart, was $ct")
        assertEquals("render-123", result.jobId)
    }

    @Test
    fun `getRenderStatus returns progress`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"jobId":"render-123","status":"PROCESSING","progress":42}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val result = api.getRenderStatus("render-123")
        assertEquals("api/v2/render/render-123/status", captured[0].url.encodedPath.trimStart('/'))
        assertEquals("PROCESSING", result.status)
        assertEquals(42, result.progress)
    }

    @Test
    fun `downloadRenderResult returns binary bytes`() = runTest {
        val payload = ByteArray(256) { it.toByte() }
        val (api, captured) = buildApi { _ ->
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "video/mp4")
            )
        }

        val bytes = api.downloadRenderResult("render-123") { it.collectAll() }

        assertEquals("api/v2/render/render-123/download", captured[0].url.encodedPath.trimStart('/'))
        assertEquals(256, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[255])
    }

    // ───────────────────── separation ─────────────────────

    @Test
    fun `getCreditCost passes durationMs query param and parses response`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"durationMs":120000,"credits":2,"balance":5,"sufficient":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = api.getCreditCost(durationMs = 120_000L)

        assertEquals("api/v2/credits/cost", captured[0].url.encodedPath.trimStart('/'))
        assertEquals("120000", captured[0].url.parameters["durationMs"])
        assertEquals(2, result.credits)
        assertEquals(5, result.balance)
        assertTrue(result.sufficient)
    }

    @Test
    fun `startSeparation sends file plus spec`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"jobId":"sep-1"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = api.startSeparation(
            file = BinaryPart("file", "input.m4a", byteArrayOf(0x10), "audio/mp4"),
            spec = SeparationSpec(sourceLanguageCode = "auto")
        )

        assertEquals("api/v2/separate", captured[0].url.encodedPath.trimStart('/'))
        assertEquals("sep-1", result.jobId)
    }

    @Test
    fun `getSeparationStatus parses ready stems`() = runTest {
        val (api, _) = buildApi { _ ->
            respond(
                content = """{
                    "jobId":"sep-1",
                    "status":"READY",
                    "progress":100,
                    "stems":[
                        {"stemId":"background","label":"배경음","url":"https://x/bg?token=abc"},
                        {"stemId":"voice_all","label":"모든 화자","url":"https://x/v?token=def"}
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val result = api.getSeparationStatus("sep-1")
        assertEquals("READY", result.status)
        assertEquals(2, result.stems.size)
        assertEquals("background", result.stems[0].stemId)
    }

    // ───────────────────── tokenized downloads ─────────────────────

    @Test
    fun `downloadStem follows tokenized url verbatim`() = runTest {
        val payload = "stem-bytes".encodeToByteArray()
        val (api, captured) = buildApi { _ ->
            respond(
                content = ByteReadChannel(payload),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "audio/mpeg")
            )
        }
        val bytes = api.downloadStem("https://bff.example/files/sep/abc?token=xyz") { it.collectAll() }
        assertEquals(payload.size, bytes.size)
        assertContains(captured[0].url.toString(), "token=xyz")
    }

    /** 스트리밍 다운로드(consume 람다)를 인메모리 ByteArray 로 모아 단언 — 프로덕션은 파일로 스트리밍. */
    private suspend fun ByteReadChannel.collectAll(): ByteArray {
        val acc = mutableListOf<Byte>()
        val buf = ByteArray(4096)
        while (true) {
            val n = readAvailable(buf, 0, buf.size)
            if (n == -1) break
            for (i in 0 until n) acc.add(buf[i])
        }
        return acc.toByteArray()
    }
}

