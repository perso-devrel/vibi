package com.vibi.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectiveAnchorTest {

    private fun seg(id: String, order: Int, trimStart: Long = 0L, trimEnd: Long = 5_000L, speed: Float = 1f) =
        Segment(
            id = id, projectId = "p", type = SegmentType.VIDEO, order = order,
            sourceUri = "u", durationMs = 5_000L, width = 1, height = 1,
            trimStartMs = trimStart, trimEndMs = trimEnd, speedScale = speed,
        )

    private fun dir(segmentId: String, ls: Long, le: Long, gs: Long, ge: Long) = SeparationDirective(
        id = "d", projectId = "p", rangeStartMs = gs, rangeEndMs = ge,
        numberOfSpeakers = 1, muteOriginalSegmentAudio = true, selections = emptyList(),
        createdAt = 0L, segmentId = segmentId, localStartMs = ls, localEndMs = le,
    )

    @Test
    fun `global range derives from anchored segment position`() {
        // 두 세그먼트, 각 5s. directive 는 두 번째 세그먼트 source [1000,3000] 에 앵커.
        val segments = listOf(seg("a", 0), seg("b", 1))
        val d = dir("b", ls = 1_000L, le = 3_000L, gs = 0L, ge = 0L)
        val r = DirectiveAnchor.globalRange(d, segments)
        // b 의 글로벌 시작 = 5000, local 1000→글로벌 6000, 3000→8000.
        assertEquals(6_000L, r.first)
        assertEquals(8_000L, r.last)
    }

    @Test
    fun `reanchor updates cache when segment order changes - move follows`() {
        val d = dir("b", ls = 0L, le = 2_000L, gs = 5_000L, ge = 7_000L)
        // b 를 앞으로 옮김 (order 0). 이제 b 의 글로벌 시작 = 0.
        val moved = listOf(seg("b", 0), seg("a", 1))
        val updates = DirectiveAnchor.reanchor(listOf(d), moved)
        assertEquals(1, updates.size)
        assertEquals(0L, updates[0].rangeStartMs)
        assertEquals(2_000L, updates[0].rangeEndMs)
    }

    @Test
    fun `reanchor is no-op when cache already matches`() {
        val segments = listOf(seg("a", 0), seg("b", 1))
        val d = dir("b", ls = 1_000L, le = 3_000L, gs = 6_000L, ge = 8_000L)
        assertTrue(DirectiveAnchor.reanchor(listOf(d), segments).isEmpty())
    }

    @Test
    fun `resyncAnchors derives anchor from global - global-truth ops`() {
        // delete 등으로 글로벌 range 가 [6000,8000] 로 옮겨진 directive. 앵커는 stale.
        val segments = listOf(seg("a", 0), seg("b", 1))
        val stale = dir("a", ls = 99L, le = 99L, gs = 6_000L, ge = 8_000L)
        val updates = DirectiveAnchor.resyncAnchors(listOf(stale), segments)
        assertEquals(1, updates.size)
        // 글로벌 6000 은 b 영역 → b 로 재앵커, local = 1000..3000.
        assertEquals("b", updates[0].segmentId)
        assertEquals(1_000L, updates[0].localStartMs)
        assertEquals(3_000L, updates[0].localEndMs)
    }

    @Test
    fun `resync then reanchor is idempotent - no clobber`() {
        val segments = listOf(seg("a", 0), seg("b", 1))
        val stale = dir("a", ls = 99L, le = 99L, gs = 6_000L, ge = 8_000L)
        val resynced = DirectiveAnchor.resyncAnchors(listOf(stale), segments).first()
        // 재동기화된 앵커로 글로벌을 다시 파생 → 변화 없어야 (idempotent).
        assertTrue(DirectiveAnchor.reanchor(listOf(resynced), segments).isEmpty())
    }
}
