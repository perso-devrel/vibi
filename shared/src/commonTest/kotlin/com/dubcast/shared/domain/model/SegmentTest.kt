package com.dubcast.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentTest {
    @Test
    fun `video with trimEndMs zero falls back to durationMs`() {
        val segment = Segment(
            id = "s1",
            projectId = "p1",
            type = SegmentType.VIDEO,
            order = 0,
            sourceUri = "content://video",
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            trimStartMs = 1_000L,
            trimEndMs = 0L
        )
        assertEquals(10_000L, segment.effectiveTrimEndMs)
        assertEquals(9_000L, segment.effectiveDurationMs)
    }

    @Test
    fun `image segment uses durationMs regardless of trim`() {
        val segment = Segment(
            id = "s1",
            projectId = "p1",
            type = SegmentType.IMAGE,
            order = 0,
            sourceUri = "content://image",
            durationMs = 3_000L,
            width = 1920,
            height = 1080
        )
        assertEquals(3_000L, segment.effectiveDurationMs)
    }
}
