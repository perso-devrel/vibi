package com.dubcast.shared.domain.chat

import com.dubcast.shared.data.remote.dto.ContextBgmClipDto
import com.dubcast.shared.data.remote.dto.ContextDubClipDto
import com.dubcast.shared.data.remote.dto.ContextSegmentDto
import com.dubcast.shared.data.remote.dto.ContextStemDto
import com.dubcast.shared.data.remote.dto.ContextSubtitleClipDto
import com.dubcast.shared.data.remote.dto.ProjectContextDto
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.ui.timeline.TimelineUiState

/**
 * TimelineUiState → ProjectContextDto 압축. Gemini 가 발화 해석 시 참조하는 단일 source.
 *
 * 토큰 비용 통제:
 *  - 자막 텍스트 200자 cap (BFF 와 동일 정책)
 *  - sourceUri 는 path 마지막 segment 만
 *  - 분리 stems 는 selections 의 flat 화 (label = stem id 그대로)
 */
object ProjectContextBuilder {
    private const val TEXT_CAP = 200

    fun build(state: TimelineUiState): ProjectContextDto {
        val segs = mutableListOf<ContextSegmentDto>()
        var acc = 0L
        for (seg in state.segments) {
            if (seg.type != SegmentType.VIDEO) {
                acc += seg.effectiveDurationMs
                continue
            }
            val start = acc
            val end = acc + seg.effectiveDurationMs
            segs += ContextSegmentDto(
                id = seg.id,
                startMs = start,
                endMs = end,
                sourceUri = seg.sourceUri.substringAfterLast('/').take(64),
                speedScale = seg.speedScale,
                volumeScale = seg.volumeScale,
            )
            acc = end
        }

        val subs = state.subtitleClips.mapIndexed { i, c ->
            ContextSubtitleClipDto(
                id = c.id,
                index = i + 1,
                startMs = c.startMs,
                endMs = c.endMs,
                text = c.text.take(TEXT_CAP),
                languageCode = c.languageCode,
            )
        }

        val dubs = state.dubClips.map { c ->
            ContextDubClipDto(
                id = c.id,
                startMs = c.startMs,
                endMs = c.startMs + c.durationMs,
                voiceId = c.voiceId,
            )
        }

        val bgms = state.bgmClips.map { b ->
            ContextBgmClipDto(
                id = b.id,
                startMs = b.startMs,
                endMs = b.startMs + b.effectiveDurationMs,
                volumeScale = b.volumeScale,
                speedScale = b.speedScale,
            )
        }

        val stems = state.separationDirectives.flatMap { d ->
            d.selections.map { s ->
                ContextStemDto(
                    stemId = s.stemId,
                    label = s.stemId,
                    volume = s.volume,
                    selected = s.selected,
                )
            }
        }

        val selectedClipId = state.selectedDubClipId
            ?: state.selectedSubtitleClipId
            ?: state.selectedImageClipId
            ?: state.selectedBgmClipId

        return ProjectContextDto(
            segments = segs,
            subtitleClips = subs,
            dubClips = dubs,
            bgmClips = bgms,
            separationStems = stems,
            currentPlayheadMs = state.playbackPositionMs,
            selectedSegmentId = state.selectedSegmentId,
            selectedClipId = selectedClipId,
        )
    }
}
