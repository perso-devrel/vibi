package com.dubcast.shared.data.remote.api

import com.dubcast.shared.data.remote.dto.AutoDubJobResponse
import com.dubcast.shared.data.remote.dto.AutoDubSpec
import com.dubcast.shared.data.remote.dto.AutoDubStatusResponse
import com.dubcast.shared.data.remote.dto.LanguageListResponse
import com.dubcast.shared.data.remote.dto.LipSyncResponse
import com.dubcast.shared.data.remote.dto.LipSyncStatusResponse
import com.dubcast.shared.data.remote.dto.MixJobResponse
import com.dubcast.shared.data.remote.dto.MixRequest
import com.dubcast.shared.data.remote.dto.MixStatusResponse
import com.dubcast.shared.data.remote.dto.RenderConfig
import com.dubcast.shared.data.remote.dto.RenderInputCacheResponse
import com.dubcast.shared.data.remote.dto.RenderJobResponse
import com.dubcast.shared.data.remote.dto.RenderStatusResponse
import com.dubcast.shared.data.remote.dto.SeparationJobResponse
import com.dubcast.shared.data.remote.dto.SeparationSpec
import com.dubcast.shared.data.remote.dto.SeparationStatusResponse
import com.dubcast.shared.data.remote.dto.SubtitleJobResponse
import com.dubcast.shared.data.remote.dto.SubtitleSpec
import com.dubcast.shared.data.remote.dto.SubtitleStatusResponse
import com.dubcast.shared.data.remote.dto.TestdataSeparationFolderDto
import com.dubcast.shared.data.remote.dto.TtsRequest
import com.dubcast.shared.data.remote.dto.TtsResponse
import com.dubcast.shared.data.remote.dto.VoiceListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

data class BinaryPart(
    val fieldName: String,
    val filename: String,
    val bytes: ByteArray,
    val contentType: String
)

