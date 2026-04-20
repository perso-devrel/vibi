package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Anchor
import com.example.dubcast.domain.model.SubtitleClip
import com.example.dubcast.domain.model.SubtitlePosition
import java.util.UUID
import javax.inject.Inject

class SplitDubTextToSubtitlesUseCase @Inject constructor() {

    operator fun invoke(
        text: String,
        startMs: Long,
        durationMs: Long,
        dubClipId: String,
        projectId: String
    ): List<SubtitleClip> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return emptyList()
        }

        val weights = lines.map { line -> line.count { !it.isWhitespace() } }
        val totalWeight = weights.sum()

        if (totalWeight == 0) {
            return listOf(singleSubtitle(lines.first(), startMs, startMs + durationMs, dubClipId, projectId))
        }

        val result = mutableListOf<SubtitleClip>()
        var cursorMs = startMs

        for (i in lines.indices) {
            val endMs = if (i < lines.size - 1) {
                cursorMs + (durationMs * weights[i]) / totalWeight
            } else {
                startMs + durationMs
            }
            result.add(singleSubtitle(lines[i], cursorMs, endMs, dubClipId, projectId))
            cursorMs = endMs
        }

        return result
    }

    private fun singleSubtitle(
        text: String,
        startMs: Long,
        endMs: Long,
        dubClipId: String,
        projectId: String
    ) = SubtitleClip(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        text = text,
        startMs = startMs,
        endMs = endMs,
        position = SubtitlePosition(anchor = Anchor.BOTTOM, yOffsetPct = 90f),
        sourceDubClipId = dubClipId,
        xPct = 50f,
        yPct = 85f,
        widthPct = 80f,
        heightPct = 12f
    )
}
