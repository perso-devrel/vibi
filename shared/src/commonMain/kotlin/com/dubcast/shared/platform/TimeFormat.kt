@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.dubcast.shared.platform

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Long(epoch ms) → "yyyy-MM-dd HH:mm" 시스템 타임존 기준 포맷. */
fun formatTimestamp(millis: Long): String {
    val ldt = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(ldt.year.toString().padStart(4, '0')).append('-')
        append(ldt.monthNumber.toString().padStart(2, '0')).append('-')
        append(ldt.dayOfMonth.toString().padStart(2, '0')).append(' ')
        append(ldt.hour.toString().padStart(2, '0')).append(':')
        append(ldt.minute.toString().padStart(2, '0'))
    }
}

/**
 * 상대 시간 표현 ("방금 전" / "5분 전" / "3시간 전" / "2일 전" / 그 이상은 절대 시간).
 */
fun formatRelative(millis: Long, now: Long = currentTimeMillis()): String {
    val diff = now - millis
    if (diff < 0) return formatTimestamp(millis)
    val sec = diff / 1000
    val min = sec / 60
    val hour = min / 60
    val day = hour / 24
    return when {
        sec < 30 -> "방금 전"
        min < 1 -> "${sec}초 전"
        hour < 1 -> "${min}분 전"
        day < 1 -> "${hour}시간 전"
        day < 7 -> "${day}일 전"
        else -> formatTimestamp(millis)
    }
}
