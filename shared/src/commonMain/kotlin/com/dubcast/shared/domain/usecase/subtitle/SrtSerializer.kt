package com.dubcast.shared.domain.usecase.subtitle

import com.dubcast.shared.domain.model.SubtitleClip

/** SubtitleClip 들을 SRT body 로 직렬화. 시작 시간 기준 정렬 후 1-based index. */
object SrtSerializer {
    fun fromClips(clips: List<SubtitleClip>): String {
        val sorted = clips.sortedBy { it.startMs }
        val sb = StringBuilder()
        var idx = 1
        for (c in sorted) {
            val text = c.text.trim()
            if (text.isEmpty()) continue
            sb.append(idx).append('\n')
            sb.append(formatSrtTime(c.startMs)).append(" --> ").append(formatSrtTime(c.endMs)).append('\n')
            sb.append(text).append("\n\n")
            idx++
        }
        return sb.toString()
    }

    private fun formatSrtTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000
        val minutes = (total % 3_600_000) / 60_000
        val seconds = (total % 60_000) / 1000
        val millis = total % 1000
        return buildString {
            append(hours.toString().padStart(2, '0')).append(':')
            append(minutes.toString().padStart(2, '0')).append(':')
            append(seconds.toString().padStart(2, '0')).append(',')
            append(millis.toString().padStart(3, '0'))
        }
    }
}
