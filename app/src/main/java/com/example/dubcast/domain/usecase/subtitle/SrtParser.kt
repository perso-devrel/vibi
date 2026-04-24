package com.example.dubcast.domain.usecase.subtitle

data class ParsedSrtCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Defensive SRT parser used for auto-subtitle ingestion.
 *
 * Behaviour for malformed input:
 *  - Lines that should hold an integer index but don't are skipped.
 *  - Lines that should hold a `HH:MM:SS,mmm --> HH:MM:SS,mmm` timestamp but
 *    don't are skipped together with their cue body.
 *  - Cues with empty text or non-positive duration are dropped.
 * The parser never throws on malformed cues — callers receive whatever
 * cues could be recovered.
 *
 * Inputs over [MAX_INPUT_BYTES] are rejected with [IllegalArgumentException]
 * so a runaway BFF response can't OOM the device. ~5 MB ≈ 50k cues, which
 * already exceeds anything the timeline can render usefully.
 */
object SrtParser {
    const val MAX_INPUT_BYTES = 5 * 1024 * 1024

    private val TIMESTAMP_LINE = Regex(
        """\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3}).*"""
    )

    fun parse(srt: String): List<ParsedSrtCue> {
        require(srt.length <= MAX_INPUT_BYTES) {
            "SRT body exceeds $MAX_INPUT_BYTES bytes"
        }
        val cues = mutableListOf<ParsedSrtCue>()
        val lines = srt.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            while (i < lines.size && lines[i].isBlank()) i++
            if (i >= lines.size) break
            val indexLine = lines[i].trim()
            if (indexLine.toIntOrNull() == null) {
                i++
                continue
            }
            i++
            if (i >= lines.size) break
            val match = TIMESTAMP_LINE.matchEntire(lines[i])
            if (match == null) {
                i++
                continue
            }
            val startMs = toMillis(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt()
            )
            val endMs = toMillis(
                match.groupValues[5].toInt(),
                match.groupValues[6].toInt(),
                match.groupValues[7].toInt(),
                match.groupValues[8].toInt()
            )
            i++
            val buf = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(lines[i])
                i++
            }
            val text = buf.toString()
            if (text.isNotBlank() && endMs > startMs) {
                cues.add(ParsedSrtCue(startMs, endMs, text))
            }
        }
        return cues
    }

    private fun toMillis(h: Int, m: Int, s: Int, ms: Int): Long =
        h * 3_600_000L + m * 60_000L + s * 1_000L + ms
}
