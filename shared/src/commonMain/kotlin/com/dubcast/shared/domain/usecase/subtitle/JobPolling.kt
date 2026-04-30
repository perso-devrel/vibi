package com.dubcast.shared.domain.usecase.subtitle

import kotlinx.coroutines.delay

internal sealed class PollDecision<out R> {
    data class Ready<R>(val value: R) : PollDecision<R>()
    data class Failed(val reason: String?) : PollDecision<Nothing>()
    data object Processing : PollDecision<Nothing>()
}

internal suspend fun <T, R> pollUntilReady(
    label: String,
    pollIntervalMs: Long,
    maxAttempts: Int,
    fetch: suspend () -> T,
    classify: (T) -> PollDecision<R>
): R {
    repeat(maxAttempts) {
        when (val decision = classify(fetch())) {
            is PollDecision.Ready -> return decision.value
            is PollDecision.Failed ->
                throw IllegalStateException(decision.reason ?: "$label job failed")
            PollDecision.Processing -> delay(pollIntervalMs)
        }
    }
    throw IllegalStateException(
        "$label job timed out after ${maxAttempts * pollIntervalMs / 1000}s"
    )
}
