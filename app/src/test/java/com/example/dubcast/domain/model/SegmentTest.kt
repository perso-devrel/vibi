package com.example.dubcast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTest {

    private fun video(
        durationMs: Long = 10_000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L
    ) = Segment(
        id = "s",
        projectId = "p",
        type = SegmentType.VIDEO,
        order = 0,
        sourceUri = "file://video",
        durationMs = durationMs,
        width = 1920,
        height = 1080,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs
    )

    @Test
    fun `video with no trim returns full duration`() {
        val s = video(durationMs = 10_000L)
        assertEquals(10_000L, s.effectiveTrimEndMs)
        assertEquals(10_000L, s.effectiveDurationMs)
    }

    @Test
    fun `video with trim start returns shortened duration`() {
        val s = video(durationMs = 10_000L, trimStartMs = 2_000L)
        assertEquals(10_000L, s.effectiveTrimEndMs)
        assertEquals(8_000L, s.effectiveDurationMs)
    }

    @Test
    fun `video with both trim endpoints respects them`() {
        val s = video(durationMs = 10_000L, trimStartMs = 2_000L, trimEndMs = 7_000L)
        assertEquals(7_000L, s.effectiveTrimEndMs)
        assertEquals(5_000L, s.effectiveDurationMs)
    }

    @Test
    fun `image segment ignores trim and uses duration directly`() {
        val s = Segment(
            id = "s",
            projectId = "p",
            type = SegmentType.IMAGE,
            order = 0,
            sourceUri = "file://img",
            durationMs = 3_000L,
            width = 1920,
            height = 1080,
            trimStartMs = 1_000L,
            trimEndMs = 2_000L
        )
        assertEquals(3_000L, s.effectiveDurationMs)
    }
}
