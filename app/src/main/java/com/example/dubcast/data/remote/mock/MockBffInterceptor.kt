package com.example.dubcast.data.remote.mock

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockBffInterceptor @Inject constructor() : Interceptor {

    private val lipSyncCounters = ConcurrentHashMap<String, Int>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.encodedPath
        val method = request.method

        val (code, json) = when {
            method == "GET" && url.endsWith("/v2/voices") -> {
                200 to mockVoicesResponse()
            }

            method == "POST" && url.endsWith("/v2/tts") -> {
                200 to """{"audioUrl":"mock://tts_audio_${System.currentTimeMillis()}.mp3","durationMs":3000}"""
            }

            method == "POST" && url.endsWith("/v2/lipsync") -> {
                val jobId = "mock-lipsync-${System.currentTimeMillis()}"
                200 to """{"jobId":"$jobId"}"""
            }

            method == "GET" && url.contains("/v2/lipsync/") && url.endsWith("/status") -> {
                val jobId = url.substringAfter("/v2/lipsync/").substringBefore("/status")
                val counter = lipSyncCounters.getOrDefault(jobId, 0) + 1
                lipSyncCounters[jobId] = counter
                val (status, progress, resultUrl) = when {
                    counter <= 2 -> Triple("PROCESSING", counter * 30, "null")
                    else -> {
                        lipSyncCounters.remove(jobId)
                        Triple("COMPLETED", 100, "\"mock://lipsync_result.mp4\"")
                    }
                }
                200 to """{"jobId":"$jobId","status":"$status","progress":$progress,"resultVideoUrl":$resultUrl}"""
            }

            method == "GET" && url.contains("/v2/lipsync/") && url.endsWith("/download") -> {
                200 to """{"url":"mock://lipsync_result.mp4"}"""
            }

            else -> {
                404 to """{"error":"Not found"}"""
            }
        }

        val responseBody = json.toResponseBody("application/json".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Not Found")
            .body(responseBody)
            .build()
    }

    private fun mockVoicesResponse(): String {
        return """
        {
          "voices": [
            {"voiceId":"EXAVITQu4vr4xnSDxMaL","name":"Sarah","previewUrl":null,"language":"en"},
            {"voiceId":"TX3LPaxmHKxFdv7VOQHJ","name":"Liam","previewUrl":null,"language":"en"},
            {"voiceId":"pFZP5JQG7iQjIQuC4Bku","name":"Lily","previewUrl":null,"language":"en"},
            {"voiceId":"bIHbv24MWmeRgasZH58o","name":"Will","previewUrl":null,"language":"en"},
            {"voiceId":"mock-korean-1","name":"지민","previewUrl":null,"language":"ko"},
            {"voiceId":"mock-korean-2","name":"서연","previewUrl":null,"language":"ko"}
          ]
        }
        """.trimIndent()
    }
}
