package com.example.dubcast.domain.usecase.subtitle

import kotlinx.coroutines.delay

internal sealed class PollDecision<out R> {
    data class Ready<R>(val value: R) : PollDecision<R>()
    data class Failed(val reason: String?) : PollDecision<Nothing>()
    data object Processing : PollDecision<Nothing>()
}

/**
 * Bounded polling loop shared by the auto-subtitle / auto-dub use cases.
 * [classify] inspects the latest fetched status and tells the loop whether
 * to return, fail, or sleep for [pollIntervalMs] before the next attempt.
 */
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
