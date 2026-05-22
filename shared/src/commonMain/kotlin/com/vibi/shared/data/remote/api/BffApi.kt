package com.vibi.shared.data.remote.api

import com.vibi.shared.data.remote.dto.AppleAuthRequestDto
import com.vibi.shared.data.remote.dto.AuthResponseDto
import com.vibi.shared.data.remote.dto.GoogleAuthRequestDto
import com.vibi.shared.data.remote.dto.MixJobResponse
import com.vibi.shared.data.remote.dto.MixRequest
import com.vibi.shared.data.remote.dto.MixStatusResponse
import com.vibi.shared.data.remote.dto.RenderConfig
import com.vibi.shared.data.remote.dto.RenderInputCacheResponse
import com.vibi.shared.data.remote.dto.RenderJobResponse
import com.vibi.shared.data.remote.dto.RenderStatusResponse
import com.vibi.shared.data.remote.dto.SeparationJobResponse
import com.vibi.shared.data.remote.dto.SeparationSpec
import com.vibi.shared.data.remote.dto.SeparationStatusResponse
import com.vibi.shared.data.remote.dto.TestdataSeparationFolderDto
import com.vibi.shared.data.remote.dto.AdminGrantRequest
import com.vibi.shared.data.remote.dto.CreditBalanceResponse
import com.vibi.shared.data.remote.dto.CreditPurchaseRequest
import com.vibi.shared.data.remote.dto.CreditPurchaseResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

// `data class` 대신 일반 class — 자동 생성된 equals/hashCode 가 큰 ByteArray 를 deep 비교하면
// 무의미한 비용. multipart 빌드용 일회성 컨테이너라 referential equality 로 충분.
class BinaryPart(
    val fieldName: String,
    val filename: String,
    val bytes: ByteArray,
    val contentType: String,
)

class BffApi(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    /** Google ID Token → BFF JWT 교환. native GoogleSignIn SDK 가 받은 ID Token 을 그대로 전달. */
    suspend fun exchangeGoogleIdToken(idToken: String): AuthResponseDto =
        client.post("api/v2/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleAuthRequestDto(idToken))
        }.body()

    /**
     * Apple ID Token → BFF JWT 교환.
     *
     * @param fullName Apple 의 최초-1회 fullName. iOS 가 받은 직후 그대로 전달 — 두 번째
     *   로그인부터는 null. 서버는 신규 가입 시에만 이 값을 user.name 으로 사용.
     */
    suspend fun exchangeAppleIdToken(idToken: String, fullName: String?): AuthResponseDto =
        client.post("api/v2/auth/apple") {
            contentType(ContentType.Application.Json)
            setBody(AppleAuthRequestDto(idToken, fullName))
        }.body()

    /** 인증된 본인 영구 삭제 — App Store 가이드라인 5.1.1(v). */
    suspend fun deleteAccount() {
        client.delete("api/v2/auth/account")
    }

    /** 현재 사용자의 크레딧 잔액. row 가 없으면 0. */
    suspend fun getCreditBalance(): CreditBalanceResponse =
        client.get("api/v2/credits").body()

    /**
     * IAP 영수증 가산. (platform, transactionId) UNIQUE 로 BFF 가 중복 호출 방어 —
     * 모바일은 StoreKit / Play Billing 콜백 후 안전하게 재시도 가능.
     */
    suspend fun purchaseCredits(request: CreditPurchaseRequest): CreditPurchaseResponse =
        client.post("api/v2/credits/purchase") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * 관리자 무료 충전. admin role 이 아니면 BFF 가 403 으로 거부. 매 호출마다 BFF 가 새
     * txId 생성하므로 모바일은 idempotency 신경 안 써도 됨.
     */
    suspend fun adminGrantCredits(request: AdminGrantRequest): CreditPurchaseResponse =
        client.post("api/v2/credits/admin-grant") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** 음성분리 mock 데이터 — testdata/<startSec>-<endSec>/ 폴더 목록 + 그 안의 stem 이름. */
    suspend fun listSeparationTestdata(): List<TestdataSeparationFolderDto> =
        client.get("api/v2/testdata/separation/list").body()

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
     * @param inputId non-null 이면 BFF 가 캐시된 video 를 재사용. null 이면 multipart 로 업로드.
     */
    suspend fun submitRenderJob(
        videoFiles: List<BinaryPart>,
        segmentImageFiles: List<BinaryPart>,
        bgmFiles: List<BinaryPart>,
        config: RenderConfig,
        inputId: String? = null,
    ): RenderJobResponse =
        client.post("api/v2/render") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        if (inputId == null) {
                            videoFiles.forEach { append(it) }
                        } else {
                            append("inputId", inputId)
                        }
                        segmentImageFiles.forEach { append(it) }
                        bgmFiles.forEach { append(it) }
                        append("config", json.encodeToString(RenderConfig.serializer(), config))
                    }
                )
            )
        }.body()

    suspend fun getRenderStatus(jobId: String): RenderStatusResponse =
        client.get("api/v2/render/$jobId/status").body()

    suspend fun downloadRenderResult(jobId: String): ByteArray =
        client.get("api/v2/render/$jobId/download").readRawBytes()

    /**
     * @param file null 이면 multipart `file` part 자체를 생략. spec.editedRenderJobId 가 non-null 일 때
     *   BFF 가 render output 을 source 로 사용하므로 file 업로드 불필요. file 보내도 BFF 가 무시하지만
     *   네트워크 비용 절약 위해 호출자가 null 권장.
     */
    suspend fun startSeparation(
        file: BinaryPart?,
        spec: SeparationSpec
    ): SeparationJobResponse =
        client.post("api/v2/separate") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        file?.let { append(it) }
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
