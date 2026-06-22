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
            // 401 은 재인증 외 복구 불가 → 즉시 종료. 그 외 일시 오류(5xx·끊김)는 연속 실패가
            // 한계를 넘기 전까지 백오프하며 폴링 지속 — 단발 블립으로 잡을 날리지 않는다.
            if (e.isUnauthorized()) throw SessionExpiredException()
            if (++consecutiveFailures > MAX_CONSECUTIVE_POLL_FAILURES) throw e
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
