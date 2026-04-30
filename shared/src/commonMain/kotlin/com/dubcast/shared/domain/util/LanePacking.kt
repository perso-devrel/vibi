package com.dubcast.shared.domain.util

fun <T> pickLowestFreeLane(
    existing: List<T>,
    startMs: Long,
    endMs: Long,
    laneOf: (T) -> Int,
    startOf: (T) -> Long,
    endOf: (T) -> Long
): Int {
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
