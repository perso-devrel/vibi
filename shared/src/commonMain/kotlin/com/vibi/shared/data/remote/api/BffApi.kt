package com.vibi.shared.data.remote.api

import com.vibi.shared.data.remote.dto.AppleAuthRequestDto
import com.vibi.shared.data.remote.dto.AssetUploadUrlRequest
import com.vibi.shared.data.remote.dto.AssetUploadUrlResponse
import com.vibi.shared.data.remote.dto.AuthResponseDto
import com.vibi.shared.data.remote.dto.GoogleAuthRequestDto
import com.vibi.shared.data.remote.dto.RenderConfig
import com.vibi.shared.data.remote.dto.RenderConfigV3
import com.vibi.shared.data.remote.dto.RenderJobResponse
import com.vibi.shared.data.remote.dto.RenderStatusResponse
import com.vibi.shared.data.remote.dto.SeparationJobResponse
import com.vibi.shared.data.remote.dto.SeparationSpec
import com.vibi.shared.data.remote.dto.SeparationStatusResponse
import com.vibi.shared.data.remote.dto.TestdataSeparationFolderDto
import com.vibi.shared.data.remote.dto.AdminGrantRequest
import com.vibi.shared.data.remote.dto.CreditBalanceResponse
import com.vibi.shared.data.remote.dto.CreditCostResponse
import com.vibi.shared.data.remote.dto.CreditPurchaseRequest
import com.vibi.shared.data.remote.dto.CreditPurchaseResponse
import io.ktor.client.request.parameter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
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
    /**
     * R2 presigned PUT 전용 client. baseUrl/Authorization/contentType default 가 없어야
     * SigV4 검증 통과. [com.vibi.shared.data.remote.createR2HttpClient] 로 생성된 인스턴스.
     */
    private val r2Client: HttpClient,
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
     * 음원 분리 비용 견적 — "이 구간 X 크레딧 사용, 진행할까요?" 확인 팝업 표시 전 호출.
     * BFF 가 비용 (시작된 1분당 1, 올림, 최소 1) + 잔액 + 충분 여부를 한 번에 반환.
     */
    suspend fun getCreditCost(durationMs: Long): CreditCostResponse =
        client.get("api/v2/credits/cost") {
            parameter("durationMs", durationMs)
        }.body()

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

    /**
     * 유료 크레딧 수요 표현 — IAP 미오픈 기간 동안 "결제 빨리 열어달라"는 탭을 BFF 에 적재.
     * 서버가 (userId) 기준 유저당 1회로 집계 → 웹 admin 이 합산 숫자를 읽는다. 모바일은
     * fire-and-forget (재탭은 컨페티만, 카운트 영향 없음) 이라 응답 body 불필요.
     */
    suspend fun recordPaidCreditIntent() {
        client.post("api/v2/intent/paid-credits")
    }

    /** 음성분리 mock 데이터 — testdata/<startSec>-<endSec>/ 폴더 목록 + 그 안의 stem 이름. */
    suspend fun listSeparationTestdata(): List<TestdataSeparationFolderDto> =
        client.get("api/v2/testdata/separation/list").body()

    /** BFF render 잡 제출 — 모든 multipart parts 를 한 번에 업로드. */
    suspend fun submitRenderJob(
        videoFiles: List<BinaryPart>,
        bgmFiles: List<BinaryPart>,
        config: RenderConfig,
    ): RenderJobResponse =
        client.post("api/v2/render") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        videoFiles.forEach { append(it) }
                        bgmFiles.forEach { append(it) }
                        append("config", json.encodeToString(RenderConfig.serializer(), config))
                    }
                )
            )
        }.body()

    suspend fun getRenderStatus(jobId: String): RenderStatusResponse =
        client.get("api/v2/render/$jobId/status").body()

    /**
     * 렌더 결과를 스트리밍으로 소비 — 응답 전체를 ByteArray 로 적재하지 않고 [consume] 에 응답
     * 채널을 넘긴다. 호출자는 보통 [com.vibi.shared.platform.writeChannelToFile] 로 파일에 직접
     * 기록(피크 메모리 ~청크 1개). 100MB대 mp4 다운로드의 OOM/피크2배 방지.
     */
    suspend fun <T> downloadRenderResult(jobId: String, consume: suspend (ByteReadChannel) -> T): T =
        client.prepareGet("api/v2/render/$jobId/download").execute { resp ->
            consume(resp.bodyAsChannel())
        }

    // --- v3 asset-by-reference 흐름 (모바일이 R2 직접 PUT, RenderConfig 에는 키만 담음) ---

    /**
     * R2 presigned PUT URL 발급 요청. 응답 [AssetUploadUrlResponse.alreadyExists] 가 true 면
     * 모바일은 PUT skip 하고 [AssetUploadUrlResponse.assetKey] 만 재사용.
     */
    suspend fun requestAssetUploadUrl(request: AssetUploadUrlRequest): AssetUploadUrlResponse =
        client.post("api/v2/assets/upload-url") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /**
     * R2 에 직접 PUT. [presignedUrl] 의 SigV4 가 [contentType] + Content-Length 를 sign 했으므로
     * 동일 값으로 요청해야 한다 — 다른 값이면 R2 가 401. BFF [client] 의 baseUrl/Authorization
     * default 가 R2 호출에 끼지 않도록 별 [r2Client] 사용.
     *
     * @return PUT 성공 (2xx) 여부. expectSuccess=true 라 실패 시 throw 가 우선이지만, 명시적으로
     *   bool 반환해 호출자가 follow-up 처리 가능.
     */
    suspend fun putAssetToR2(presignedUrl: String, body: OutgoingContent): Boolean {
        // body 는 스트리밍 OutgoingContent([com.vibi.shared.platform.fileUploadBody]) — 대용량
        // 영상을 ByteArray 로 통째 적재하지 않고 청크 전송. SigV4 가 contentType + Content-Length
        // 를 sign 하므로 body 가 둘 다 정확히 제공해야 하며, fileUploadBody 가 이를 보장한다.
        val resp = r2Client.put(presignedUrl) { setBody(body) }
        return resp.status.value in 200..299
    }

    /**
     * v3 render 잡 제출 — multipart 없이 JSON body 만. segment/BGM 은 [RenderConfigV3] 의
     * assetKey 로 R2 참조. Cloud Run body 한도 회피.
     */
    suspend fun submitRenderJobV3(config: RenderConfigV3): RenderJobResponse =
        client.post("api/v2/render/v3") {
            contentType(ContentType.Application.Json)
            setBody(config)
        }.body()

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

    /** 토큰화 stem URL 을 verbatim GET 후 스트리밍 소비 — 전체 적재 없이 [consume] 에 채널 전달. */
    suspend fun <T> downloadStem(tokenizedUrl: String, consume: suspend (ByteReadChannel) -> T): T =
        client.prepareGet(tokenizedUrl).execute { resp ->
            consume(resp.bodyAsChannel())
        }
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
