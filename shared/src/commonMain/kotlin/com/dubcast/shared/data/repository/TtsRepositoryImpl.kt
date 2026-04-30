package com.dubcast.shared.data.repository

import com.dubcast.shared.data.remote.api.BffApi
import com.dubcast.shared.data.remote.dto.TtsRequest
import com.dubcast.shared.domain.model.Voice
import com.dubcast.shared.domain.repository.TtsRepository
import com.dubcast.shared.domain.repository.TtsResult
import com.dubcast.shared.platform.generateId
import com.dubcast.shared.platform.saveBytesToCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

class TtsRepositoryImpl(
    private val api: BffApi,
    private val httpClient: HttpClient,
    private val bffBaseUrl: String
) : TtsRepository {

    override suspend fun getVoices(): Result<List<Voice>> = runCatching {
        api.getVoices().voices.map { dto ->
            Voice(
                voiceId = dto.voiceId,
                name = dto.name,
                previewUrl = dto.previewUrl,
                language = dto.language
            )
        }
    }

    override suspend fun synthesize(text: String, voiceId: String): Result<TtsResult> = runCatching {
        val response = api.synthesize(TtsRequest(text = text, voiceId = voiceId))
        val audioUrl = rewriteLocalhostUrl(response.audioUrl)
        val bytes = httpClient.get(audioUrl).readRawBytes()
        check(bytes.isNotEmpty()) { "Downloaded audio file is empty" }
        val path = saveBytesToCache(fileName = "dub_${generateId()}.mp3", bytes = bytes)
        TtsResult(localAudioPath = path, durationMs = response.durationMs)
    }

    /**
     * BFF may return URLs with localhost/127.0.0.1 which are unreachable from the
     * emulator or a phone on Wi-Fi. Rewrite the host to match bffBaseUrl.
     */
    private fun rewriteLocalhostUrl(url: String): String {
        val localhostPrefixes = listOf("http://localhost", "http://127.0.0.1")
        val trimmedBase = bffBaseUrl.trimEnd('/')
        val match = localhostPrefixes.firstOrNull { url.startsWith(it) } ?: return url

        // Strip "localhost:PORT" or "127.0.0.1:PORT" up to first '/' after host
        val afterScheme = url.removePrefix("http://")
        val pathStart = afterScheme.indexOf('/')
        val pathAndQuery = if (pathStart >= 0) afterScheme.substring(pathStart) else ""
        // Use the base URL (without trailing slash) + path portion
        return "$trimmedBase$pathAndQuery"
    }
}
