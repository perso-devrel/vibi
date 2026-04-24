package com.example.dubcast.data.remote.api

import com.example.dubcast.data.remote.dto.AutoDubJobResponse
import com.example.dubcast.data.remote.dto.AutoDubStatusResponse
import com.example.dubcast.data.remote.dto.MixJobResponse
import com.example.dubcast.data.remote.dto.MixRequest
import com.example.dubcast.data.remote.dto.MixStatusResponse
import com.example.dubcast.data.remote.dto.RenderJobResponse
import com.example.dubcast.data.remote.dto.RenderStatusResponse
import com.example.dubcast.data.remote.dto.SeparationJobResponse
import com.example.dubcast.data.remote.dto.SeparationStatusResponse
import com.example.dubcast.data.remote.dto.SubtitleJobResponse
import com.example.dubcast.data.remote.dto.SubtitleStatusResponse
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
import retrofit2.http.Url

interface BffApiService {

    @GET("api/v2/voices")
    suspend fun getVoices(): VoiceListResponse

    @POST("api/v2/tts")
    suspend fun synthesize(@Body request: TtsRequest): TtsResponse

    // Render (server-side FFmpeg)

    @Multipart
    @POST("api/v2/render")
    suspend fun submitRenderJob(
        @Part videoFiles: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part audioFiles: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part subtitles: MultipartBody.Part?,
        @Part imageFiles: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part segmentImageFiles: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part bgmFiles: List<@JvmSuppressWildcards MultipartBody.Part>,
        @Part audioOverride: MultipartBody.Part?,
        @Part("config") config: RequestBody
    ): RenderJobResponse

    @GET("api/v2/render/{jobId}/status")
    suspend fun getRenderStatus(@Path("jobId") jobId: String): RenderStatusResponse

    @Streaming
    @GET("api/v2/render/{jobId}/download")
    suspend fun downloadRenderResult(@Path("jobId") jobId: String): ResponseBody

    // Audio separation

    @Multipart
    @POST("api/v2/separate")
    suspend fun startSeparation(
        @Part file: MultipartBody.Part,
        @Part("spec") spec: RequestBody
    ): SeparationJobResponse

    @GET("api/v2/separate/{jobId}")
    suspend fun getSeparationStatus(@Path("jobId") jobId: String): SeparationStatusResponse

    @Streaming
    @GET
    suspend fun downloadStem(@Url tokenizedUrl: String): ResponseBody

    @POST("api/v2/separate/{jobId}/mix")
    suspend fun requestStemMix(
        @Path("jobId") jobId: String,
        @Body body: MixRequest
    ): MixJobResponse

    @GET("api/v2/separate/mix/{mixJobId}")
    suspend fun getMixStatus(@Path("mixJobId") mixJobId: String): MixStatusResponse

    @Streaming
    @GET
    suspend fun downloadMix(@Url tokenizedUrl: String): ResponseBody

    // Auto subtitles (Perso STT + Gemini translate)

    @Multipart
    @POST("api/v2/subtitles")
    suspend fun submitSubtitleJob(
        @Part file: MultipartBody.Part,
        @Part("spec") spec: RequestBody
    ): SubtitleJobResponse

    @GET("api/v2/subtitles/{jobId}")
    suspend fun getSubtitleStatus(@Path("jobId") jobId: String): SubtitleStatusResponse

    @Streaming
    @GET
    suspend fun downloadSrt(@Url tokenizedUrl: String): ResponseBody

    // Auto dubbing (Perso translate, no lipsync)

    @Multipart
    @POST("api/v2/autodub")
    suspend fun submitAutoDubJob(
        @Part file: MultipartBody.Part,
        @Part("spec") spec: RequestBody
    ): AutoDubJobResponse

    @GET("api/v2/autodub/{jobId}")
    suspend fun getAutoDubStatus(@Path("jobId") jobId: String): AutoDubStatusResponse

    @Streaming
    @GET
    suspend fun downloadDubbedAudio(@Url tokenizedUrl: String): ResponseBody
}
