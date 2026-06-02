package com.vibi.shared.domain.usecase.timeline

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.SegmentType
import com.vibi.shared.domain.model.SeparationDirective
import com.vibi.shared.domain.repository.SegmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 사용자 보고 버그 repro: 음원분리(directive) 포함 구간을 복제하면 분리가 복제본을 따라가야 한다.
 * 앵커링 도입 후 — 복제본 세그먼트에 directive 가 새 segmentId 로 복제되는지 검증.
 */
class DuplicateSegmentRangeUseCaseTest {

    private fun seg() = Segment(
        id = "seg", projectId = "p1", type = SegmentType.VIDEO, order = 0,
        sourceUri = "content://v", durationMs = 10_000L, width = 1920, height = 1080,
        trimStartMs = 0L, trimEndMs = 10_000L,
    )

    @Test
    fun `duplicating front part with a separation makes the separation follow the copy`() = runTest {
        val segs = FakeSegmentRepository().apply { store["seg"] = seg() }
        val dirs = FakeSeparationDirectiveRepository()
        // 앞부분 [0,4000] 에 분리 적용 (해당 세그먼트에 앵커).
        dirs.store["d"] = SeparationDirective(
            id = "d", projectId = "p1", rangeStartMs = 0L, rangeEndMs = 4_000L,
            numberOfSpeakers = 1, muteOriginalSegmentAudio = true, selections = emptyList(),
            createdAt = 0L, segmentId = "seg", localStartMs = 0L, localEndMs = 4_000L,
        )
        val useCase = DuplicateSegmentRangeUseCase(segs, SplitSegmentUseCase(segs, dirs), dirs)

        // 앞부분 [0,4000] 복제 → 기대 세그먼트 순서 [앞(seg) / 앞'(dup) / 뒤(post)].
        val dup = useCase("seg", 0L, 4_000L)

        val ordered = segs.getByProjectId("p1")
        assertEquals(3, ordered.size)
        assertEquals(listOf(0, 1, 2), ordered.map { it.order })
        // 복제본은 order 1, duplicatedFromId 가 원본(seg).
        assertEquals(dup.id, ordered[1].id)
        assertEquals("seg", ordered[1].duplicatedFromId)

        // directive 가 둘: 원본(seg) + 복제본(dup) 각각에 앵커 → 분리가 복제본을 따라감.
        val all = dirs.getByProject("p1")
        assertEquals(2, all.size)
        assertTrue(all.any { it.segmentId == "seg" }, "원본 분리 유지")
        val cloned = all.firstOrNull { it.segmentId == dup.id }
        assertNotNull(cloned, "복제본에 분리가 따라와야 함")
        // 복제본 directive 는 local 좌표 동일, jobId 는 dedup 충돌 방지 위해 null.
        assertEquals(0L, cloned.localStartMs)
        assertEquals(4_000L, cloned.localEndMs)
        assertEquals(null, cloned.jobId)
    }
}