class BffApi(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    suspend fun getVoices(): VoiceListResponse =
        client.get("api/v2/voices").body()

    /** 지원 타깃 언어 목록 — Perso 가 지원하는 언어를 BFF 가 프록시. */
    suspend fun getLanguages(): LanguageListResponse =
        client.get("api/v2/languages").body()

    /** 음성분리 mock 데이터 — testdata/<startSec>-<endSec>/ 폴더 목록 + 그 안의 stem 이름. */
    suspend fun listSeparationTestdata(): List<TestdataSeparationFolderDto> =
        client.get("api/v2/testdata/separation/list").body()

    suspend fun synthesize(request: TtsRequest): TtsResponse =
        client.post("api/v2/tts") {
            setBody(request)
        }.body()

    suspend fun requestLipSync(
        video: BinaryPart,
        audio: BinaryPart,
        startMs: Long,
        durationMs: Long
    ): LipSyncResponse =
        client.post("api/v2/lipsync") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(video)
                        append(audio)
                        append("startMs", startMs.toString())
                        append("durationMs", durationMs.toString())
                    }
                )
            )
        }.body()

    suspend fun getLipSyncStatus(jobId: String): LipSyncStatusResponse =
        client.get("api/v2/lipsync/$jobId/status").body()

    suspend fun downloadLipSyncResult(jobId: String): ByteArray =
        client.get("api/v2/lipsync/$jobId/download").readRawBytes()

    /**
     * Multi-variant export 시 video/audios 를 한 번만 업로드하기 위한 캐시 endpoint.
     *
     * 응답 [RenderInputCacheResponse.inputId] 를 [submitRenderJob] 호출 시 `inputId` 인자로 전달하면
     * BFF 가 캐시된 video/audios 를 재사용해서 multipart 재업로드 비용을 제거한다.
     */
    suspend fun uploadRenderInputs(
        video: BinaryPart,
        audios: List<BinaryPart>,
    ): RenderInputCacheResponse =
        client.post("api/v2/render/inputs") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(video)
                        audios.forEach { append(it) }
                    }
                )
            )
        }.body()

    /**
     * @param inputId non-null 이면 BFF 가 캐시된 video/audios 를 재사용. 그 경우 [videoFiles] / [audioFiles]
     *   는 빈 list 로 보내도 됨. null 이면 기존처럼 multipart 로 업로드.
     */
    suspend fun submitRenderJob(
        videoFiles: List<BinaryPart>,
        audioFiles: List<BinaryPart>,
        subtitles: BinaryPart?,
        imageFiles: List<BinaryPart>,
        segmentImageFiles: List<BinaryPart>,
        bgmFiles: List<BinaryPart>,
        audioOverride: BinaryPart?,
        config: RenderConfig,
        inputId: String? = null,
    ): RenderJobResponse =
        client.post("api/v2/render") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        if (inputId == null) {
                            videoFiles.forEach { append(it) }
                            audioFiles.forEach { append(it) }
                        } else {
                            append("inputId", inputId)
                        }
                        subtitles?.let { append(it) }
                        imageFiles.forEach { append(it) }
                        segmentImageFiles.forEach { append(it) }
                        bgmFiles.forEach { append(it) }
                        audioOverride?.let { append(it) }
                        append("config", json.encodeToString(RenderConfig.serializer(), config))
                    }
                )
            )
        }.body()

    suspend fun getRenderStatus(jobId: String): RenderStatusResponse =
        client.get("api/v2/render/$jobId/status").body()

    suspend fun downloadRenderResult(jobId: String): ByteArray =
        client.get("api/v2/render/$jobId/download").readRawBytes()

    suspend fun startSeparation(
        file: BinaryPart,
        spec: SeparationSpec
    ): SeparationJobResponse =
        client.post("api/v2/separate") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(file)
                        append("spec", json.encodeToString(SeparationSpec.serializer(), spec))
                    }
                )
            )
        }.body()

    suspend fun getSeparationStatus(jobId: String): SeparationStatusResponse =
        client.get("api/v2/separate/$jobId").body()

    suspend fun downloadStem(tokenizedUrl: String): ByteArray =
        client.get(tokenizedUrl).readRawBytes()

    suspend fun requestStemMix(jobId: String, body: MixRequest): MixJobResponse =
        client.post("api/v2/separate/$jobId/mix") {
            setBody(body)
        }.body()

    suspend fun getMixStatus(mixJobId: String): MixStatusResponse =
        client.get("api/v2/separate/mix/$mixJobId").body()

    suspend fun downloadMix(tokenizedUrl: String): ByteArray =
        client.get(tokenizedUrl).readRawBytes()

    // Auto subtitles (Perso STT + Gemini translate)

    suspend fun submitSubtitleJob(
        file: BinaryPart,
        spec: SubtitleSpec
    ): SubtitleJobResponse =
        client.post("api/v2/subtitles") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(file)
                        append("spec", json.encodeToString(SubtitleSpec.serializer(), spec))
                    }
                )
            )
        }.body()

    /**
     * 사용자가 수정한 SRT 텍스트를 source 로 다른 언어 자막 재생성. 영상/오디오 업로드 없음 — Gemini
     * 만 호출. 응답 jobId 는 기존 [getSubtitleStatus] / [downloadSrt] 로 폴링·다운로드 가능.
     */
    suspend fun regenerateSubtitleJob(
        srtFile: BinaryPart,
        spec: SubtitleSpec
    ): SubtitleJobResponse =
        client.post("api/v2/subtitles/regenerate") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(srtFile)
                        append("spec", json.encodeToString(SubtitleSpec.serializer(), spec))
                    }
                )
            )
        }.body()

    suspend fun getSubtitleStatus(jobId: String): SubtitleStatusResponse =
        client.get("api/v2/subtitles/$jobId").body()

    suspend fun downloadSrt(tokenizedUrl: String): ByteArray =
        client.get(tokenizedUrl).readRawBytes()

    // Auto dubbing (Perso translate, no lipsync)

    suspend fun submitAutoDubJob(
        file: BinaryPart,
        spec: AutoDubSpec
    ): AutoDubJobResponse =
        client.post("api/v2/autodub") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(file)
                        append("spec", json.encodeToString(AutoDubSpec.serializer(), spec))
                    }
                )
            )
        }.body()

    suspend fun getAutoDubStatus(jobId: String): AutoDubStatusResponse =
        client.get("api/v2/autodub/$jobId").body()

    suspend fun downloadDubbedAudio(tokenizedUrl: String): ByteArray =
        client.get(tokenizedUrl).readRawBytes()

    suspend fun downloadDubbedVideo(tokenizedUrl: String): ByteArray =
        client.get(tokenizedUrl).readRawBytes()
}

private fun io.ktor.client.request.forms.FormBuilder.append(part: BinaryPart) {
    append(
        key = part.fieldName,
        value = part.bytes,
        headers = Headers.build {
            append(HttpHeaders.ContentType, part.contentType)
            append(HttpHeaders.ContentDisposition, "filename=\"${part.filename}\"")
        }
    )
}
