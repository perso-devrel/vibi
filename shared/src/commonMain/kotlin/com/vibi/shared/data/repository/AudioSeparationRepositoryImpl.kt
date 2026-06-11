package com.vibi.shared.data.repository

import com.vibi.shared.data.local.CreditStore
import com.vibi.shared.data.local.UserSession
import com.vibi.shared.data.remote.api.BffApi
import com.vibi.shared.data.remote.api.BinaryPart
import com.vibi.shared.data.remote.dto.BffErrorResponse
import com.vibi.shared.data.remote.dto.SeparationSpec
import com.vibi.shared.domain.error.InsufficientCreditsException
import com.vibi.shared.domain.model.SeparationCost
import com.vibi.shared.domain.model.Stem
import com.vibi.shared.domain.repository.AudioSeparationRepository
import com.vibi.shared.domain.repository.SeparationStatus
import com.vibi.shared.platform.AudioExtractException
import com.vibi.shared.platform.AudioExtractor
import com.vibi.shared.platform.AudioSourceKind
import com.vibi.shared.platform.readFileBytes
import com.vibi.shared.platform.saveBytesToPersistentFile
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

class AudioSeparationRepositoryImpl(
    private val api: BffApi,
    private val bffBaseUrl: String,
    /** trim + audio extract → m4a 생성. 안드로이드 stub 분기 차단도 본 객체의 `isSupported` 로
     *  결정 — Android actual 은 `false` 라 진입 전 typed exception 으로 떨어짐. */
    private val audioExtractor: AudioExtractor,
    /** 402 응답의 권위 잔액으로 로컬 캐시 동기화. null 이면 동기화 skip (테스트). */
    private val creditStore: CreditStore? = null,
    /** 현재 로그인 사용자 식별 — credit cache key. null 이면 동기화 skip. */
    private val userSession: UserSession? = null,
) : AudioSeparationRepository {

    /** stem URL 이 path-only (`/api/v2/...`) 면 BFF base 와 join 해 absolute URL 로 — iOS AVPlayer
     * 가 host 없는 URL 을 silent fail 처리하는 것 회피. */
    private fun absUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl
        else "${bffBaseUrl.trimEnd('/')}/${pathOrUrl.trimStart('/')}"

    override suspend fun getCost(durationMs: Long): Result<SeparationCost> = runCatching {
        val resp = api.getCreditCost(durationMs)
        // BFF 권위 잔액으로 로컬 캐시 동기화 — UserMenu 같은 다른 화면이 즉시 최신 값 표시.
        if (creditStore != null && userSession != null) {
            creditStore.setBalance(userSession.current(), resp.balance)
        }
        SeparationCost(
            durationMs = resp.durationMs,
            credits = resp.credits,
            balance = resp.balance,
            sufficient = resp.sufficient,
        )
    }

    override suspend fun startSeparation(
        sourceUri: String,
        sourceKind: AudioSourceKind,
        sourceLanguageCode: String,
        trimStartMs: Long?,
        trimEndMs: Long?,
    ): Result<String> = runCatching {
        // Repository 단 최종 가드 — UI hide / ViewModel 가드 누락 시에도 안드로이드 stub 호출
        // 막아 crash 차단. typed exception 이라 UI 가 사용자 메시지로 graceful 매핑.
        if (!audioExtractor.isSupported) {
            throw AudioExtractException.Unknown("separation not supported on this platform")
        }

        val prepared = audioExtractor.prepareSeparationAudio(
            sourceUri = sourceUri,
            sourceKind = sourceKind,
            startMs = trimStartMs,
            endMs = trimEndMs,
        )
        val bytes = readFileBytes(prepared.path)
        val part = BinaryPart(
            fieldName = "file",
            filename = "separation.${prepared.ext}",
            bytes = bytes,
            contentType = prepared.mimeType,
        )
        val spec = SeparationSpec(sourceLanguageCode = sourceLanguageCode)
        try {
            api.startSeparation(file = part, spec = spec).jobId
        } catch (e: ClientRequestException) {
            throw mapInsufficientCreditsOrRethrow(e)
        }
    }

    /**
     * 402 응답 파싱. body 가 `BffErrorResponse(error="insufficient_credits", detail="required=N balance=M")`
     * 형식이라 detail 을 정규식으로 분해. detail 파싱 실패 시 fallback (required=0, balance=0) 으로
     * exception 던지되 UI 는 "충전 필요" 분기로 유도. 다른 4xx 는 원본 그대로 rethrow.
     *
     * Side effect: balance 파싱에 성공하면 [CreditStore] 도 갱신 — 사용자가 다른 화면 (UserMenu)
     * 으로 이동해 충전 시작 직전 잔액이 이미 최신.
     */
    private suspend fun mapInsufficientCreditsOrRethrow(e: ClientRequestException): Throwable {
        if (e.response.status.value != HTTP_PAYMENT_REQUIRED) return e
        val body = runCatching { e.response.body<BffErrorResponse>() }.getOrNull()
        if (body?.error != ERROR_INSUFFICIENT_CREDITS) return e
        val (required, balance) = parseRequiredBalance(body.detail)
        if (creditStore != null && userSession != null) {
            creditStore.setBalance(userSession.current(), balance)
        }
        return InsufficientCreditsException(required = required, balance = balance)
    }

    private fun parseRequiredBalance(detail: String?): Pair<Int, Int> {
        if (detail == null) return 0 to 0
        // "required=2 balance=1" 형식. BFF SeparationRoutes.kt 의 ApiErrorException detail 생성과 1:1.
        // 형식 변경 시 양쪽 동시 갱신.
        val req = REQUIRED_REGEX.find(detail)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val bal = BALANCE_REGEX.find(detail)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return req to bal
    }

    override suspend fun pollStatus(jobId: String): Result<SeparationStatus> = runCatching {
        val response = api.getSeparationStatus(jobId)
        when {
            response.status == STATUS_FAILED ->
                SeparationStatus.Failed(response.jobId, response.progressReason)

            response.status == STATUS_READY && response.stems.isNotEmpty() ->
                SeparationStatus.Ready(
                    jobId = response.jobId,
                    stems = response.stems.map {
                        Stem(
                            stemId = it.stemId,
                            label = it.label,
                            url = absUrl(it.url),
                            kind = Stem.kindFromId(it.stemId),
                            speakerIndex = Stem.speakerIndexFromId(it.stemId)
                        )
                    },
                    actualDurationMs = response.actualDurationMs
                )

            else -> SeparationStatus.Processing(
                jobId = response.jobId,
                progress = response.progress,
                progressReason = response.progressReason
            )
        }
    }

    override suspend fun downloadStem(stemUrl: String, outputFileName: String): Result<String> =
        runCatching {
            val bytes = api.downloadStem(stemUrl)
            // 영구 디렉터리에 저장 — 서버 연결이 끊겨도 오프라인 재생/편집 가능.
            saveBytesToPersistentFile(outputFileName, bytes)
        }

    private companion object {
        const val STATUS_READY = "READY"
        const val STATUS_FAILED = "FAILED"
        const val HTTP_PAYMENT_REQUIRED = 402
        const val ERROR_INSUFFICIENT_CREDITS = "insufficient_credits"
        val REQUIRED_REGEX = Regex("required=(\\d+)")
        val BALANCE_REGEX = Regex("balance=(\\d+)")
    }
}
