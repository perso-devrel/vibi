@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.vibi.shared.platform

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Long(epoch ms) → "yyyy-MM-dd HH:mm" 시스템 타임존 기준 포맷. */
fun formatTimestamp(millis: Long): String {
    val ldt = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(ldt.year.toString().padStart(4, '0')).append('-')
        // monthNumber / dayOfMonth 는 kotlinx-datetime 0.7+ 에서 deprecated 이지만
        // 현 버전 (0.7.1) 의 새 property 가 month.number / day 가 아니라 빌드 실패 —
        // 정확한 마이그레이션 path 확인 전까지 deprecation warning 수용하고 원본 유지.
        @Suppress("DEPRECATION")
        append(ldt.monthNumber.toString().padStart(2, '0')).append('-')
        @Suppress("DEPRECATION")
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
        sec < 30 -> "just now"
        min < 1 -> "${sec}s ago"
        hour < 1 -> "${min}m ago"
        day < 1 -> "${hour}h ago"
        day < 7 -> "${day}d ago"
        else -> formatTimestamp(millis)
    }
}
