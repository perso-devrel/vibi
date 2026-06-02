package com.vibi.shared.domain.model

import kotlin.math.round

/**
 * directive 앵커링 — 세그먼트 source 좌표(localStart/localEnd)와 타임라인 글로벌 ms 사이 변환.
 *
 * directive 는 [SeparationDirective.segmentId] 로 세그먼트에 묶이고, 글로벌
 * [SeparationDirective.rangeStartMs]/[SeparationDirective.rangeEndMs] 는 세그먼트 위치에서 파생되는 캐시다.
 * 세그먼트가 이동/복제/분할돼도 여기 함수로 글로벌 range 를 다시 계산하면 directive 가 자동으로 따라간다.
 *
 * source-local ↔ global 매핑은 [TimelineViewModel.sliceGlobalRange] 의 역(逆)과 동일한 수식:
 * `global = segStart + (local - trimStart) / speed`, `local = trimStart + (global - segStart) * speed`.
 * round() 후 toLong() — truncate 로 1ms 미만 잔재가 인접 경계와 어긋나는 문제 방지(slice 와 동일 규약).
 */
object DirectiveAnchor {

    /** 세그먼트 리스트에서 [segmentId] 의 글로벌 시작 ms (effectiveDurationMs 누적합). 없으면 null. */
    fun segmentStartOffsetMs(segments: List<Segment>, segmentId: String): Long? {
        var acc = 0L
        for (seg in segments) {
            if (seg.id == segmentId) return acc
            acc += seg.effectiveDurationMs
        }
        return null
    }

    /**
     * 앵커된 directive 의 현재 글로벌 range 를 세그먼트 위치에서 파생. 미앵커(segmentId 빔)거나 세그먼트가
     * 없으면 저장된 글로벌 range 를 그대로 반환(fallback).
     */
    fun globalRange(directive: SeparationDirective, segments: List<Segment>): LongRange {
        if (!directive.isAnchored) return directive.rangeStartMs..directive.rangeEndMs
        val seg = segments.firstOrNull { it.id == directive.segmentId }
            ?: return directive.rangeStartMs..directive.rangeEndMs
        val segStart = segmentStartOffsetMs(segments, directive.segmentId)
            ?: return directive.rangeStartMs..directive.rangeEndMs
        val speed = if (seg.speedScale > 0f) seg.speedScale else 1f
        val gs = segStart + (round((directive.localStartMs - seg.trimStartMs) / speed)).toLong()
        val ge = segStart + (round((directive.localEndMs - seg.trimStartMs) / speed)).toLong()
        return gs..ge
    }

    /** global timeline ms → 세그먼트 source-local ms. directive 생성 시 앵커 local 좌표 계산용. */
    fun toLocalMs(globalMs: Long, segment: Segment, segGlobalStart: Long): Long {
        val speed = if (segment.speedScale > 0f) segment.speedScale else 1f
        return segment.trimStartMs + round((globalMs - segGlobalStart) * speed).toLong()
    }

    /**
     * 앵커된 directive 들의 글로벌 range 캐시를 현재 세그먼트 배치로 재계산. 변경된 것만 copy 해 반환
     * (미앵커는 그대로). 호출자가 변경분을 영속화(addAll)한다.
     */
    fun reanchor(directives: List<SeparationDirective>, segments: List<Segment>): List<SeparationDirective> =
        directives.mapNotNull { d ->
            if (!d.isAnchored) return@mapNotNull null
            val r = globalRange(d, segments)
            if (r.first == d.rangeStartMs && r.last == d.rangeEndMs) null
            else d.copy(rangeStartMs = r.first, rangeEndMs = r.last)
        }

    /** [rangeStartMs] 가 속한 세그먼트 + 그 글로벌 시작. 못 찾으면(범위 밖) 마지막 세그먼트. 빈 리스트면 null. */
    private fun ownerSegment(globalStartMs: Long, segments: List<Segment>): Pair<Segment, Long>? {
        if (segments.isEmpty()) return null
        var acc = 0L
        for (s in segments) {
            val end = acc + s.effectiveDurationMs
            if (globalStartMs in acc until end) return s to acc
            acc = end
        }
        // 범위 끝(또는 밖) — 마지막 세그먼트에 귀속.
        val last = segments.last()
        return last to (acc - last.effectiveDurationMs)
    }

    /**
     * 글로벌 range 를 진실로 보고 앵커(segmentId + local source 좌표)를 재계산. delete/speed 등 글로벌 ms
     * ripple 로 글로벌 range 를 직접 옮긴 연산 직후 호출해 앵커를 일관되게 맞춘다 — 이후 [reanchor]
     * (앵커→글로벌)가 idempotent 가 되도록. 변경분만 반환.
     */
    fun resyncAnchors(directives: List<SeparationDirective>, segments: List<Segment>): List<SeparationDirective> =
        directives.mapNotNull { d ->
            val (owner, ownerStart) = ownerSegment(d.rangeStartMs, segments) ?: return@mapNotNull null
            val ls = toLocalMs(d.rangeStartMs, owner, ownerStart)
            val le = toLocalMs(d.rangeEndMs, owner, ownerStart)
            if (owner.id == d.segmentId && ls == d.localStartMs && le == d.localEndMs) null
            else d.copy(segmentId = owner.id, localStartMs = ls, localEndMs = le)
        }
}
