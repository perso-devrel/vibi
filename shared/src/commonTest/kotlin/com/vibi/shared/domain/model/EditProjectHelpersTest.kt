package com.vibi.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditProjectHelpersTest {

    private fun project(
        separationJobId: String? = null,
        separationStatus: AutoJobStatus = AutoJobStatus.IDLE,
        separationError: String? = null,
        processingSeparations: List<PersistedSeparationJob> = emptyList(),
    ) = EditProject(
        projectId = "p", createdAt = 0L, updatedAt = 0L,
        separationJobId = separationJobId,
        separationStatus = separationStatus,
        separationError = separationError,
        processingSeparations = processingSeparations,
    )

    private fun job(jobId: String, segId: String = "s") = PersistedSeparationJob(
        jobId = jobId, segmentId = segId,
    )

    // ── clearSeparation ──

    @Test fun `clearSeparation 은 separationJobId_segmentId_status_error 만 비움`() {
        val p = project(
            separationJobId = "j1",
            separationStatus = AutoJobStatus.READY,
            separationError = "boom",
        )
        val cleared = p.clearSeparation()
        assertNull(cleared.separationJobId)
        assertNull(cleared.separationSegmentId)
        assertEquals(AutoJobStatus.IDLE, cleared.separationStatus)
        assertNull(cleared.separationError)
    }

    @Test fun `clearSeparation 은 processingSeparations 보존 — 다른 잡 영향 X`() {
        val keep = listOf(job("j1"), job("j2"))
        val p = project(separationStatus = AutoJobStatus.FAILED, processingSeparations = keep)
        val cleared = p.clearSeparation()
        assertEquals(keep, cleared.processingSeparations)
    }

    // ── addProcessingSeparation ──

    @Test fun `addProcessingSeparation 은 신규 jobId 추가`() {
        val p = project()
        val updated = p.addProcessingSeparation(job("j1"))
        assertEquals(1, updated.processingSeparations.size)
        assertEquals("j1", updated.processingSeparations[0].jobId)
    }

    @Test fun `addProcessingSeparation 중복 jobId 는 무시 — same instance 반환`() {
        val p = project(processingSeparations = listOf(job("j1")))
        val sameJob = job("j1", segId = "다른값")
        val updated = p.addProcessingSeparation(sameJob)
        // 중복 jobId 면 그대로 반환 — segId 가 달라도 dedup.
        assertSame(p, updated)
        assertEquals(1, updated.processingSeparations.size)
    }

    @Test fun `addProcessingSeparation 다른 jobId 는 추가 — 동시 진행 가능`() {
        val p = project(processingSeparations = listOf(job("j1")))
        val updated = p.addProcessingSeparation(job("j2"))
        assertEquals(2, updated.processingSeparations.size)
        assertEquals(listOf("j1", "j2"), updated.processingSeparations.map { it.jobId })
    }

    // ── removeProcessingSeparation ──

    @Test fun `removeProcessingSeparation 지정 jobId entry 만 제거`() {
        val p = project(processingSeparations = listOf(job("j1"), job("j2"), job("j3")))
        val updated = p.removeProcessingSeparation("j2")
        assertEquals(listOf("j1", "j3"), updated.processingSeparations.map { it.jobId })
    }

    @Test fun `removeProcessingSeparation 일치 없으면 no-op`() {
        val initial = listOf(job("j1"), job("j2"))
        val p = project(processingSeparations = initial)
        val updated = p.removeProcessingSeparation("nonexistent")
        assertEquals(initial, updated.processingSeparations)
    }

    @Test fun `removeProcessingSeparation 빈 리스트면 그대로 빈 리스트`() {
        val p = project()
        val updated = p.removeProcessingSeparation("j1")
        assertTrue(updated.processingSeparations.isEmpty())
    }

    // ── default 값 sanity (regression guard) ──

    @Test fun `EditProject default 는 무편집 상태 — isProjectEdited 호환`() {
        val p = project()
        assertEquals(EditProject.DEFAULT_BACKGROUND_COLOR_HEX, p.backgroundColorHex)
        assertEquals(EditProject.DEFAULT_VIDEO_SCALE, p.videoScale)
        assertEquals(0f, p.videoOffsetXPct)
        assertEquals(0f, p.videoOffsetYPct)
        assertEquals(0, p.frameWidth)
        assertEquals(0, p.frameHeight)
        assertFalse(p.processingSeparations.isNotEmpty())
    }
}
