package com.example.dubcast.data.remote.api

import com.example.dubcast.data.remote.dto.LipSyncResponse
import com.example.dubcast.data.remote.dto.LipSyncStatusResponse
import com.example.dubcast.data.remote.dto.TtsRequest
import com.example.dubcast.data.remote.dto.TtsResponse
import com.example.dubcast.data.remote.dto.VoiceListResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

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
        @Part("startMs") startMs: Long,
        @Part("durationMs") durationMs: Long
    ): LipSyncResponse

    @GET("api/v2/lipsync/{jobId}/status")
    suspend fun getLipSyncStatus(@Path("jobId") jobId: String): LipSyncStatusResponse

    @GET("api/v2/lipsync/{jobId}/download")
    suspend fun downloadLipSyncResult(@Path("jobId") jobId: String): ResponseBody
}
