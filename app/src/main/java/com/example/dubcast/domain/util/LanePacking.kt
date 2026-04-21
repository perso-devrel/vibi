package com.example.dubcast.domain.util

/**
 * Pick the lowest non-negative lane index whose existing items don't overlap
 * the given time interval. Greedy first-fit.
 *
 * Used by overlay use cases (image clips, text overlays) to auto-place new
 * clips on a free row when several share the same time window.
 */
internal fun <T> pickLowestFreeLane(
    existing: List<T>,
    startMs: Long,
    endMs: Long,
    laneOf: (T) -> Int,
    startOf: (T) -> Long,
    endOf: (T) -> Long
): Int {
    // Single linear pass collects every lane that overlaps the target window,
    // then walk lane numbers from 0 until we find one not in that set. The
    // earlier implementation re-scanned `existing` for every candidate lane
    // (O(items × lanes)); this is O(items + lanes) at worst and avoids
    // repeated allocation of the lambda call frame for `any`.
    val occupied = HashSet<Int>(existing.size)
    for (item in existing) {
        if (startOf(item) < endMs && endOf(item) > startMs) {
            occupied += laneOf(item)
        }
    }
    var lane = 0
    while (lane in occupied) lane++
    return lane
}
