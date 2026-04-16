package com.example.dubcast.data.remote.api

import com.example.dubcast.data.remote.dto.LipSyncResponse
import com.example.dubcast.data.remote.dto.LipSyncStatusResponse
import com.example.dubcast.data.remote.dto.RenderJobResponse
import com.example.dubcast.data.remote.dto.RenderStatusResponse
import com.example.dubcast.data.remote.dto.TtsRequest
import com.example.dubcast.data.remote.dto.TtsResponse
import com.example.dubcast.data.remote.dto.VoiceListResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface BffApiService {

    @GET("api/v2/voices")
    suspend fun getVoices(): VoiceListResponse

    @POST("api/v2/tts")
    suspend fun synthesize(@Body request: TtsRequest): TtsResponse

    @Multipart
    @POST("api/v2/lipsync")
    suspend fun requestLipSync(
        @Part video: MultipartBody.Part,
        @Part audio: MultipartBody.Part,
        @Part("startMs") startMs: RequestBody,
        @Part("durationMs") durationMs: RequestBody
    ): LipSyncResponse

    @GET("api/v2/lipsync/{jobId}/status")
    suspend fun getLipSyncStatus(@Path("jobId") jobId: String): LipSyncStatusResponse

    @GET("api/v2/lipsync/{jobId}/download")
    suspend fun downloadLipSyncResult(@Path("jobId") jobId: String): ResponseBody

    // Render (server-side FFmpeg)

    @Multipart
    @POST("api/v2/render")
    suspend fun submitRenderJob(
        @Part video: MultipartBody.Part,
        @Part audioFiles: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part subtitles: MultipartBody.Part?,
        @Part("config") config: RequestBody
    ): RenderJobResponse

    @GET("api/v2/render/{jobId}/status")
    suspend fun getRenderStatus(@Path("jobId") jobId: String): RenderStatusResponse

    @Streaming
    @GET("api/v2/render/{jobId}/download")
    suspend fun downloadRenderResult(@Path("jobId") jobId: String): ResponseBody
}
