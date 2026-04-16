package com.example.dubcast.data.repository

import android.content.Context
import com.example.dubcast.BuildConfig
import com.example.dubcast.data.remote.api.BffApiService
import com.example.dubcast.data.remote.dto.TtsRequest
import com.example.dubcast.domain.model.Voice
import com.example.dubcast.domain.repository.TtsRepository
import com.example.dubcast.domain.repository.TtsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.UUID
import javax.inject.Inject

class TtsRepositoryImpl @Inject constructor(
    private val apiService: BffApiService,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : TtsRepository {

    override suspend fun getVoices(): Result<List<Voice>> {
        return runCatching {
            val response = apiService.getVoices()
            response.voices.map { dto ->
                Voice(
                    voiceId = dto.voiceId,
                    name = dto.name,
                    previewUrl = dto.previewUrl,
                    language = dto.language
                )
            }
        }
    }

    override suspend fun synthesize(text: String, voiceId: String): Result<TtsResult> {
        return runCatching {
            val response = apiService.synthesize(TtsRequest(text, voiceId))
            val audioDir = File(context.filesDir, "dub_audio")
            if (!audioDir.exists()) audioDir.mkdirs()
            val audioFile = File(audioDir, "${UUID.randomUUID()}.mp3")

            // Rewrite localhost URLs to match BFF_BASE_URL host for emulator compatibility
            val audioUrl = rewriteLocalhostUrl(response.audioUrl)

            // Download audio on IO dispatcher to avoid NetworkOnMainThreadException
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(audioUrl).build()
                okHttpClient.newCall(request).execute().use { httpResponse ->
                    if (!httpResponse.isSuccessful) {
                        throw java.io.IOException("Audio download failed: HTTP ${httpResponse.code}")
                    }
                    val body = httpResponse.body
                        ?: throw java.io.IOException("Empty response body for audio download")
                    body.byteStream().use { input ->
                        audioFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            if (audioFile.length() == 0L) {
                audioFile.delete()
                throw java.io.IOException("Downloaded audio file is empty")
            }

            TtsResult(
                localAudioPath = audioFile.absolutePath,
                durationMs = response.durationMs
            )
        }
    }

    /**
     * BFF may return URLs with localhost/127.0.0.1 which are unreachable from the emulator.
     * Rewrite them to use the same host as BFF_BASE_URL.
     */
    private fun rewriteLocalhostUrl(url: String): String {
        val uri = URI.create(url)
        val host = uri.host ?: return url
        if (host == "localhost" || host == "127.0.0.1") {
            val bffUri = URI.create(BuildConfig.BFF_BASE_URL)
            val newPort = if (bffUri.port > 0) bffUri.port else uri.port
            return URI(uri.scheme, uri.userInfo, bffUri.host, newPort,
                uri.path, uri.query, uri.fragment).toString()
        }
        return url
    }
}
