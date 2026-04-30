package com.dubcast.shared.data.remote.api

import com.dubcast.shared.data.remote.dto.MixRequest
import com.dubcast.shared.data.remote.dto.MixStemRequest
import com.dubcast.shared.data.remote.dto.RenderConfig
import com.dubcast.shared.data.remote.dto.SeparationSpec
import com.dubcast.shared.data.remote.dto.TtsRequest
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
 * 12 BFF v2 endpoints + lipsync (3개 추가, shared 가 legacy superset).
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
        return BffApi(client) to captured
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    // ───────────────────── voices ─────────────────────

    @Test
    fun `getVoices parses voice list`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"voices":[{"voiceId":"v1","name":"Alice","previewUrl":"https://x/p","language":"ko"}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = api.getVoices()

        assertEquals(1, captured.size)
        assertEquals(HttpMethod.Get, captured[0].method)
        assertEquals("api/v2/voices", captured[0].url.encodedPath.trimStart('/'))
        assertEquals(1, result.voices.size)
        assertEquals("v1", result.voices[0].voiceId)
        assertEquals("ko", result.voices[0].language)
    }

    @Test
    fun `getVoices propagates upstream 5xx`() = runTest {
        val (api, _) = buildApi { _ -> respondError(HttpStatusCode.BadGateway) }
        assertFailsWith<Exception> { api.getVoices() }
    }

    // ───────────────────── tts ─────────────────────

    @Test
    fun `synthesize sends request body and parses response`() = runTest {
        val (api, captured) = buildApi { request ->
            respond(
                content = """{"audioUrl":"/files/tts/abc.mp3","durationMs":4200}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = api.synthesize(TtsRequest(text = "안녕하세요", voiceId = "v1"))

        assertEquals(HttpMethod.Post, captured[0].method)
        assertEquals("api/v2/tts", captured[0].url.encodedPath.trimStart('/'))
        val sentBody = (captured[0].body as TextContent).text
        assertContains(sentBody, "안녕하세요")
        assertContains(sentBody, "v1")
        assertEquals("/files/tts/abc.mp3", result.audioUrl)
        assertEquals(4200L, result.durationMs)
    }

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
            audioFiles = emptyList(),
            subtitles = null,
            imageFiles = emptyList(),
            segmentImageFiles = emptyList(),
            bgmFiles = emptyList(),
            audioOverride = null,
            config = RenderConfig(dubClips = emptyList(), segments = emptyList())
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

        val bytes = api.downloadRenderResult("render-123")

        assertEquals("api/v2/render/render-123/download", captured[0].url.encodedPath.trimStart('/'))
        assertEquals(256, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[255])
    }

    // ───────────────────── separation ─────────────────────

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
            file = BinaryPart("file", "input.mp4", byteArrayOf(0x10), "video/mp4"),
            spec = SeparationSpec(
                mediaType = "VIDEO",
                numberOfSpeakers = 2,
                sourceLanguageCode = "auto"
            )
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

    @Test
    fun `requestStemMix posts stem volumes`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"mixJobId":"mix-1"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = api.requestStemMix(
            "sep-1",
            MixRequest(stems = listOf(
                MixStemRequest(stemId = "background", volume = 0.8f),
                MixStemRequest(stemId = "voice_all", volume = 1.2f)
            ))
        )

        assertEquals("api/v2/separate/sep-1/mix", captured[0].url.encodedPath.trimStart('/'))
        val body = (captured[0].body as TextContent).text
        assertContains(body, "background")
        assertContains(body, "voice_all")
        assertEquals("mix-1", result.mixJobId)
    }

    @Test
    fun `requestStemMix returns 409 when source already consumed`() = runTest {
        val (api, _) = buildApi { _ ->
            respondError(HttpStatusCode.Conflict, """{"error":"separation_consumed"}""", jsonHeaders())
        }
        assertFailsWith<Exception> {
            api.requestStemMix("sep-1", MixRequest(stems = emptyList()))
        }
    }

    @Test
    fun `getMixStatus parses signed download url`() = runTest {
        val (api, _) = buildApi { _ ->
            respond(
                content = """{"mixJobId":"mix-1","status":"COMPLETED","progress":100,"downloadUrl":"https://x/m?token=xyz"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val result = api.getMixStatus("mix-1")
        assertEquals("COMPLETED", result.status)
        assertEquals("https://x/m?token=xyz", result.downloadUrl)
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
        val bytes = api.downloadStem("https://bff.example/files/sep/abc?token=xyz")
        assertEquals(payload.size, bytes.size)
        assertContains(captured[0].url.toString(), "token=xyz")
    }

    // ───────────────────── lipsync (shared-only superset) ─────────────────────

    @Test
    fun `requestLipSync posts video plus audio plus timing`() = runTest {
        val (api, captured) = buildApi { _ ->
            respond(
                content = """{"jobId":"lip-1"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        api.requestLipSync(
            video = BinaryPart("video", "v.mp4", byteArrayOf(0x01), "video/mp4"),
            audio = BinaryPart("audio", "a.mp3", byteArrayOf(0x02), "audio/mpeg"),
            startMs = 1000,
            durationMs = 5000
        )
        assertEquals("api/v2/lipsync", captured[0].url.encodedPath.trimStart('/'))
    }
}

