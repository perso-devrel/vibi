package com.vibi.shared.domain.usecase.separation

import com.vibi.shared.data.remote.MAX_CONSECUTIVE_POLL_FAILURES
import com.vibi.shared.data.remote.SessionExpiredException
import com.vibi.shared.data.remote.isUnauthorized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed class PollDecision<out R> {
    data class Ready<R>(val value: R) : PollDecision<R>()
    data class Failed(val reason: String?) : PollDecision<Nothing>()
    data object Processing : PollDecision<Nothing>()
}

suspend fun <T, R> pollUntilReady(
    label: String,
    pollIntervalMs: Long,
    maxAttempts: Int,
    fetch: suspend () -> T,
    onProcessing: suspend (T) -> Unit = {},
    classify: (T) -> PollDecision<R>,
): R {
    var consecutiveFailures = 0
    repeat(maxAttempts) {
        val current = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 일시 오류(5xx·끊김)뿐 아니라 401 도 즉시 종료하지 않는다 — 기기 잠금 중 Keychain
            // 읽기 실패로 Authorization 헤더가 잠깐 빠지면 BFF 가 401(missing_token)을 주는데, 이는
            // 잠금 해제 후 회복되는 일시 오류이고 잡은 서버에서 계속 진행된다(단발 401로 잡을 날리면
            // 서버 완료 결과를 못 본다). 연속 실패 한계까지 백오프 재시도하고, 그 뒤에도 401 이
            // 지속되면(진짜 세션 만료) 비로소 SessionExpiredException 으로 재로그인을 유도한다.
            if (++consecutiveFailures > MAX_CONSECUTIVE_POLL_FAILURES) {
                if (e.isUnauthorized()) throw SessionExpiredException()
                throw e
            }
            delay(pollIntervalMs)
            return@repeat
        }
        consecutiveFailures = 0
        when (val decision = classify(current)) {
            is PollDecision.Ready -> return decision.value
            is PollDecision.Failed ->
                throw IllegalStateException(decision.reason ?: "$label job failed")
            PollDecision.Processing -> {
                onProcessing(current)
                delay(pollIntervalMs)
            }
        }
    }
    throw IllegalStateException(
        "$label job timed out after ${maxAttempts * pollIntervalMs / 1000}s"
    )
}
