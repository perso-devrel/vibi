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
import kotlin.test.assertNull

class MergeSegmentsUseCaseTest {

    // 같은 source 를 [0,3], [3,7], [7,10] 으로 split 한 a,b,c (연속 trim).
    private fun split3(): FakeSegmentRepository {
        val repo = FakeSegmentRepository()
        fun seg(id: String, order: Int, ts: Long, te: Long) = Segment(
            id = id, projectId = "p", type = SegmentType.VIDEO, order = order,
            sourceUri = "v", durationMs = 10_000L, width = 1, height = 1,
            trimStartMs = ts, trimEndMs = te,
        )
        repo.store["a"] = seg("a", 0, 0L, 3_000L)
        repo.store["b"] = seg("b", 1, 3_000L, 7_000L)
        repo.store["c"] = seg("c", 2, 7_000L, 10_000L)
        return repo
    }

    @Test
    fun `merge contiguous split pieces into one`() = runTest {
        val repo = split3()
        val merged = MergeSegmentsUseCase(repo, FakeSeparationDirectiveRepository())(listOf("a", "b"))
        assertNotNull(merged)
        // a+b → 하나로 (a 의 id 유지, trim [0,7000]). c 는 남고 order 압축.
        assertEquals("a", merged.id)
        assertEquals(0L, merged.trimStartMs)
        assertEquals(7_000L, merged.trimEndMs)
        val all = repo.getByProjectId("p")
        assertEquals(listOf("a", "c"), all.map { it.id })
        assertEquals(listOf(0, 1), all.map { it.order })
    }

    @Test
    fun `reject non-contiguous trim`() = runTest {
        val repo = split3()
        // a [0,3] 과 c [7,10] 은 trim 이 안 이어짐 (b 건너뜀) + order 도 비인접.
        assertNull(MergeSegmentsUseCase(repo, FakeSeparationDirectiveRepository())(listOf("a", "c")))
    }

    @Test
    fun `reject different source`() = runTest {
        val repo = split3()
        repo.store["b"] = repo.store["b"]!!.copy(sourceUri = "other")
        assertNull(MergeSegmentsUseCase(repo, FakeSeparationDirectiveRepository())(listOf("a", "b")))
    }

    @Test
    fun `directive on removed piece re-anchors to head`() = runTest {
        val repo = split3()
        val dirs = FakeSeparationDirectiveRepository()
        // b(제거 대상)에 앵커된 directive — 병합 후 head(a)로 재앵커돼야.
        dirs.store["d"] = SeparationDirective(
            id = "d", projectId = "p", rangeStartMs = 3_000L, rangeEndMs = 5_000L,
            numberOfSpeakers = 1, muteOriginalSegmentAudio = true, selections = emptyList(),
            createdAt = 0L, segmentId = "b", localStartMs = 3_000L, localEndMs = 5_000L,
        )
        MergeSegmentsUseCase(repo, dirs)(listOf("a", "b"))
        assertEquals("a", dirs.store["d"]!!.segmentId)
        assertEquals(3_000L, dirs.store["d"]!!.localStartMs)  // local 좌표는 그대로
    }
}
