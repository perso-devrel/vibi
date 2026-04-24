package com.example.dubcast.fake

/**
 * Step-through helper for the auto-pipeline fakes. Each call to [next]
 * returns the configured result at the current index and advances; once
 * the list is exhausted, the last entry is returned for every subsequent
 * call so a polling loop can settle on a terminal state.
 */
internal class FakePollSequence<T> {
    val results: MutableList<T> = mutableListOf()
    private var index = 0

    fun next(empty: () -> T): T {
        if (results.isEmpty()) return empty()
        val value = results[index.coerceAtMost(results.lastIndex)]
        index++
        return value
    }
}
